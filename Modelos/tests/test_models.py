import json
import tempfile
import unittest
from pathlib import Path

import numpy as np
import pandas as pd

from proaula_models.delay_risk import (
    ARTIFACT_PATH,
    get_delay_risk_model,
    load_delay_dataset,
    select_f1_threshold,
    train_delay_risk_model,
)
from proaula_models.recommendations import recommend_route, suggest_destinations
from proaula_models.synthetic_data import (
    build_delay_dataset,
    build_destination_history_dataset,
    build_route_scenario_dataset,
)


class SyntheticDataTests(unittest.TestCase):
    def test_dataset_is_reproducible_and_has_binary_target(self):
        first = build_delay_dataset(sample_count=500)
        second = build_delay_dataset(sample_count=500)

        self.assertTrue(first.equals(second))
        self.assertEqual(set(first["retraso_mayor_10_min"].unique()), {0, 1})
        self.assertTrue((first["retraso_minutos"] >= 0).all())

    def test_synthetic_route_and_destination_datasets_are_reproducible(self):
        routes = build_route_scenario_dataset(sample_count=100)
        destinations = build_destination_history_dataset(
            user_count=100,
            place_count=5,
        )

        self.assertEqual(len(routes), 300)
        self.assertEqual(routes["scenario_id"].nunique(), 100)
        self.assertEqual(len(destinations), 500)
        self.assertTrue((destinations["visitas_futuras_7d_sinteticas"] >= 0).all())

    def test_delay_data_covers_balanced_and_stress_scenarios(self):
        scenarios = {
            "operacion_regular",
            "hora_pico",
            "demanda_alta",
            "demanda_baja",
            "duracion_extensa",
            "fin_semana_pico",
        }
        balanced = build_delay_dataset(sample_count=6_000, scenario_mix="balanced")
        stress = build_delay_dataset(
            sample_count=6_000,
            random_state=123,
            scenario_mix="stress",
        )

        self.assertEqual(set(balanced["escenario_sintetico"]), scenarios)
        self.assertEqual(set(stress["escenario_sintetico"]), scenarios)
        self.assertGreater(
            stress["escenario_sintetico"].value_counts()["fin_semana_pico"],
            balanced["escenario_sintetico"].value_counts()["fin_semana_pico"],
        )
        for data in (balanced, stress):
            high_demand = data[data["escenario_sintetico"] == "demanda_alta"]
            low_demand = data[data["escenario_sintetico"] == "demanda_baja"]
            long_duration = data[data["escenario_sintetico"] == "duracion_extensa"]
            weekend_peak = data[data["escenario_sintetico"] == "fin_semana_pico"]
            rush_hour = data[data["escenario_sintetico"] == "hora_pico"]
            self.assertTrue(
                high_demand["indice_demanda_pronosticada"].between(65, 100).all()
            )
            self.assertTrue(
                low_demand["indice_demanda_pronosticada"].between(0, 25).all()
            )
            self.assertTrue(
                long_duration["duracion_planificada_min"].between(90, 240).all()
            )
            self.assertTrue(weekend_peak["fecha_viaje"].dt.dayofweek.isin([5, 6]).all())
            self.assertTrue(
                rush_hour["hora_salida"].isin([6, 7, 8, 9, 16, 17, 18, 19]).all()
            )
            self.assertGreater(
                high_demand["retraso_mayor_10_min"].mean(),
                low_demand["retraso_mayor_10_min"].mean(),
            )
            self.assertGreater(
                long_duration["retraso_mayor_10_min"].mean(),
                data[data["escenario_sintetico"] == "operacion_regular"][
                    "retraso_mayor_10_min"
                ].mean(),
            )


class DelayRiskTests(unittest.TestCase):
    def test_f1_threshold_search_covers_scores_above_previous_fixed_range(self):
        threshold = select_f1_threshold(
            pd.Series([0, 0, 1, 1]),
            np.array([0.1, 0.2, 0.8, 0.9]),
        )

        self.assertAlmostEqual(threshold, 0.8)

    def test_real_route_ids_are_learned_and_unseen_routes_remain_predictable(self):
        data = build_delay_dataset(sample_count=2_000)
        data["ruta"] = data["ruta"].map(
            {
                "R-01": "101",
                "R-02": "102",
                "R-03": "103",
                "R-04": "104",
                "R-05": "105",
                "R-06": "106",
            }
        )
        model = train_delay_risk_model(data, data_origin="HISTORICO_REAL")

        prediction = model.predict(
            {
                "ruta": "999",
                "dia_semana": "Lunes",
                "hora_salida": 17,
                "duracion_planificada_min": 48,
                "indice_demanda_pronosticada": 72,
            }
        )

        self.assertEqual(prediction["origenDatos"], "HISTORICO_REAL")
        self.assertGreaterEqual(prediction["probabilidadRetraso"], 0)
        self.assertLessEqual(prediction["probabilidadRetraso"], 1)

    def test_real_training_csv_validates_labels_and_derives_weekday(self):
        data = build_delay_dataset(sample_count=2_000)
        data["ruta"] = "101"
        data["dia_semana"] = "incorrecto"
        data.loc[0, "retraso_minutos"] = -4
        data.loc[0, "retraso_mayor_10_min"] = 0
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "viajes.csv"
            data.to_csv(path, index=False)
            loaded = load_delay_dataset(path)

        self.assertEqual(loaded["ruta"].unique().tolist(), ["101"])
        self.assertEqual(loaded["dia_semana"].iloc[0], "Miércoles")
        self.assertTrue(loaded["fecha_viaje"].is_monotonic_increasing)
        self.assertEqual(
            loaded["retraso_mayor_10_min"].tolist(),
            (loaded["retraso_minutos"] > 10).astype(int).tolist(),
        )

    def test_real_training_csv_rejects_conflicting_delay_labels(self):
        data = build_delay_dataset(sample_count=2_000)
        data.loc[0, "retraso_mayor_10_min"] = 1 - data.loc[
            0, "retraso_mayor_10_min"
        ]
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "viajes.csv"
            data.to_csv(path, index=False)
            with self.assertRaisesRegex(ValueError, "no coincide"):
                load_delay_dataset(path)

    def test_pretrained_artifact_is_delivered(self):
        project_root = Path(__file__).resolve().parents[1]
        evaluation_path = project_root / "artifacts" / "evaluation.json"
        self.assertTrue(ARTIFACT_PATH.is_file())
        self.assertGreater(ARTIFACT_PATH.stat().st_size, 0)
        self.assertTrue(evaluation_path.is_file())
        report = json.loads(evaluation_path.read_text(encoding="utf-8"))
        self.assertEqual(report["selectedModel"], "HistGradientBoostingClassifier")
        self.assertTrue(all(report["qualityGates"].values()))
        stress_evaluation = report["stressScenarioEvaluation"]
        self.assertEqual(
            stress_evaluation["datasetOrigin"],
            "SYNTHETIC_STRESS_NOT_USED_FOR_TRAINING",
        )
        self.assertEqual(stress_evaluation["scenarioCount"], 6)
        for scenario_metrics in stress_evaluation["metricsByScenario"].values():
            self.assertEqual(
                sum(
                    scenario_metrics[key]
                    for key in (
                        "trueNegatives",
                        "falsePositives",
                        "falseNegatives",
                        "truePositives",
                    )
                ),
                scenario_metrics["samples"],
            )
            self.assertTrue(
                scenario_metrics["rocAuc"] is None
                or np.isfinite(scenario_metrics["rocAuc"])
            )
        model = get_delay_risk_model()
        for model_metrics in report["classifierComparison"].values():
            self.assertIn("f1", model_metrics)
            self.assertGreaterEqual(model_metrics["f1"], 0)
            self.assertLessEqual(model_metrics["f1"], 1)
        evaluated = report["classifierComparison"]["HistGradientBoosting"]
        for report_key, model_key in (
            ("threshold", "umbral"),
            ("accuracy", "accuracy"),
            ("balancedAccuracy", "balancedAccuracy"),
            ("precision", "precision"),
            ("recall", "recall"),
            ("f1", "f1"),
            ("f2", "f2"),
            ("rocAuc", "rocAuc"),
            ("averagePrecision", "averagePrecision"),
            ("brierScore", "brierScore"),
            ("verdaderosNegativos", "verdaderosNegativos"),
            ("falsosPositivos", "falsosPositivos"),
            ("falsosNegativos", "falsosNegativos"),
            ("verdaderosPositivos", "verdaderosPositivos"),
        ):
            self.assertAlmostEqual(evaluated[report_key], model.metrics[model_key])

    def test_model_returns_binary_class_and_probability(self):
        prediction = get_delay_risk_model().predict(
            {
                "ruta": "101",
                "dia_semana": "Lunes",
                "hora_salida": 17,
                "duracion_planificada_min": 48,
                "indice_demanda_pronosticada": 72.0,
            }
        )

        self.assertIn(prediction["riesgo"], {"ALTO", "BAJO"})
        self.assertGreaterEqual(prediction["probabilidadRetraso"], 0)
        self.assertLessEqual(prediction["probabilidadRetraso"], 1)
        self.assertIn("HistGradientBoostingClassifier", prediction["modelo"])
        self.assertEqual(prediction["origenDatos"], "SINTETICOS_DEMOSTRACION")

    def test_prediction_rejects_out_of_range_values(self):
        features = {
            "ruta": "101",
            "dia_semana": "Lunes",
            "hora_salida": 17,
            "duracion_planificada_min": 48,
            "indice_demanda_pronosticada": 72.0,
        }
        features["hora_salida"] = 24

        with self.assertRaisesRegex(ValueError, "hora_salida"):
            get_delay_risk_model().predict(features)

    def test_model_exposes_holdout_metrics(self):
        metrics = get_delay_risk_model().metrics
        self.assertGreaterEqual(metrics["rocAuc"], 0.80)
        self.assertGreaterEqual(metrics["precisionLift"], 2.5)
        self.assertGreaterEqual(metrics["accuracy"], 0.80)
        self.assertGreaterEqual(metrics["precision"], 0.35)
        self.assertGreaterEqual(metrics["recall"], 0.50)
        self.assertGreaterEqual(metrics["f1"], 0.40)
        self.assertGreater(metrics["brierSkillScore"], 0)
        self.assertGreaterEqual(metrics["pruebaMuestras"], 5_000)
        self.assertEqual(
            metrics["ajusteFinalMuestras"],
            metrics["entrenamientoMuestras"] + metrics["validacionMuestras"],
        )
        self.assertLess(metrics["ajusteFinalMuestras"], 40_000)
        self.assertEqual(
            sum(
                metrics[key]
                for key in (
                    "verdaderosNegativos",
                    "falsosPositivos",
                    "falsosNegativos",
                    "verdaderosPositivos",
                )
            ),
            metrics["pruebaMuestras"],
        )


class RouteRecommendationTests(unittest.TestCase):
    def test_recommends_alternative_only_when_saving_is_significant(self):
        response = recommend_route(
            {
                "codigo": "HABITUAL",
                "eta_min": 58,
                "salidas_por_hora": 4,
                "demora_clima_min": 3,
                "congestion_pct": 88,
            },
            [
                {
                    "codigo": "RAPIDA",
                    "eta_min": 34,
                    "salidas_por_hora": 6,
                    "demora_clima_min": 1,
                    "congestion_pct": 25,
                }
            ],
        )
        self.assertEqual(response["rutaRecomendada"], "RAPIDA")
        self.assertEqual(response["origenDatos"], "ESTIMACIONES_DEMOSTRACION")

    def test_preserves_habitual_route_when_improvement_is_below_threshold(self):
        response = recommend_route(
            {"codigo": "HABITUAL", "eta_min": 40, "salidas_por_hora": 4},
            [{"codigo": "ALTERNATIVA", "eta_min": 38, "salidas_por_hora": 4}],
        )
        self.assertEqual(response["rutaRecomendada"], "HABITUAL")


class DestinationRecommendationTests(unittest.TestCase):
    def test_ranks_matching_places_by_visit_history(self):
        suggestions = suggest_destinations(
            "man",
            [
                {"id": "P-01", "nombre": "Parada Manga", "tipo": "parada"},
                {"id": "D-01", "nombre": "Mercado de Manga", "tipo": "destino"},
            ],
            {
                "P-01": {"visitas_7d": 5, "visitas_30d": 18, "dias_desde_ultima_visita": 1},
                "D-01": {"visitas_7d": 1, "visitas_30d": 4, "dias_desde_ultima_visita": 3},
            },
        )
        self.assertEqual(suggestions[0]["id"], "P-01")

    def test_rejects_empty_queries_and_non_positive_limits(self):
        with self.assertRaises(ValueError):
            suggest_destinations("", [], {})
        with self.assertRaises(ValueError):
            suggest_destinations("parada", [], {}, limit=0)


if __name__ == "__main__":
    unittest.main()
