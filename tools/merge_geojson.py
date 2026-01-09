import argparse
import json
from pathlib import Path


def load_features(path: Path):
    if not path.exists():
        raise FileNotFoundError(path)
    data = json.loads(path.read_text(encoding="utf-8"))
    if data.get("type") != "FeatureCollection":
        raise ValueError(f"{path} is not a FeatureCollection")
    features = data.get("features")
    if not isinstance(features, list):
        raise ValueError(f"{path} has invalid features")
    return features


def main():
    parser = argparse.ArgumentParser(description="Merge road and walkway GeoJSON files.")
    parser.add_argument(
        "--roads",
        default="data/raw/gbuc-jianggao-roads.geojson",
        help="Road GeoJSON path",
    )
    parser.add_argument(
        "--walkways",
        default="data/raw/gbuc-jianggao-campus-walkways.geojson",
        help="Campus walkways GeoJSON path",
    )
    parser.add_argument(
        "--output",
        default="data/raw/gbuc-jianggao-roads-merged.geojson",
        help="Output GeoJSON path",
    )
    args = parser.parse_args()

    roads_path = Path(args.roads)
    walkways_path = Path(args.walkways)
    output_path = Path(args.output)

    road_features = load_features(roads_path)
    walkway_features = load_features(walkways_path)

    merged = {
        "type": "FeatureCollection",
        "features": road_features + walkway_features,
    }

    output_path.write_text(json.dumps(merged, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Merged {len(road_features)} road features + {len(walkway_features)} walkway features -> {output_path}")


if __name__ == "__main__":
    main()
