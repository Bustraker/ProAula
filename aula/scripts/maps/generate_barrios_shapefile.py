import json
from pathlib import Path

import shapefile  # type: ignore[import-not-found]
from pyproj import CRS, Transformer  # type: ignore[import-not-found]


def _clean_text(value: object) -> str:
    return "" if value is None else str(value).strip()


repository_root = Path(__file__).resolve().parents[3]
app_root = repository_root / "aula"
source_directory = repository_root / "data" / "maps" / "source" / "Barrios_Ctg"
reader = shapefile.Reader(str(source_directory / "Barrios_Ctg.shp"))
prj_path = source_directory / "Barrios_Ctg.prj"

with prj_path.open("r", encoding="utf-8") as f:
    prj_text = f.read().strip()

src_crs = CRS.from_wkt(prj_text)
transformer = Transformer.from_crs(src_crs, CRS.from_epsg(4326), always_xy=True)

output = []
for rec, shape in zip(reader.iterRecords(), reader.iterShapes()):
    if rec is None or shape is None:
        continue
    codigo, nombre, ucg, loc, zona = rec[0], rec[1], rec[2], rec[3], rec[4]
    points = shape.points
    if not points:
        continue
    xs = [p[0] for p in points]
    ys = [p[1] for p in points]
    centroid_x = sum(xs) / len(xs)
    centroid_y = sum(ys) / len(ys)
    lon, lat = transformer.transform(centroid_x, centroid_y)
    output.append({
        "codigo": _clean_text(codigo),
        "nombre": _clean_text(nombre),
        "zona": _clean_text(zona),
        "ucg": ucg,
        "loc": _clean_text(loc),
        "lat": round(lat, 6),
        "lng": round(lon, 6),
    })

output_path = app_root / "src" / "main" / "resources" / "data" / "barrios-shapefile.json"
output_path.parent.mkdir(parents=True, exist_ok=True)
with output_path.open("w", encoding="utf-8") as f:
    json.dump(output, f, ensure_ascii=False, indent=2)

print("Wrote", len(output), "records to", output_path)
