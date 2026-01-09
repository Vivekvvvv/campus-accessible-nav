#!/usr/bin/env python3
import argparse
import json
import math
import os
import urllib.parse
import urllib.request
from pathlib import Path


def parse_bbox(value: str):
    try:
        parts = [float(part) for part in value.split(",")]
    except (TypeError, ValueError):
        return None
    if len(parts) != 4:
        return None
    min_lat, min_lng, max_lat, max_lng = parts
    if not (min_lat < max_lat and min_lng < max_lng):
        return None
    return min_lat, min_lng, max_lat, max_lng


def build_query(bbox):
    min_lat, min_lng, max_lat, max_lng = bbox
    return f"""[out:json][timeout:120];
(
  way["highway"]({min_lat},{min_lng},{max_lat},{max_lng});
);
out geom;"""


def fetch_overpass(endpoint, query):
    data = urllib.parse.urlencode({"data": query}).encode()
    req = urllib.request.Request(
        endpoint,
        data=data,
        headers={
            "Content-Type": "application/x-www-form-urlencoded",
            "User-Agent": "AccessibleNav Data Pipeline",
        },
    )
    with urllib.request.urlopen(req, timeout=120) as resp:
        if not (200 <= resp.status < 300):
            body = resp.read().decode("utf-8", errors="ignore")
            raise RuntimeError(f"Overpass HTTP {resp.status}: {body[:400]}")
        payload = resp.read().decode("utf-8")
    return json.loads(payload)


def is_finite(value):
    return isinstance(value, (int, float)) and math.isfinite(value)


def build_features(elements, highway_filter):
    features = []
    allow = set(v.strip().lower() for v in highway_filter if v.strip())
    for el in elements or []:
        if not isinstance(el, dict) or el.get("type") != "way":
            continue
        tags = el.get("tags") or {}
        highway = str(tags.get("highway", "")).lower()
        if allow and highway not in allow:
            continue
        geom = el.get("geometry") or []
        coords = []
        for pt in geom:
            lat = pt.get("lat") if isinstance(pt, dict) else None
            lng = pt.get("lon") if isinstance(pt, dict) else None
            if not (is_finite(lat) and is_finite(lng)):
                continue
            coords.append([lng, lat])
        if len(coords) < 2:
            continue
        props = dict(tags)
        if el.get("id"):
            props["osmId"] = f"way/{el['id']}"
        props.setdefault("source", "OSM")
        features.append(
            {
                "type": "Feature",
                "properties": props,
                "geometry": {"type": "LineString", "coordinates": coords},
            }
        )
    return features


def write_geojson(output_path, features):
    Path(output_path).parent.mkdir(parents=True, exist_ok=True)
    payload = {"type": "FeatureCollection", "features": features}
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=True, indent=2)


def main():
    parser = argparse.ArgumentParser(description="Export highway ways from OSM (Overpass) to GeoJSON.")
    parser.add_argument("--bbox", required=True, help="minLat,minLng,maxLat,maxLng")
    parser.add_argument("--out", default="", help="Output file path")
    parser.add_argument("--out-dir", default=os.path.join("data", "raw"))
    parser.add_argument("--prefix", default="gbuc-jianggao")
    parser.add_argument(
        "--overpass",
        default="https://overpass-api.de/api/interpreter",
        help="Overpass API endpoint",
    )
    parser.add_argument(
        "--highway",
        default="",
        help="Comma-separated highway values to keep (optional)",
    )
    args = parser.parse_args()

    bbox = parse_bbox(args.bbox)
    if not bbox:
        parser.error("Invalid bbox, expected minLat,minLng,maxLat,maxLng")

    output = args.out.strip()
    if not output:
        output = str(Path(args.out_dir) / f"{args.prefix}-roads.geojson")

    query = build_query(bbox)
    data = fetch_overpass(args.overpass, query)
    features = build_features(data.get("elements", []), args.highway.split(","))
    write_geojson(output, features)
    print(f"Roads: {len(features)} -> {output}")


if __name__ == "__main__":
    main()
