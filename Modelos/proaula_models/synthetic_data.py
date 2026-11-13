"""Generación reproducible de datos sintéticos para demostración y evaluación."""

import numpy as np
import pandas as pd


def build_delay_dataset(
    sample_count: int = 40_000,
    random_state: int = 20_261_102,
    scenario_mix: str = "balanced",
) -> pd.DataFrame:
    if sample_count < 100:
        raise ValueError("sample_count debe ser al menos 100.")
    if scenario_mix not in {"balanced", "stress"}:
        raise ValueError("scenario_mix debe ser 'balanced' o 'stress'.")

    rng = np.random.default_rng(random_state)
    routes = np.array(["R-01", "R-02", "R-03", "R-04", "R-05", "R-06"])
    route = rng.choice(
        routes,
        size=sample_count,
        p=[0.22, 0.20, 0.18, 0.16, 0.14, 0.10],
    )
    date = (
        pd.Timestamp("2025-01-01")
        + pd.to_timedelta(rng.integers(0, 730, size=sample_count), unit="D")
    ).to_numpy(copy=True)
    scenario_names = np.array(
        [
            "operacion_regular",
            "hora_pico",
            "demanda_alta",
            "demanda_baja",
            "duracion_extensa",
            "fin_semana_pico",
        ]
    )
    scenario_probabilities = (
        [0.40, 0.15, 0.10, 0.10, 0.10, 0.15]
        if scenario_mix == "balanced"
        else [0.10, 0.20, 0.20, 0.10, 0.20, 0.20]
    )
    scenario = rng.choice(
        scenario_names,
        size=sample_count,
        p=scenario_probabilities,
    )
    hours = np.arange(24)
    hour = rng.choice(
        hours,
        size=sample_count,
        p=np.array(
            [1, 1, 1, 1, 2, 3, 5, 8, 10, 8, 5, 3, 3, 3, 4, 6, 9, 11, 9, 6, 4, 3, 2, 1]
        ) / 109,
    )
    peak_hours = np.array([6, 7, 8, 9, 16, 17, 18, 19])
    off_peak_hours = np.array([0, 1, 2, 3, 4, 5, 10, 11, 12, 13, 14, 15, 20, 21, 22, 23])
    hour[np.isin(scenario, ["hora_pico", "fin_semana_pico"])] = rng.choice(
        peak_hours,
        size=np.isin(scenario, ["hora_pico", "fin_semana_pico"]).sum(),
    )
    hour[scenario == "demanda_baja"] = rng.choice(
        off_peak_hours,
        size=(scenario == "demanda_baja").sum(),
    )
    weekend_mask = scenario == "fin_semana_pico"
    weekend_dates = pd.DatetimeIndex(date[weekend_mask])
    weekend_offsets = (5 - weekend_dates.dayofweek.to_numpy()) % 7
    weekend_offsets += rng.integers(0, 2, size=weekend_mask.sum())
    date[weekend_mask] += pd.to_timedelta(
        weekend_offsets,
        unit="D",
    ).to_numpy(copy=True)
    date_index = pd.DatetimeIndex(date)
    planned_duration = np.clip(
        rng.normal(
            loc=np.select(
                [
                    route == "R-01",
                    route == "R-02",
                    route == "R-03",
                    route == "R-04",
                    route == "R-05",
                ],
                [38, 46, 53, 58, 42],
                default=64,
            ),
            scale=9,
        ),
        20,
        90,
    ).round()
    morning_peak = ((hour >= 6) & (hour <= 9)).astype(int)
    evening_peak = ((hour >= 16) & (hour <= 19)).astype(int)
    weekday_traffic = (date_index.dayofweek < 5).astype(int)
    demand = np.clip(
        25
        + 18 * morning_peak
        + 22 * evening_peak
        + 9 * weekday_traffic
        + rng.normal(0, 14, sample_count),
        0,
        100,
    ).round(1)
    demand[scenario == "demanda_alta"] = rng.uniform(
        65,
        100,
        size=(scenario == "demanda_alta").sum(),
    ).round(1)
    demand[scenario == "demanda_baja"] = rng.uniform(
        0,
        25,
        size=(scenario == "demanda_baja").sum(),
    ).round(1)
    demand[weekend_mask] = np.maximum(
        demand[weekend_mask],
        rng.uniform(45, 85, size=weekend_mask.sum()),
    ).round(1)
    planned_duration[scenario == "duracion_extensa"] = rng.uniform(
        90,
        240,
        size=(scenario == "duracion_extensa").sum(),
    ).round()
    route_effect = (
        pd.Series(route)
        .map(
            {
                "R-01": 0.35,
                "R-02": 0.10,
                "R-03": -0.20,
                "R-04": 0.25,
                "R-05": -0.05,
                "R-06": 0.45,
            }
        )
        .to_numpy()
    )
    # Latent risk is stronger during rush hours and when demand is high.
    risk_log_odds = (
        -4.5
        + 2.0 * morning_peak
        + 2.3 * evening_peak
        + 0.065 * (demand - 50)
        + 0.035 * (planned_duration - 45)
        + 0.45 * weekday_traffic
        + 1.25 * route_effect
    )
    delay_probability = 1 / (1 + np.exp(-risk_log_odds))
    has_delay = rng.random(sample_count) < delay_probability
    delay_minutes = np.where(
        has_delay,
        10.1 + rng.gamma(shape=2.2, scale=6.0, size=sample_count),
        rng.uniform(0, 10, size=sample_count),
    )

    data = pd.DataFrame(
        {
            "fecha_viaje": date,
            "ruta": route,
            "dia_semana": date_index.dayofweek.map(
                {
                    0: "Lunes",
                    1: "Martes",
                    2: "Miércoles",
                    3: "Jueves",
                    4: "Viernes",
                    5: "Sábado",
                    6: "Domingo",
                }
            ),
            "hora_salida": hour,
            "duracion_planificada_min": planned_duration,
            "indice_demanda_pronosticada": demand,
            "escenario_sintetico": scenario,
            "retraso_minutos": delay_minutes.round(1),
        }
    ).sort_values("fecha_viaje", ignore_index=True)
    data["retraso_mayor_10_min"] = (data["retraso_minutos"] > 10).astype(int)
    return data


def build_route_scenario_dataset(
    sample_count: int = 5_000,
    random_state: int = 20_261_103,
) -> pd.DataFrame:
    if sample_count < 100:
        raise ValueError("sample_count debe ser al menos 100.")

    rng = np.random.default_rng(random_state)
    trip_duration = rng.uniform(25, 65, sample_count)
    actual_eta = np.clip(
        trip_duration[:, None] + rng.normal(0, 7, (sample_count, 3)),
        12,
        110,
    )
    departures = rng.integers(2, 13, size=(sample_count, 3))
    actual_weather_delay = rng.gamma(shape=1.5, scale=1.2, size=(sample_count, 3))
    eta_forecast = np.clip(
        actual_eta + rng.normal(0, 3.5, size=(sample_count, 3)),
        10,
        120,
    )
    weather_forecast = np.clip(
        actual_weather_delay + rng.normal(0, 1.0, size=(sample_count, 3)),
        0,
        15,
    )
    scenario_ids = np.repeat(np.arange(sample_count), 3)
    candidate_index = np.tile(np.arange(3), sample_count)
    return pd.DataFrame(
        {
            "scenario_id": scenario_ids,
            "codigo": [f"R-{index + 1:02d}" for index in candidate_index],
            "es_habitual": candidate_index == 0,
            "eta_min": eta_forecast.ravel().round(1),
            "salidas_por_hora": departures.ravel(),
            "demora_clima_min": weather_forecast.ravel().round(1),
            "eta_real_min": actual_eta.ravel().round(1),
            "demora_clima_real_min": actual_weather_delay.ravel().round(1),
            "tiempo_generalizado_real_min": (
                actual_eta + 30 / departures + actual_weather_delay
            ).ravel().round(1),
        }
    )


def build_destination_history_dataset(
    user_count: int = 800,
    place_count: int = 24,
    random_state: int = 20_261_104,
) -> pd.DataFrame:
    if user_count < 100 or place_count < 5:
        raise ValueError("Se requieren al menos 100 usuarios y 5 destinos.")

    rng = np.random.default_rng(random_state)
    place_popularity = rng.lognormal(mean=0, sigma=0.4, size=place_count)
    user_affinity = rng.gamma(shape=2.0, scale=0.55, size=(user_count, place_count))
    user_activity = rng.gamma(shape=2.0, scale=0.6, size=user_count)
    expected_visits = (
        user_activity[:, None] * user_affinity * place_popularity[None, :] * 2.5
    )
    visits_30d = rng.poisson(expected_visits)
    visits_7d = rng.binomial(visits_30d, 7 / 30)

    days_since_last_visit = rng.integers(31, 91, size=(user_count, place_count))
    recent_mask = visits_7d > 0
    days_since_last_visit[recent_mask] = np.minimum(
        rng.geometric(
            np.clip(visits_7d[recent_mask] / 7, 0.1, 0.9)
        )
        - 1,
        6,
    )
    older_mask = (visits_30d > visits_7d) & ~recent_mask
    days_since_last_visit[older_mask] = np.minimum(
        7
        + rng.geometric(
            np.clip((visits_30d[older_mask] - visits_7d[older_mask]) / 23, 0.05, 0.8)
        )
        - 1,
        30,
    )

    recency_signal = np.exp(-days_since_last_visit / 14)
    future_visit_rate = expected_visits * (7 / 30) * (
        0.9 + 0.2 * recency_signal
    )
    future_visits = rng.poisson(future_visit_rate)

    user_ids = np.repeat(np.arange(user_count), place_count)
    place_ids = np.tile(np.arange(place_count), user_count)
    return pd.DataFrame(
        {
            "usuario_sintetico": user_ids,
            "id": [f"D-{index + 1:03d}" for index in place_ids],
            "nombre": [f"Lugar {index + 1:03d}" for index in place_ids],
            "tipo": "destino",
            "barrio": "Sector Norte",
            "visitas_7d": visits_7d.ravel(),
            "visitas_30d": visits_30d.ravel(),
            "dias_desde_ultima_visita": days_since_last_visit.ravel(),
            "visitas_futuras_7d_sinteticas": future_visits.ravel(),
        }
    )
