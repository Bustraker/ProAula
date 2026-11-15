"""Recomendaciones basadas en ordenamiento y señales observables."""

import math
import unicodedata
from typing import Any


MINIMUM_ROUTE_SAVING_MIN = 5.0


def _normalize(value: str) -> str:
    normalized = unicodedata.normalize("NFKD", value.casefold())
    return "".join(char for char in normalized if not unicodedata.combining(char))


def _route_cost(route: dict[str, Any]) -> float:
    # ETA must already account for traffic; adding congestion again would double-count it.
    eta = route["eta_min"]
    hourly_departures = route["salidas_por_hora"]
    weather_delay = route.get("demora_clima_min", 0.0)
    return eta + (30 / hourly_departures) + weather_delay


def recommend_route(
    habitual: dict[str, Any],
    alternatives: list[dict[str, Any]],
) -> dict[str, Any]:
    if not alternatives:
        raise ValueError("Se requiere al menos una ruta alternativa.")

    scored = [
        {
            **route,
            "tiempoGeneralizadoMin": round(_route_cost(route), 2),
        }
        for route in [habitual, *alternatives]
    ]
    best_alternative = min(scored[1:], key=lambda route: route["tiempoGeneralizadoMin"])
    habitual_cost = scored[0]["tiempoGeneralizadoMin"]
    alternative_cost = best_alternative["tiempoGeneralizadoMin"]

    if alternative_cost + MINIMUM_ROUTE_SAVING_MIN < habitual_cost:
        recommended = best_alternative
        reason = (
            f"La alternativa ahorra al menos {MINIMUM_ROUTE_SAVING_MIN:g} minutos "
            "de tiempo generalizado frente a la ruta habitual."
        )
    else:
        recommended = scored[0]
        reason = (
            "La mejora estimada no supera el umbral mínimo; se conserva la ruta habitual."
        )

    ranked = sorted(scored, key=lambda route: route["tiempoGeneralizadoMin"])
    ranked = [
        {
            "codigo": route["codigo"],
            "etaMin": route["eta_min"],
            "salidasPorHora": route["salidas_por_hora"],
            "demoraClimaMin": route.get("demora_clima_min", 0.0),
            "congestionPct": route.get("congestion_pct", 0.0),
            "tiempoGeneralizadoMin": route["tiempoGeneralizadoMin"],
        }
        for route in ranked
    ]
    return {
        "rutaRecomendada": recommended["codigo"],
        "tiempoGeneralizadoMin": recommended["tiempoGeneralizadoMin"],
        "motivo": reason,
        "metodo": "Ordenamiento por tiempo estimado, espera y demora climática",
        "origenDatos": "ESTIMACIONES_DEMOSTRACION",
        "rutasOrdenadas": ranked,
    }


def suggest_destinations(
    query: str,
    catalog: list[dict[str, Any]],
    history: dict[str, dict[str, Any]],
    limit: int = 8,
) -> list[dict[str, Any]]:
    if not query.strip():
        raise ValueError("La consulta no puede estar vacía.")
    if not 1 <= limit <= 50:
        raise ValueError("limit debe estar entre 1 y 50.")

    normalized_query = _normalize(query.strip())
    matches = [
        place
        for place in catalog
        if normalized_query in _normalize(
            " ".join(
                str(place.get(field, ""))
                for field in ("nombre", "tipo", "barrio")
                if place.get(field)
            )
        )
    ]

    suggestions = []
    for place in matches:
        visits = history.get(place["id"], {})
        visits_7d = max(0, int(visits.get("visitas_7d", 0)))
        visits_30d = max(0, int(visits.get("visitas_30d", 0)))
        days_since_last_visit = max(0, int(visits.get("dias_desde_ultima_visita", 365)))
        recent_rate = visits_7d / 7
        monthly_rate = visits_30d / 30
        estimated_weekly_visits = (
            7
            * (0.05 * recent_rate + 0.95 * monthly_rate)
            * (0.9 + 0.2 * math.exp(-days_since_last_visit / 14))
        )
        suggestions.append(
            {
                **place,
                "puntuacion": round(estimated_weekly_visits, 4),
                "motivo": (
                    "Ordenado por visitas semanales esperadas, frecuencia y recencia."
                    if visits_7d or visits_30d
                    else "Sin historial de visitas; ordenado alfabéticamente."
                ),
            }
        )

    suggestions.sort(key=lambda place: (-place["puntuacion"], _normalize(place["nombre"])))
    return suggestions[:limit]
