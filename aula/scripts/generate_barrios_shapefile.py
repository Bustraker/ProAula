import json
import os
import shapefile
from pyproj import CRS, Transformer

folder = r"c:/Users/HP/OneDrive/Desktop/Programas/Samuel/Sprint boot/Universidad/ProAula/Barrios_Ctg"
shp_path = os.path.join(folder, "Barrios_Ctg.shp")
reader = shapefile.Reader(shp_path)
prj_path = os.path.join(folder, "Barrios_Ctg.prj")

with open(prj_path, "r", encoding="utf-8") as f:
    prj_text = f.read().strip()

src_crs = CRS.from_wkt(prj_text)
transformer = Transformer.from_crs(src_crs, CRS.from_epsg(4326), always_xy=True)

output = []
for rec, shape in zip(reader.iterRecords(), reader.iterShapes()):
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
        "codigo": codigo.strip(),
        "nombre": nombre.strip(),
        "zona": zona.strip(),
        "ucg": ucg,
        "loc": loc.strip(),
        "lat": round(lat, 6),
        "lng": round(lon, 6),
    })

output_path = r"c:/Users/HP/OneDrive/Desktop/Programas/Samuel/Sprint boot/Universidad/ProAula/aula/src/main/resources/data/barrios-shapefile.json"
os.makedirs(os.path.dirname(output_path), exist_ok=True)
with open(output_path, "w", encoding="utf-8") as f:
    json.dump(output, f, ensure_ascii=False, indent=2)

print("Wrote", len(output), "records to", output_path)
