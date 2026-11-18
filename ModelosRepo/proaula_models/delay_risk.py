"""Clasificador de riesgo de retraso superior a diez minutos."""

from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path
from typing import Any

import joblib
import numpy as np
import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.ensemble import HistGradientBoostingClassifier
from sklearn.metrics import (
    accuracy_score,
    average_precision_score,
    balanced_accuracy_score,
    brier_score_loss,
    confusion_matrix,
    fbeta_score,
    f1_score,
    precision_recall_curve,
    precision_score,
    recall_score,
    roc_auc_score,
)
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder

from .synthetic_data import build_delay_dataset

FEATURES = [
    "ruta",
    "dia_semana",
    "hora_salida",
    "duracion_planificada_min",
    "indice_demanda_pronosticada",
]
CATEGORICAL_FEATURES = ["ruta", "dia_semana"]
NUMERIC_FEATURES = [
    "hora_salida",
    "duracion_planificada_min",
    "indice_demanda_pronosticada",
]
TARGET = "retraso_mayor_10_min"
DATE_COLUMN = "fecha_viaje"
ARTIFACT_PATH = Path(__file__).resolve().parents[1] / "artifacts" / "delay_risk.joblib"
_WEEKDAYS = ("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")


def _build_pipeline(random_state: int = 20_261_102) -> Pipeline:
    preprocessing = ColumnTransformer(
        transformers=[
            (
                "categorical",
                OneHotEncoder(handle_unknown="ignore", sparse_output=False),
                CATEGORICAL_FEATURES,
            ),
            ("numeric", "passthrough", NUMERIC_FEATURES),
        ]
    )
    return Pipeline(
        steps=[
            ("preprocessing", preprocessing),
            (
                "classifier",
                HistGradientBoostingClassifier(
                    max_iter=180,
                    learning_rate=0.07,
                    l2_regularization=2.0,
                    random_state=random_state,
                ),
            ),
        ]
    )


@dataclass(frozen=True)
class DelayRiskModel:
    estimator: Pipeline
    metrics: dict[str, float]
    threshold: float
    data_origin: str = "SINTETICOS_DEMOSTRACION"

    def predict(self, features: dict[str, Any]) -> dict[str, Any]:
        missing = set(FEATURES).difference(features)
        if missing:
            raise ValueError(f"Faltan variables requeridas: {', '.join(sorted(missing))}.")

        if features["ruta"] is None:
            raise ValueError("ruta no puede estar vacía.")
        route = str(features["ruta"]).strip()
        if not route:
            raise ValueError("ruta no puede estar vacía.")
        day = features["dia_semana"]
        if day not in _WEEKDAYS:
            raise ValueError("dia_semana contiene un valor no reconocido.")
        validated_features: dict[str, Any] = {
            "ruta": route,
            "dia_semana": day,
        }
        for column, minimum, maximum, minimum_inclusive in (
            ("hora_salida", 0, 23, True),
            ("duracion_planificada_min", 0, 240, False),
            ("indice_demanda_pronosticada", 0, 100, True),
        ):
            try:
                value = float(features[column])
            except (TypeError, ValueError) as error:
                raise ValueError(f"{column} debe ser numérico.") from error
            below_minimum = value < minimum if minimum_inclusive else value <= minimum
            if not np.isfinite(value) or below_minimum or value > maximum:
                raise ValueError(f"{column} está fuera de su rango permitido.")
            if column == "hora_salida" and not value.is_integer():
                raise ValueError("hora_salida debe ser una hora entera.")
            validated_features[column] = int(value) if column == "hora_salida" else value

        probability = float(
            self.estimator.predict_proba(
                pd.DataFrame([validated_features], columns=FEATURES)
            )[0, 1]
        )
        return {
            "riesgo": "ALTO" if probability >= self.threshold else "BAJO",
            "probabilidadRetraso": round(probability, 4),
            "umbral": round(self.threshold, 4),
            "modelo": "HistGradientBoostingClassifier (clasificación binaria)",
            "origenDatos": self.data_origin,
        }


@lru_cache(maxsize=1)
def get_delay_risk_model() -> DelayRiskModel:
    """Carga el modelo entregado o entrena uno reproducible si falta el artefacto."""
    if ARTIFACT_PATH.exists():
        loaded = joblib.load(ARTIFACT_PATH)
        if not isinstance(loaded, DelayRiskModel):
            raise TypeError(f"El artefacto de modelo tiene un formato no compatible: {ARTIFACT_PATH}.")
        return loaded
    return train_delay_risk_model()


def train_delay_risk_model(
    data: pd.DataFrame | None = None,
    random_state: int = 20_261_102,
    data_origin: str = "SINTETICOS_DEMOSTRACION",
) -> DelayRiskModel:
    """Ajusta el umbral en validación temporal y evalúa en un holdout posterior."""
    data = build_delay_dataset(random_state=random_state) if data is None else data
    data = validate_delay_dataset(data)
    train_mask, validation_mask, test_mask = temporal_masks(data)
    train_and_validation_mask = train_mask | validation_mask
    features = data[FEATURES]
    target = data[TARGET]

    threshold_model = _build_pipeline(random_state)
    threshold_model.fit(features.loc[train_mask], target.loc[train_mask])
    validation_probabilities = threshold_model.predict_proba(
        features.loc[validation_mask]
    )[:, 1]
    threshold = select_f1_threshold(
        target.loc[validation_mask],
        validation_probabilities,
    )

    evaluation_model = _build_pipeline(random_state)
    evaluation_model.fit(
        features.loc[train_and_validation_mask],
        target.loc[train_and_validation_mask],
    )
    test_target = target.loc[test_mask]
    probabilities = evaluation_model.predict_proba(features.loc[test_mask])[:, 1]
    predictions = (probabilities >= threshold).astype(int)
    test_prevalence = float(test_target.mean())
    test_brier_score = float(brier_score_loss(test_target, probabilities))
    true_negative, false_positive, false_negative, true_positive = confusion_matrix(
        test_target,
        predictions,
        labels=[0, 1],
    ).ravel()
    metrics = {
        "accuracy": float(accuracy_score(test_target, predictions)),
        "balancedAccuracy": float(balanced_accuracy_score(test_target, predictions)),
        "precision": float(precision_score(test_target, predictions, zero_division=0)),
        "recall": float(recall_score(test_target, predictions, zero_division=0)),
        "f1": float(f1_score(test_target, predictions, zero_division=0)),
        "f2": float(fbeta_score(test_target, predictions, beta=2, zero_division=0)),
        "rocAuc": float(roc_auc_score(test_target, probabilities)),
        "averagePrecision": float(average_precision_score(test_target, probabilities)),
        "precisionBaseline": test_prevalence,
        "precisionLift": float(average_precision_score(test_target, probabilities) / test_prevalence),
        "brierScore": test_brier_score,
        "brierSkillScore": float(
            1 - test_brier_score / (test_prevalence * (1 - test_prevalence))
        ),
        "verdaderosNegativos": int(true_negative),
        "falsosPositivos": int(false_positive),
        "falsosNegativos": int(false_negative),
        "verdaderosPositivos": int(true_positive),
        "umbral": float(threshold),
        "entrenamientoMuestras": float(train_mask.sum()),
        "validacionMuestras": float(validation_mask.sum()),
        "pruebaMuestras": float(test_mask.sum()),
        "ajusteFinalMuestras": float(train_and_validation_mask.sum()),
    }

    return DelayRiskModel(
        estimator=evaluation_model,
        metrics=metrics,
        threshold=threshold,
        data_origin=data_origin,
    )


def load_delay_dataset(path: Path) -> pd.DataFrame:
    """Carga viajes históricos etiquetados con el contrato de entrenamiento."""
    data = pd.read_csv(path)
    if "retraso_minutos" not in data and TARGET not in data:
        raise ValueError(
            "El CSV debe incluir retraso_minutos o retraso_mayor_10_min como etiqueta real."
        )
    if "dia_semana" not in data:
        data["dia_semana"] = ""
    if "ruta" not in data:
        raise ValueError("El CSV debe incluir la columna ruta.")
    if "hora_salida" not in data:
        raise ValueError("El CSV debe incluir la columna hora_salida.")
    if "duracion_planificada_min" not in data:
        raise ValueError("El CSV debe incluir duracion_planificada_min.")
    if "indice_demanda_pronosticada" not in data:
        raise ValueError("El CSV debe incluir indice_demanda_pronosticada.")
    if DATE_COLUMN not in data:
        raise ValueError(f"El CSV debe incluir la columna {DATE_COLUMN}.")

    data[DATE_COLUMN] = pd.to_datetime(data[DATE_COLUMN], errors="coerce")
    if data[DATE_COLUMN].isna().any():
        raise ValueError("fecha_viaje contiene fechas vacías o inválidas.")
    if isinstance(data[DATE_COLUMN].dtype, pd.DatetimeTZDtype):
        data[DATE_COLUMN] = data[DATE_COLUMN].dt.tz_localize(None)
    data[DATE_COLUMN] = data[DATE_COLUMN].dt.normalize()
    data["dia_semana"] = data[DATE_COLUMN].dt.dayofweek.map(
        dict(enumerate(_WEEKDAYS))
    )

    data["ruta"] = data["ruta"].astype("string").str.strip()
    if data["ruta"].isna().any() or data["ruta"].eq("").any():
        raise ValueError("ruta contiene identificadores vacíos.")

    if "retraso_minutos" in data:
        delay_minutes = pd.to_numeric(data["retraso_minutos"], errors="coerce")
        if delay_minutes.isna().any() or not np.isfinite(delay_minutes).all():
            raise ValueError("retraso_minutos debe contener solo valores numéricos finitos.")
        derived_target = (delay_minutes > 10).astype(int)
        if TARGET in data:
            supplied_target = _validate_binary_target(data[TARGET])
            if not np.array_equal(derived_target.to_numpy(), supplied_target.to_numpy()):
                raise ValueError(
                    "retraso_mayor_10_min no coincide con retraso_minutos > 10."
                )
        data[TARGET] = derived_target
    else:
        data[TARGET] = _validate_binary_target(data[TARGET])

    return validate_delay_dataset(data)


def _validate_binary_target(values: pd.Series) -> pd.Series:
    target = pd.to_numeric(values, errors="coerce")
    if target.isna().any() or not target.isin([0, 1]).all():
        raise ValueError(f"{TARGET} debe contener únicamente 0 y 1.")
    return target.astype(int)


def select_f1_threshold(target: pd.Series, probabilities: np.ndarray) -> float:
    precision, recall, thresholds = precision_recall_curve(target, probabilities)
    if not len(thresholds):
        raise ValueError("No se pudo ajustar el umbral: la validación no contiene clases.")
    denominator = precision[:-1] + recall[:-1]
    f1_scores = np.divide(
        2 * precision[:-1] * recall[:-1],
        denominator,
        out=np.zeros_like(denominator),
        where=denominator > 0,
    )
    best_indices = np.flatnonzero(f1_scores == f1_scores.max())
    return float(thresholds[best_indices[-1]])


def validate_delay_dataset(data: pd.DataFrame) -> pd.DataFrame:
    required = {DATE_COLUMN, *FEATURES, TARGET}
    missing = required.difference(data.columns)
    if missing:
        raise ValueError(f"Faltan columnas de entrenamiento: {', '.join(sorted(missing))}.")
    if len(data) < 500:
        raise ValueError("Se requieren al menos 500 viajes etiquetados.")
    if data[list(required)].isna().any().any():
        raise ValueError("El conjunto de viajes contiene valores vacíos en variables requeridas.")
    if not data[TARGET].isin([0, 1]).all():
        raise ValueError(f"{TARGET} debe contener únicamente 0 y 1.")

    ordered = data.copy()
    ordered[DATE_COLUMN] = pd.to_datetime(ordered[DATE_COLUMN], errors="coerce")
    if ordered[DATE_COLUMN].isna().any():
        raise ValueError("fecha_viaje contiene fechas vacías o inválidas.")
    if isinstance(ordered[DATE_COLUMN].dtype, pd.DatetimeTZDtype):
        ordered[DATE_COLUMN] = ordered[DATE_COLUMN].dt.tz_localize(None)
    ordered[DATE_COLUMN] = ordered[DATE_COLUMN].dt.normalize()
    ordered = ordered.sort_values(DATE_COLUMN, kind="stable").reset_index(drop=True)
    derived_weekdays = ordered[DATE_COLUMN].dt.dayofweek.map(dict(enumerate(_WEEKDAYS)))
    if not ordered["dia_semana"].equals(derived_weekdays):
        raise ValueError("dia_semana no coincide con la fecha_viaje.")
    if "retraso_minutos" in ordered:
        delay_minutes = pd.to_numeric(ordered["retraso_minutos"], errors="coerce")
        if delay_minutes.isna().any() or not np.isfinite(delay_minutes).all():
            raise ValueError("retraso_minutos debe contener solo valores numéricos finitos.")
        if not np.array_equal(
            (delay_minutes > 10).astype(int).to_numpy(),
            ordered[TARGET].astype(int).to_numpy(),
        ):
            raise ValueError(
                "retraso_mayor_10_min no coincide con retraso_minutos > 10."
            )
    ordered["ruta"] = ordered["ruta"].astype("string").str.strip()
    if ordered["ruta"].eq("").any():
        raise ValueError("ruta contiene identificadores vacíos.")
    if not ordered["dia_semana"].isin(_WEEKDAYS).all():
        raise ValueError("dia_semana contiene valores no reconocidos.")
    for column, minimum, maximum, minimum_inclusive in (
        ("hora_salida", 0, 23, True),
        ("duracion_planificada_min", 0, 240, False),
        ("indice_demanda_pronosticada", 0, 100, True),
    ):
        values = pd.to_numeric(ordered[column], errors="coerce")
        if values.isna().any() or not np.isfinite(values).all():
            raise ValueError(f"{column} debe contener solo valores numéricos finitos.")
        below_minimum = values < minimum if minimum_inclusive else values <= minimum
        if (
            below_minimum.any()
            or (values > maximum).any()
            or (column == "hora_salida" and (values % 1 != 0).any())
        ):
            raise ValueError(f"{column} está fuera de su rango permitido.")
        ordered[column] = values

    train_mask, validation_mask, test_mask = temporal_masks(ordered)
    for name, mask in (
        ("entrenamiento", train_mask),
        ("validación", validation_mask),
        ("prueba", test_mask),
    ):
        if ordered.loc[mask, TARGET].nunique() != 2:
            raise ValueError(
                f"La partición temporal de {name} debe contener retrasos y no retrasos."
            )
    return ordered


def temporal_masks(data: pd.DataFrame) -> tuple[pd.Series, pd.Series, pd.Series]:
    unique_dates = np.sort(data[DATE_COLUMN].unique())
    if len(unique_dates) < 10:
        raise ValueError("Se requieren al menos 10 fechas distintas para la validación temporal.")
    train_end = unique_dates[int(len(unique_dates) * 0.6)]
    validation_end = unique_dates[int(len(unique_dates) * 0.8)]
    dates = data[DATE_COLUMN]
    train_mask = dates < train_end
    validation_mask = (dates >= train_end) & (dates < validation_end)
    test_mask = dates >= validation_end
    return train_mask, validation_mask, test_mask
