package com.demo.accessiblenav.graph;

import com.demo.accessiblenav.graph.dto.GraphImportRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GraphImportPayloadBuilder {

    private final Map<String, GraphImportRequest.NodeUpsert> nodesByKey = new LinkedHashMap<>();
    private final List<GraphImportRequest.EdgeCreate> edges = new ArrayList<>();

    public GraphImportRequest build() {
        GraphImportRequest req = new GraphImportRequest();
        req.setNodes(new ArrayList<>(nodesByKey.values()));
        req.setEdges(new ArrayList<>(edges));
        return req;
    }

    public void merge(GraphImportRequest other) {
        if (other == null) return;
        if (other.getNodes() != null) {
            for (GraphImportRequest.NodeUpsert node : other.getNodes()) {
                if (node == null || node.getKey() == null) continue;
                nodesByKey.putIfAbsent(node.getKey(), node);
            }
        }
        if (other.getEdges() != null) {
            for (GraphImportRequest.EdgeCreate edge : other.getEdges()) {
                if (edge == null) continue;
                edges.add(edge);
            }
        }
    }

    public void addGeoJson(JsonNode root) {
        if (root == null) return;
        if (root.has("features") && root.get("features").isArray()) {
            ArrayNode features = (ArrayNode) root.get("features");
            for (JsonNode feature : features) {
                addFeature(feature);
            }
        } else if (root.has("type") && root.get("type").asText("").equals("Feature")) {
            addFeature(root);
        } else if (root.has("coordinates")) {
            addGeometry(root);
        }
    }

    private void addFeature(JsonNode feature) {
        if (feature == null) return;
        JsonNode geometry = feature.get("geometry");
        addGeometry(geometry);
    }

    private void addGeometry(JsonNode geometry) {
        if (geometry == null || !geometry.has("type")) return;
        String type = geometry.get("type").asText("");
        JsonNode coords = geometry.get("coordinates");
        if (coords == null) return;
        switch (type) {
            case "LineString":
                addLineString(coords, false);
                break;
            case "MultiLineString":
                for (JsonNode part : coords) {
                    addLineString(part, false);
                }
                break;
            default:
                break;
        }
    }

    private void addLineString(JsonNode coords, boolean closeRing) {
        if (coords == null || !coords.isArray()) return;
        List<List<Double>> pts = new ArrayList<>();
        for (JsonNode pt : coords) {
            if (pt.isArray() && pt.size() >= 2) {
                Double lng = safeDouble(pt.get(0));
                Double lat = safeDouble(pt.get(1));
                if (lng != null && lat != null) {
                    List<Double> pair = new ArrayList<>();
                    pair.add(lng);
                    pair.add(lat);
                    pts.add(pair);
                }
            }
        }
        if (pts.size() < 2) return;
        for (int i = 0; i < pts.size() - 1; i++) {
            List<Double> a = pts.get(i);
            List<Double> b = pts.get(i + 1);
            addEdge(a.get(0), a.get(1), b.get(0), b.get(1), false);
        }
        if (closeRing && pts.size() > 2) {
            List<Double> first = pts.get(0);
            List<Double> last = pts.get(pts.size() - 1);
            if (!first.equals(last)) {
                addEdge(last.get(0), last.get(1), first.get(0), first.get(1), false);
            }
        }
    }

    public void addPath(List<double[]> coordinates) {
        if (coordinates == null || coordinates.size() < 2) return;
        for (int i = 0; i < coordinates.size() - 1; i++) {
            double[] a = coordinates.get(i);
            double[] b = coordinates.get(i + 1);
            addEdge(a[0], a[1], b[0], b[1], false);
        }
    }

    private void addEdge(double lng1, double lat1, double lng2, double lat2, boolean oneway) {
        String fromKey = addNode(lng1, lat1);
        String toKey = addNode(lng2, lat2);
        double distance = haversineMeters(lng1, lat1, lng2, lat2);
        GraphImportRequest.EdgeCreate edge = new GraphImportRequest.EdgeCreate();
        edge.setFromKey(fromKey);
        edge.setToKey(toKey);
        edge.setDistanceM(round(distance).doubleValue());
        edge.setOneway(oneway);
        edge.setHasStairs(false);
        edge.setSlopeLevel(0);
        edge.setAccessibleDefault(true);
        edge.setBaseCost(round(distance).doubleValue());
        edges.add(edge);
        if (!oneway) {
            GraphImportRequest.EdgeCreate reverse = new GraphImportRequest.EdgeCreate();
            reverse.setFromKey(toKey);
            reverse.setToKey(fromKey);
            reverse.setDistanceM(round(distance).doubleValue());
            reverse.setOneway(false);
            reverse.setHasStairs(false);
            reverse.setSlopeLevel(0);
            reverse.setAccessibleDefault(true);
            reverse.setBaseCost(round(distance).doubleValue());
            edges.add(reverse);
        }
    }

    private String addNode(double lng, double lat) {
        String key = nodeKey(lng, lat);
        if (!nodesByKey.containsKey(key)) {
            GraphImportRequest.NodeUpsert node = new GraphImportRequest.NodeUpsert();
            node.setKey(key);
            node.setLat(formatLat(lat));
            node.setLng(formatLng(lng));
            node.setNodeType("NORMAL");
            node.setExtraJson(null);
            nodesByKey.put(key, node);
        }
        return key;
    }

    static String nodeKey(double lng, double lat) {
        return formatLat(lat) + "," + formatLng(lng);
    }

    static String formatLat(double lat) {
        return round(lat).toPlainString();
    }

    static String formatLng(double lng) {
        return round(lng).toPlainString();
    }

    private static BigDecimal round(double value) {
        return new BigDecimal(value).setScale(7, RoundingMode.HALF_UP);
    }

    private static double haversineMeters(double aLng, double aLat, double bLng, double bLat) {
        final double R = 6371000;
        double dLat = Math.toRadians(bLat - aLat);
        double dLng = Math.toRadians(bLng - aLng);
        double lat1 = Math.toRadians(aLat);
        double lat2 = Math.toRadians(bLat);
        double s = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(s), Math.sqrt(1 - s));
        return R * c;
    }

    private Double safeDouble(JsonNode node) {
        if (node == null || !node.isNumber()) return null;
        return node.asDouble();
    }
}
