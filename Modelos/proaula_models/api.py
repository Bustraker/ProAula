"""API HTTP interna para los modelos y recomendaciones de ProAula."""

from typing import Annotated, Literal

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

from .delay_risk import get_delay_risk_model
from .recommendations import recommend_route, suggest_destinations


def _to_camel(value: str) -> str:
    first, *rest = value.split("_")
    return first + "".join(part.capitalize() for part in rest)


class ApiModel(BaseModel):
    model_config = ConfigDict(alias_generator=_to_camel, populate_by_name=True)


class DelayRiskRequest(ApiModel):
    ruta: str = Field(min_length=1, max_length=100)
    dia_semana: Literal["Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"]
    hora_salida: int = Field(ge=0, le=23)
    duracion_planificada_min: float = Field(gt=0, le=240)
    indice_demanda_pronosticada: float = Field(ge=0, le=100)

    @field_validator("ruta")
    @classmethod
    def validate_route_id(cls, value: str) -> str:
        normalized = value.strip()
        if not normalized:
            raise ValueError("ruta no puede estar vacía.")
        return normalized


class RouteOption(ApiModel):
    codigo: str = Field(min_length=1, max_length=100)
    eta_min: float = Field(gt=0, le=600)
    salidas_por_hora: float = Field(gt=0, le=60)
    demora_clima_min: float = Field(ge=0, le=180, default=0)
    congestion_pct: float = Field(ge=0, le=100, default=0)


class RouteRecommendationRequest(ApiModel):
    habitual: RouteOption
    alternativas: Annotated[list[RouteOption], Field(min_length=1, max_length=20)]


class Destination(ApiModel):
    id: str = Field(min_length=1, max_length=100)
    nombre: str = Field(min_length=1, max_length=255)
    tipo: str = Field(min_length=1, max_length=50)
    barrio: str | None = Field(default=None, max_length=255)


class VisitHistory(ApiModel):
    visitas_7d: int = Field(ge=0, le=10_000)
    visitas_30d: int = Field(ge=0, le=50_000)
    dias_desde_ultima_visita: int = Field(ge=0, le=3_650)


class DestinationSuggestionRequest(ApiModel):
    consulta: str = Field(min_length=1, max_length=100)
    catalogo: Annotated[list[Destination], Field(min_length=1, max_length=500)]
    historial: dict[str, VisitHistory] = Field(default_factory=dict)
    limite: int = Field(default=8, ge=1, le=50)

    @model_validator(mode="after")
    def validate_unique_destinations(self) -> "DestinationSuggestionRequest":
        ids = [destination.id for destination in self.catalogo]
        if len(ids) != len(set(ids)):
            raise ValueError("Los identificadores del catálogo deben ser únicos.")
        if not set(self.historial).issubset(ids):
            raise ValueError("El historial solo puede incluir destinos del catálogo.")
        return self


app = FastAPI(
    title="ProAula Predictive Models",
    description="Clasificación de riesgo y recomendaciones de movilidad.",
    version="1.0.0",
)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/v1/models/delay-risk/metrics")
def delay_risk_metrics() -> dict[str, object]:
    model = get_delay_risk_model()
    return {
        "modelo": "HistGradientBoostingClassifier (clasificación binaria)",
        "origenDatos": model.data_origin,
        "metricas": model.metrics,
    }


@app.post("/v1/predictions/delay-risk")
def predict_delay_risk(request: DelayRiskRequest) -> dict[str, object]:
    return get_delay_risk_model().predict(request.model_dump(by_alias=False))


@app.post("/v1/recommendations/routes")
def recommend_routes(request: RouteRecommendationRequest) -> dict[str, object]:
    try:
        return recommend_route(
            request.habitual.model_dump(by_alias=False),
            [route.model_dump(by_alias=False) for route in request.alternativas],
        )
    except ValueError as error:
        raise HTTPException(status_code=422, detail=str(error)) from error


@app.post("/v1/recommendations/destinations")
def recommend_destinations(request: DestinationSuggestionRequest) -> dict[str, object]:
    try:
        suggestions = suggest_destinations(
            request.consulta,
            [place.model_dump(by_alias=False) for place in request.catalogo],
            {
                place_id: history.model_dump(by_alias=False)
                for place_id, history in request.historial.items()
            },
            request.limite,
        )
    except ValueError as error:
        raise HTTPException(status_code=422, detail=str(error)) from error
    return {
        "sugerencias": suggestions,
        "metodo": "Estimación de visitas semanales por frecuencia y recencia",
        "origenDatos": "HISTORIAL_PROPORCIONADO_POR_LA_APLICACION",
    }
