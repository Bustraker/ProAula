"""Genera datos de demostración, evalúa los modelos y guarda el artefacto."""

import json
import os
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    accuracy_score,
    average_precision_score,
    balanced_accuracy_score,
    brier_score_loss,
    confusion_matrix,
    fbeta_score,
    f1_score,
    precision_score,
    recall_score,
    roc_auc_score,
)
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler

from proaula_models.delay_risk import (
    ARTIFACT_PATH,
    CATEGORICAL_FEATURES,
    FEATURES,
    NUMERIC_FEATURES,
    _build_pipeline,
    load_delay_dataset,
    select_f1_threshold,
    temporal_masks,
    train_delay_risk_model,
)
from proaula_models.recommendations import recommend_route, suggest_destinations
from proaula_models.synthetic_data import (
    build_delay_dataset,
    build_destination_history_dataset,
    build_route_scenario_dataset,
)

ROOT = Path(__file__).resolve().parent
DATA_DIRECTORY = ROOT / "data"
RANDOM_STATE = 20_261_102


def build_logistic_baseline() -> Pipeline:
    preprocessing = ColumnTransformer(
        [
            ("categorical", OneHotEncoder(handle_unknown="ignore"), CATEGORICAL_FEATURES),
            ("numeric", StandardScaler(), NUMERIC_FEATURES),
        ]
    )
    return Pipeline(
        [
            ("preprocessing", preprocessing),
            ("classifier", LogisticRegression(max_iter=1_000, random_state=RANDOM_STATE)),
        ]
    )


def evaluate_classifier(
    estimator: Pipeline,
    data: pd.DataFrame,
    masks: tuple[pd.Series, pd.Series, pd.Series],
) -> dict[str, float]:
    train_mask, validation_mask, test_mask = masks
    features = data[FEATURES]
    target = data["retraso_mayor_10_min"]

    estimator.fit(features.loc[train_mask], target.loc[train_mask])
    validation_probabilities = estimator.predict_proba(features.loc[validation_mask])[:, 1]
    threshold = select_f1_threshold(
        target.loc[validation_mask],
        validation_probabilities,
    )

    training_mask = train_mask | validation_mask
    estimator.fit(features.loc[training_mask], target.loc[training_mask])
    probabilities = estimator.predict_proba(features.loc[test_mask])[:, 1]
    predictions = probabilities >= threshold
    test_target = target.loc[test_mask]
    baseline_precision = float(test_target.mean())
    baseline_brier = baseline_precision * (1 - baseline_precision)
    brier_score = float(brier_score_loss(test_target, probabilities))
    return {
        "threshold": float(threshold),
        "accuracy": float(accuracy_score(test_target, predictions)),
        "balancedAccuracy": float(
            balanced_accuracy_score(test_target, predictions)
        ),
        "precision": float(precision_score(test_target, predictions, zero_division=0)),
        "recall": float(recall_score(test_target, predictions, zero_division=0)),
        "f1": float(f1_score(test_target, predictions, zero_division=0)),
        "f2": float(fbeta_score(test_target, predictions, beta=2, zero_division=0)),
        "rocAuc": float(roc_auc_score(test_target, probabilities)),
        "averagePrecision": float(average_precision_score(test_target, probabilities)),
        "precisionBaseline": baseline_precision,
        "precisionLift": float(
            average_precision_score(test_target, probabilities) / baseline_precision
        ),
        "brierScore": brier_score,
        "brierSkillScore": float(1 - brier_score / baseline_brier),
        "verdaderosNegativos": int(
            ((test_target == 0) & (predictions == 0)).sum()
        ),
        "falsosPositivos": int(
            ((test_target == 0) & (predictions == 1)).sum()
        ),
        "falsosNegativos": int(
            ((test_target == 1) & (predictions == 0)).sum()
        ),
        "verdaderosPositivos": int(
            ((test_target == 1) & (predictions == 1)).sum()
        ),
    }


def evaluate_scenario_groups(
    estimator: Pipeline,
    data: pd.DataFrame,
    threshold: float,
) -> dict[str, dict[str, int | float | None]]:
    results = {}
    for scenario, group in data.groupby("escenario_sintetico", sort=True):
        target = group["retraso_mayor_10_min"]
        probabilities = estimator.predict_proba(group[FEATURES])[:, 1]
        predictions = probabilities >= threshold
        true_negative, false_positive, false_negative, true_positive = (
            confusion_matrix(target, predictions, labels=[0, 1]).ravel()
        )
        results[scenario] = {
            "samples": int(len(group)),
            "positiveRate": float(target.mean()),
            "accuracy": float(accuracy_score(target, predictions)),
            "precision": float(precision_score(target, predictions, zero_division=0)),
            "recall": float(recall_score(target, predictions, zero_division=0)),
            "f1": float(f1_score(target, predictions, zero_division=0)),
            "f2": float(fbeta_score(target, predictions, beta=2, zero_division=0)),
            "rocAuc": (
                float(roc_auc_score(target, probabilities))
                if target.nunique() == 2
                else None
            ),
            "brierScore": float(brier_score_loss(target, probabilities)),
            "trueNegatives": int(true_negative),
            "falsePositives": int(false_positive),
            "falseNegatives": int(false_negative),
            "truePositives": int(true_positive),
        }
    return results


def evaluate_route_recommendations(scenarios: pd.DataFrame) -> dict[str, float]:
    agreements = []
    regrets = []
    bad_switches = []

    for _, options in scenarios.groupby("scenario_id", sort=True):
        routes = options.to_dict(orient="records")
        habitual = next(route for route in routes if route["es_habitual"])
        alternatives = [route for route in routes if not route["es_habitual"]]
        result = recommend_route(
            {
                "codigo": habitual["codigo"],
                "eta_min": habitual["eta_min"],
                "salidas_por_hora": habitual["salidas_por_hora"],
                "demora_clima_min": habitual["demora_clima_min"],
            },
            [
                {
                    "codigo": route["codigo"],
                    "eta_min": route["eta_min"],
                    "salidas_por_hora": route["salidas_por_hora"],
                    "demora_clima_min": route["demora_clima_min"],
                }
                for route in alternatives
            ],
        )
        oracle = min(
            alternatives,
            key=lambda route: route["tiempo_generalizado_real_min"],
        )
        oracle_route = (
            oracle
            if oracle["tiempo_generalizado_real_min"]
            + 5
            < habitual["tiempo_generalizado_real_min"]
            else habitual
        )
        selected = next(
            route for route in routes if route["codigo"] == result["rutaRecomendada"]
        )
        selected_cost = selected["tiempo_generalizado_real_min"]
        oracle_cost = oracle_route["tiempo_generalizado_real_min"]
        agreements.append(selected["codigo"] == oracle_route["codigo"])
        regrets.append(max(0.0, selected_cost - oracle_cost))
        if selected["codigo"] != habitual["codigo"]:
            bad_switches.append(
                selected_cost >= habitual["tiempo_generalizado_real_min"]
            )

    return {
        "scenarios": float(len(agreements)),
        "oracleAgreement": float(np.mean(agreements)),
        "meanRegretMin": float(np.mean(regrets)),
        "badSwitchRate": float(np.mean(bad_switches)) if bad_switches else 0.0,
    }


def _ndcg_at_k(relevance: list[int], limit: int) -> float:
    gains = [2**min(value, 3) - 1 for value in relevance[:limit]]
    discounts = np.log2(np.arange(2, len(gains) + 2))
    dcg = float(np.sum(np.asarray(gains) / discounts))
    ideal = sorted(relevance, reverse=True)[:limit]
    ideal_gains = [2**min(value, 3) - 1 for value in ideal]
    ideal_dcg = float(np.sum(np.asarray(ideal_gains) / discounts[: len(ideal_gains)]))
    return dcg / ideal_dcg if ideal_dcg else 0.0


def evaluate_destination_recommendations(
    history_data: pd.DataFrame,
    limit: int = 5,
) -> dict[str, float]:
    recalls = []
    precisions = []
    ndcgs = []
    popular_baseline_recalls = []
    popular_baseline_precisions = []
    popular_baseline_ndcgs = []
    candidate_positive_rates = []

    for _, user_history in history_data.groupby("usuario_sintetico", sort=True):
        catalog = user_history[
            ["id", "nombre", "tipo", "barrio"]
        ].to_dict(orient="records")
        visit_history = {
            row["id"]: {
                "visitas_7d": int(row["visitas_7d"]),
                "visitas_30d": int(row["visitas_30d"]),
                "dias_desde_ultima_visita": int(row["dias_desde_ultima_visita"]),
            }
            for row in user_history.to_dict(orient="records")
        }
        relevance = {
            row["id"]: int(row["visitas_futuras_7d_sinteticas"])
            for row in user_history.to_dict(orient="records")
        }
        relevant = sum(value > 0 for value in relevance.values())
        candidate_positive_rates.extend(value > 0 for value in relevance.values())
        if not relevant:
            continue

        suggestions = suggest_destinations("sector norte", catalog, visit_history, limit)
        recommended_ids = [place["id"] for place in suggestions]
        recommended_relevance = [relevance[place_id] for place_id in recommended_ids]
        hits = sum(value > 0 for value in recommended_relevance)
        recalls.append(hits / relevant)
        precisions.append(hits / limit)
        ndcgs.append(_ndcg_at_k(recommended_relevance, limit))

        popular = user_history.sort_values(
            ["visitas_30d", "visitas_7d"], ascending=False
        ).head(limit)
        popular_hits = sum(
            int(value > 0)
            for value in popular["visitas_futuras_7d_sinteticas"].tolist()
        )
        popular_baseline_recalls.append(popular_hits / relevant)
        popular_baseline_precisions.append(popular_hits / limit)
        popular_baseline_ndcgs.append(
            _ndcg_at_k(
                popular["visitas_futuras_7d_sinteticas"].astype(int).tolist(),
                limit,
            )
        )

    return {
        "usersEvaluated": float(len(recalls)),
        "candidateFutureVisitRate": float(np.mean(candidate_positive_rates)),
        "recallAt5": float(np.mean(recalls)),
        "precisionAt5": float(np.mean(precisions)),
        "ndcgAt5": float(np.mean(ndcgs)),
        "recentPopularityBaselineRecallAt5": float(np.mean(popular_baseline_recalls)),
        "recentPopularityBaselinePrecisionAt5": float(
            np.mean(popular_baseline_precisions)
        ),
        "recentPopularityBaselineNdcgAt5": float(np.mean(popular_baseline_ndcgs)),
    }


def main() -> None:
    DATA_DIRECTORY.mkdir(parents=True, exist_ok=True)
    configured_dataset = os.environ.get("PROAULA_DELAY_DATASET")
    dataset_path = Path(configured_dataset).expanduser() if configured_dataset else None
    dataset = (
        load_delay_dataset(dataset_path)
        if dataset_path is not None
        else build_delay_dataset(random_state=RANDOM_STATE)
    )
    data_origin = (
        "HISTORICO_REAL" if dataset_path is not None else "SINTETICOS_DEMOSTRACION"
    )
    stress_dataset = (
        build_delay_dataset(
            sample_count=12_000,
            random_state=RANDOM_STATE + 1,
            scenario_mix="stress",
        )
        if dataset_path is None
        else None
    )
    route_scenarios = build_route_scenario_dataset()
    destination_history = build_destination_history_dataset(
        user_count=2_000,
        random_state=20_261_105,
    )
    train_mask, validation_mask, test_mask = temporal_masks(dataset)
    if dataset_path is None:
        delay_export = dataset.copy()
        delay_export["particion_temporal"] = np.select(
            [train_mask, validation_mask, test_mask],
            ["entrenamiento", "validacion", "prueba"],
            default="sin_particion",
        )
        delay_export["origen_datos"] = data_origin
        delay_export.to_csv(DATA_DIRECTORY / "synthetic_delay_trips.csv", index=False)
        stress_dataset.to_csv(
            DATA_DIRECTORY / "synthetic_delay_stress.csv",
            index=False,
        )
    route_scenarios.to_csv(DATA_DIRECTORY / "synthetic_route_scenarios.csv", index=False)
    destination_history.to_csv(
        DATA_DIRECTORY / "synthetic_destination_history.csv",
        index=False,
    )

    masks = (train_mask, validation_mask, test_mask)
    hist_gradient_model = _build_pipeline(RANDOM_STATE)
    logistic_model = build_logistic_baseline()
    classifier_comparison = {
        "HistGradientBoosting": evaluate_classifier(
            hist_gradient_model,
            dataset,
            masks,
        ),
        "LogisticRegressionBaseline": evaluate_classifier(
            logistic_model,
            dataset,
            masks,
        ),
    }
    stress_scenario_evaluation = None
    if stress_dataset is not None:
        stress_scenario_evaluation = {
            "datasetOrigin": "SYNTHETIC_STRESS_NOT_USED_FOR_TRAINING",
            "scenarioMix": "stress",
            "samples": int(len(stress_dataset)),
            "metricsByScenario": evaluate_scenario_groups(
                hist_gradient_model,
                stress_dataset,
                classifier_comparison["HistGradientBoosting"]["threshold"],
            ),
        }
    destination_test = destination_history.loc[
        destination_history["usuario_sintetico"] >= 1_500
    ]
    metrics = {
        "classifierComparison": classifier_comparison,
        "selectedModel": "HistGradientBoostingClassifier",
        "selectedModelPurpose": "Equilibrar precision y recall; umbral elegido por F1 en validación temporal.",
        "stressScenarioEvaluation": stress_scenario_evaluation,
        "routeRecommendationEvaluation": evaluate_route_recommendations(route_scenarios),
        "destinationRecommendationEvaluation": evaluate_destination_recommendations(
            destination_test
        ),
    }
    histogram = metrics["classifierComparison"]["HistGradientBoosting"]
    logistic = metrics["classifierComparison"]["LogisticRegressionBaseline"]
    route_metrics = metrics["routeRecommendationEvaluation"]
    destination_metrics = metrics["destinationRecommendationEvaluation"]
    quality_gates = {
        "delayRocAucAtLeast080": histogram["rocAuc"] >= 0.80,
        "delayAveragePrecisionLiftAtLeast25": histogram["precisionLift"] >= 2.5,
        "delayF1AtLeast040": histogram["f1"] >= 0.40,
        "histGradientBeatsLogisticOnF1": histogram["f1"] > logistic["f1"],
        "delayBrierBetterThanPrevalenceBaseline": histogram["brierSkillScore"] > 0,
        "histGradientBeatsLogisticOnRocAuc": (
            histogram["rocAuc"] > logistic["rocAuc"]
        ),
        "routeOracleAgreementAtLeast075": route_metrics["oracleAgreement"] >= 0.75,
        "routeMeanRegretBelowOneMinute": route_metrics["meanRegretMin"] < 1,
        "destinationPrecisionLiftOverCandidateRateAtLeast135": (
            destination_metrics["precisionAt5"]
            >= destination_metrics["candidateFutureVisitRate"] * 1.35
        ),
        "destinationNdcgAtLeast070": destination_metrics["ndcgAt5"] >= 0.70,
        "destinationNdcgNearPopularityBaseline": (
            destination_metrics["ndcgAt5"]
            >= destination_metrics["recentPopularityBaselineNdcgAt5"] - 0.01
        ),
    }
    if stress_scenario_evaluation is not None:
        stress_scenario_evaluation["scenarioCount"] = len(
            stress_scenario_evaluation["metricsByScenario"]
        )
        quality_gates["stressScenarioCoverage"] = (
            set(stress_scenario_evaluation["metricsByScenario"])
            == {
                "operacion_regular",
                "hora_pico",
                "demanda_alta",
                "demanda_baja",
                "duracion_extensa",
                "fin_semana_pico",
            }
        )
    metrics["qualityGates"] = quality_gates
    if not all(quality_gates.values()):
        raise RuntimeError(
            "La evaluación no superó todos los criterios de calidad definidos."
        )

    trained_model = train_delay_risk_model(
        dataset,
        random_state=RANDOM_STATE,
        data_origin=data_origin,
    )
    model_metric_mapping = (
        ("threshold", "umbral"),
        ("accuracy", "accuracy"),
        ("balancedAccuracy", "balancedAccuracy"),
        ("precision", "precision"),
        ("recall", "recall"),
        ("f1", "f1"),
        ("f2", "f2"),
        ("rocAuc", "rocAuc"),
        ("averagePrecision", "averagePrecision"),
        ("precisionBaseline", "precisionBaseline"),
        ("precisionLift", "precisionLift"),
        ("brierScore", "brierScore"),
        ("brierSkillScore", "brierSkillScore"),
        ("verdaderosNegativos", "verdaderosNegativos"),
        ("falsosPositivos", "falsosPositivos"),
        ("falsosNegativos", "falsosNegativos"),
        ("verdaderosPositivos", "verdaderosPositivos"),
    )
    if any(
        not np.isclose(
            trained_model.metrics[model_key],
            metrics["classifierComparison"]["HistGradientBoosting"][report_key],
            atol=1e-12,
        )
        for report_key, model_key in model_metric_mapping
    ):
        raise RuntimeError("La comparación y el artefacto usaron evaluaciones diferentes.")

    ARTIFACT_PATH.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump(trained_model, ARTIFACT_PATH, compress=3)
    report = {
        "version": 2,
        "datasetOrigin": (
            "MIXED_REAL_AND_SYNTHETIC"
            if dataset_path is not None
            else "SYNTHETIC_DEMONSTRATION_ONLY"
        ),
        "randomState": RANDOM_STATE,
        "delayDataset": {
            "file": (
                dataset_path.name
                if dataset_path is not None
                else "data/synthetic_delay_trips.csv"
            ),
            "origin": data_origin,
            "rows": int(len(dataset)),
            "positiveRate": float(dataset["retraso_mayor_10_min"].mean()),
            "firstDate": str(dataset["fecha_viaje"].min().date()),
            "lastDate": str(dataset["fecha_viaje"].max().date()),
            "trainRows": int(masks[0].sum()),
            "validationRows": int(masks[1].sum()),
            "testRows": int(masks[2].sum()),
            "scenarioMix": "balanced" if dataset_path is None else None,
            "scenarioDistribution": (
                {
                    scenario: int(count)
                    for scenario, count in dataset["escenario_sintetico"]
                    .value_counts()
                    .sort_index()
                    .items()
                }
                if "escenario_sintetico" in dataset
                else None
            ),
        },
        "stressDataset": (
            {
                "file": "data/synthetic_delay_stress.csv",
                "origin": "SYNTHETIC_STRESS_NOT_USED_FOR_TRAINING",
                "scenarioMix": "stress",
                "rows": int(len(stress_dataset)),
                "scenarioDistribution": {
                    scenario: int(count)
                    for scenario, count in stress_dataset["escenario_sintetico"]
                    .value_counts()
                    .sort_index()
                    .items()
                },
            }
            if stress_dataset is not None
            else None
        ),
        "routeDataset": {
            "file": "data/synthetic_route_scenarios.csv",
            "origin": "SYNTHETIC_DEMONSTRATION_ONLY",
            "independentScenarios": int(route_scenarios["scenario_id"].nunique()),
        },
        "destinationDataset": {
            "file": "data/synthetic_destination_history.csv",
            "origin": "SYNTHETIC_DEMONSTRATION_ONLY",
            "users": int(destination_history["usuario_sintetico"].nunique()),
            "heldOutTestUsers": int(destination_test["usuario_sintetico"].nunique()),
        },
        **metrics,
    }
    (ROOT / "artifacts" / "evaluation.json").write_text(
        json.dumps(report, indent=2, ensure_ascii=False, allow_nan=False) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(report, indent=2, ensure_ascii=False))
    print(f"\nModelo serializado: {ARTIFACT_PATH}")


if __name__ == "__main__":
    main()
