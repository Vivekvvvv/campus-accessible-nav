#!/usr/bin/env python3
import argparse
import json
import math
from pathlib import Path


def haversine_m(lat1, lng1, lat2, lng2):
    r = 6371000.0
    dlat = math.radians(lat2 - lat1)
    dlng = math.radians(lng2 - lng1)
    a = math.sin(dlat / 2) ** 2 + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(dlng / 2) ** 2
    return r * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))


def load_graph(path):
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    nodes = {n["key"]: (float(n["lat"]), float(n["lng"])) for n in data.get("nodes", [])}
    edges = data.get("edges", [])
    return data, nodes, edges


def build_components(nodes, edges):
    adj = {k: set() for k in nodes}
    for edge in edges:
        fk = edge.get("fromKey")
        tk = edge.get("toKey")
        if fk not in nodes or tk not in nodes:
            continue
        adj[fk].add(tk)
        if not edge.get("oneway", False):
            adj[tk].add(fk)

    visited = set()
    comps = []
    comp_id = {}
    for node_key in nodes:
        if node_key in visited:
            continue
        stack = [node_key]
        visited.add(node_key)
        comp = []
        while stack:
            cur = stack.pop()
            comp.append(cur)
            for nxt in adj.get(cur, []):
                if nxt not in visited:
                    visited.add(nxt)
                    stack.append(nxt)
        cid = len(comps)
        for n in comp:
            comp_id[n] = cid
        comps.append(comp)
    return comps, comp_id


def centroid(feature):
    geom = feature.get("geometry") or {}
    gtype = geom.get("type")
    coords = geom.get("coordinates")
    if gtype == "Point":
        if not coords or len(coords) < 2:
            return None
        return coords[1], coords[0]
    if gtype == "Polygon":
        ring = coords[0] if coords else []
        if not ring:
            return None
        lat = sum(p[1] for p in ring) / len(ring)
        lng = sum(p[0] for p in ring) / len(ring)
        return lat, lng
    if gtype == "MultiPolygon":
        ring = coords[0][0] if coords and coords[0] else []
        if not ring:
            return None
        lat = sum(p[1] for p in ring) / len(ring)
        lng = sum(p[0] for p in ring) / len(ring)
        return lat, lng
    return None


def load_poi_components(poi_paths, nodes, comp_id):
    comp_hits = set()
    for path in poi_paths:
        data = json.loads(Path(path).read_text(encoding="utf-8"))
        for feature in data.get("features", []):
            c = centroid(feature)
            if not c:
                continue
            lat, lng = c
            best_key = None
            best_dist = float("inf")
            for key, (nlat, nlng) in nodes.items():
                d = (lat - nlat) ** 2 + (lng - nlng) ** 2
                if d < best_dist:
                    best_dist = d
                    best_key = key
            if best_key is not None:
                comp_hits.add(comp_id.get(best_key))
    return comp_hits


def nearest_pair(nodes, comp_a, comp_b):
    best = (None, None, float("inf"))
    for a in comp_a:
        lat1, lng1 = nodes[a]
        for b in comp_b:
            lat2, lng2 = nodes[b]
            d = haversine_m(lat1, lng1, lat2, lng2)
            if d < best[2]:
                best = (a, b, d)
    return best


def edge_key(a, b):
    return f"{min(a, b)}|{max(a, b)}"


def expand_oneway_edges(edges):
    existing = set()
    for e in edges:
        fk = e.get("fromKey")
        tk = e.get("toKey")
        if fk and tk:
            existing.add((fk, tk))

    added = 0
    new_edges = []
    for e in edges:
        fk = e.get("fromKey")
        tk = e.get("toKey")
        if not fk or not tk:
            continue
        if not e.get("oneway", False):
            continue
        if (tk, fk) in existing:
            continue
        reverse = dict(e)
        reverse["fromKey"] = tk
        reverse["toKey"] = fk
        new_edges.append(reverse)
        existing.add((tk, fk))
        added += 1
    edges.extend(new_edges)
    return added


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--graph", default="data/gbuc-jianggao/graph-import.json")
    parser.add_argument("--max-distance", type=float, default=200.0)
    parser.add_argument("--expand-oneway", action="store_true",
                        help="Add reverse edges for oneway links to make the graph bidirectional")
    parser.add_argument("--poi", action="append", default=[
        "frontend/public/data/gbuc-jianggao-buildings.geojson",
        "frontend/public/data/gbuc-jianggao-poi.geojson",
    ])
    args = parser.parse_args()

    data, nodes, edges = load_graph(args.graph)
    added_oneway = 0
    if args.expand_oneway:
        added_oneway = expand_oneway_edges(edges)
    comps, comp_id = build_components(nodes, edges)
    if not comps:
        print("No components found.")
        return

    largest_id = max(range(len(comps)), key=lambda i: len(comps[i]))
    poi_comp_ids = load_poi_components(args.poi, nodes, comp_id)

    existing = set()
    directed = set()
    for e in edges:
        fk = e.get("fromKey")
        tk = e.get("toKey")
        if fk and tk:
            existing.add(edge_key(fk, tk))
            directed.add((fk, tk))

    added = 0
    for cid, comp in enumerate(comps):
        if cid == largest_id:
            continue
        a, b, dist = nearest_pair(nodes, comp, comps[largest_id])
        if a is None or b is None:
            continue
        if cid not in poi_comp_ids and dist > args.max_distance:
            continue
        if edge_key(a, b) in existing:
            if (a, b) in directed and (b, a) in directed:
                continue
            existing_edge = None
            if (a, b) in directed:
                existing_edge = next((e for e in edges if e.get("fromKey") == a and e.get("toKey") == b), None)
            if existing_edge is None and (b, a) in directed:
                existing_edge = next((e for e in edges if e.get("fromKey") == b and e.get("toKey") == a), None)
            if existing_edge is None or not existing_edge.get("oneway", False):
                continue
            reverse = dict(existing_edge)
            reverse["fromKey"] = b
            reverse["toKey"] = a
            edges.append(reverse)
            directed.add((b, a))
            added += 1
            continue

        edge = {
            "fromKey": a,
            "toKey": b,
            "distanceM": round(dist, 3),
            "oneway": False,
            "hasStairs": False,
            "slopeLevel": 0,
            "accessibleDefault": True,
            "baseCost": round(dist, 3),
        }
        edges.append(edge)
        existing.add(edge_key(a, b))
        directed.add((a, b))
        added += 1

    if added or added_oneway:
        Path(args.graph).write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
        msg = f"Added {added} bridge edges."
        if added_oneway:
            msg += f" Added {added_oneway} reverse oneway edges."
        print(msg)
    else:
        print("No bridge edges added.")


if __name__ == "__main__":
    main()
