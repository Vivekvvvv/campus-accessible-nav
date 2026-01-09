#!/usr/bin/env python3
import argparse
import json
import os
import sys
import textwrap
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


def build_building_query(bbox):
    min_lat, min_lng, max_lat, max_lng = bbox
    return textwrap.dedent(
        f"""\
[out:json][timeout:60];
(
  way["building"]({min_lat},{min_lng},{max_lat},{max_lng});
  relation["building"]({min_lat},{min_lng},{max_lat},{max_lng});
);
out body;
>;
out skel qt;
"""
    )


def build_poi_query(bbox):
    min_lat, min_lng, max_lat, max_lng = bbox
    return textwrap.dedent(
        f"""\
[out:json][timeout:60];
(
  node["amenity"]({min_lat},{min_lng},{max_lat},{max_lng});
  node["shop"]({min_lat},{min_lng},{max_lat},{max_lng});
  node["tourism"]({min_lat},{min_lng},{max_lat},{max_lng});
  node["leisure"]({min_lat},{min_lng},{max_lat},{max_lng});
  node["office"]({min_lat},{min_lng},{max_lat},{max_lng});
  node["public_transport"]({min_lat},{min_lng},{max_lat},{max_lng});
  node["highway"="bus_stop"]({min_lat},{min_lng},{max_lat},{max_lng});
  node["building"="entrance"]({min_lat},{min_lng},{max_lat},{max_lng});
);
out body;
"""
    )


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


def index_elements(elements):
    nodes = {}
    ways = {}
    relations = {}
    for el in elements or []:
        if not isinstance(el, dict):
            continue
        el_type = el.get("type")
        if el_type == "node":
            nodes[el["id"]] = el
        elif el_type == "way":
            ways[el["id"]] = el
        elif el_type == "relation":
            relations[el["id"]] = el
    return nodes, ways, relations


def close_ring(coords):
    if not coords or len(coords) < 3:
        return None
    if coords[0] != coords[-1]:
        coords = coords + [coords[0]]
    if len(coords) < 4:
        return None
    return coords


def way_to_ring(way, nodes):
    if not way or "nodes" not in way:
        return None
    coords = []
    for node_id in way["nodes"]:
        node = nodes.get(node_id)
        if not node:
            continue
        lat = node.get("lat")
        lon = node.get("lon")
        if lat is None or lon is None:
            continue
        coords.append([lon, lat])
    return close_ring(coords)


def pick_name(tags, fallback):
    if not tags:
        return fallback
    for key in ("name:zh", "name", "name:en", "short_name", "brand", "operator"):
        value = tags.get(key)
        if value:
            return value
    return fallback


def build_properties(tags, fallback_name, osm_id):
    props = {}
    name = pick_name(tags, fallback_name)
    if name:
        props["name"] = name
    if tags:
        keys = [
            "building",
            "amenity",
            "shop",
            "office",
            "tourism",
            "leisure",
            "public_transport",
            "highway",
            "type",
            "category",
            "level",
        ]
        for key in keys:
            if key in tags and tags[key]:
                props[key] = tags[key]
        category = (
            tags.get("amenity")
            or tags.get("shop")
            or tags.get("office")
            or tags.get("tourism")
            or tags.get("leisure")
            or tags.get("public_transport")
            or tags.get("highway")
            or tags.get("building")
        )
        if category and "category" not in props:
            props["category"] = category
        level = tags.get("level")
        if level:
            props["level"] = level
    if osm_id:
        props["osmId"] = osm_id
    props["source"] = "OSM"
    return props


def extract_building_features(elements):
    nodes, ways, relations = index_elements(elements)
    features = []
    for way in list(ways.values()):
        if not way.get("tags") or not way["tags"].get("building"):
            continue
        ring = way_to_ring(way, nodes)
        if not ring:
            continue
        props = build_properties(way.get("tags"), f"Building {way['id']}", f"way/{way['id']}")
        features.append(
            {
                "type": "Feature",
                "properties": props,
                "geometry": {"type": "Polygon", "coordinates": [ring]},
            }
        )

    for rel in list(relations.values()):
        tags = rel.get("tags") or {}
        if not tags.get("building") or not rel.get("members"):
            continue
        polygons = []
        for member in rel["members"]:
            if member.get("type") != "way":
                continue
            if member.get("role") and member["role"] != "outer":
                continue
            way = ways.get(member["ref"])
            if not way:
                continue
            ring = way_to_ring(way, nodes)
            if not ring:
                continue
            polygons.append([ring])
        if not polygons:
            continue
        props = build_properties(tags, f"Building {rel['id']}", f"relation/{rel['id']}")
        features.append(
            {
                "type": "Feature",
                "properties": props,
                "geometry": {"type": "MultiPolygon", "coordinates": polygons},
            }
        )
    return features


def extract_poi_features(elements):
    features = []
    for el in elements or []:
        if not el or el.get("type") != "node":
            continue
        lat = el.get("lat")
        lon = el.get("lon")
        if lat is None or lon is None:
            continue
        tags = el.get("tags")
        if not tags:
            continue
        props = build_properties(tags, f"POI {el['id']}", f"node/{el['id']}")
        features.append(
            {
                "type": "Feature",
                "properties": props,
                "geometry": {"type": "Point", "coordinates": [lon, lat]},
            }
        )
    return features


def write_geojson(output_path, features):
    obj = {"type": "FeatureCollection", "features": features}
    Path(output_path).parent.mkdir(parents=True, exist_ok=True)
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)


def main():
    parser = argparse.ArgumentParser(
        description="导出广东白云学院江高校区的建筑/POI GeoJSON（Overpass）"
    )
    parser.add_argument("--bbox", required=True, help="minLat,minLng,maxLat,maxLng")
    parser.add_argument("--out-dir", default=os.path.join("frontend", "public", "data"))
    parser.add_argument("--prefix", default="gbuc-jianggao")
    parser.add_argument(
        "--overpass",
        default="https://overpass-api.de/api/interpreter",
        help="Overpass API endpoint",
    )
    parser.add_argument("--buildings-only", action="store_true")
    parser.add_argument("--poi-only", action="store_true")

    args = parser.parse_args()
    bbox = parse_bbox(args.bbox)
    if not bbox:
        parser.error("请提供合法的 bbox，格式为 minLat,minLng,maxLat,maxLng")

    if args.buildings_only and args.poi_only:
        parser.error("--buildings-only 与 --poi-only 不能同时使用")

    output_dir = Path(args.out_dir)
    prefix = args.prefix

    if not args.poi_only:
        print("正在拉取建筑数据...")
        buildings = fetch_overpass(args.overpass, build_building_query(bbox)).get("elements", [])
        building_features = extract_building_features(buildings)
        out_path = output_dir / f"{prefix}-buildings.geojson"
        write_geojson(out_path, building_features)
        print(f"输出建筑：{len(building_features)} 条 -> {out_path}")

    if not args.buildings_only:
        print("正在拉取 POI...")
        poi = fetch_overpass(args.overpass, build_poi_query(bbox)).get("elements", [])
        poi_features = extract_poi_features(poi)
        out_path = output_dir / f"{prefix}-poi.geojson"
        write_geojson(out_path, poi_features)
        print(f"输出 POI：{len(poi_features)} 条 -> {out_path}")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"导出失败：{exc}", file=sys.stderr)
        sys.exit(1)
