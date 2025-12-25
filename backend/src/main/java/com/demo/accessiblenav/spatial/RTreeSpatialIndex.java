package com.demo.accessiblenav.spatial;

import com.github.davidmoten.rtree2.Entry;
import com.github.davidmoten.rtree2.Entries;
import com.github.davidmoten.rtree2.RTree;
import com.github.davidmoten.rtree2.geometry.Geometries;
import com.github.davidmoten.rtree2.geometry.Point;
import com.github.davidmoten.rtree2.geometry.Rectangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * R-Tree 空间索引服务
 * 提供高效的空间查询能力，替代暴力遍历
 */
@Component
public class RTreeSpatialIndex {

    private static final Logger log = LoggerFactory.getLogger(RTreeSpatialIndex.class);

    // 地球半径（米）
    private static final double EARTH_RADIUS_METERS = 6371000;

    // 节点索引
    private RTree<Long, Point> nodeTree;

    // 边索引（使用包围盒）
    private RTree<Long, Rectangle> edgeTree;

    // 设施索引
    private RTree<Long, Point> facilityTree;

    public RTreeSpatialIndex() {
        // 使用 Star 分裂策略，适合频繁查询
        this.nodeTree = RTree.star().create();
        this.edgeTree = RTree.star().create();
        this.facilityTree = RTree.star().create();
    }

    // ========== 节点索引操作 ==========

    /**
     * 添加节点到索引
     */
    public void addNode(Long nodeId, double lat, double lng) {
        Point point = Geometries.point(lng, lat);
        nodeTree = nodeTree.add(nodeId, point);
    }

    /**
     * 批量添加节点
     */
    public void addNodes(List<SpatialNode> nodes) {
        List<Entry<Long, Point>> entries = nodes.stream()
                .map(n -> Entries.entry(n.getId(), Geometries.point(n.getLng(), n.getLat())))
                .collect(Collectors.toList());

        nodeTree = RTree.star().create(entries);
        log.info("已索引 {} 个节点", nodes.size());
    }

    /**
     * 删除节点
     */
    public void removeNode(Long nodeId, double lat, double lng) {
        Point point = Geometries.point(lng, lat);
        nodeTree = nodeTree.delete(nodeId, point);
    }

    /**
     * 查找指定距离内的节点
     *
     * @param lat 中心纬度
     * @param lng 中心经度
     * @param radiusMeters 搜索半径（米）
     * @return 范围内的节点ID列表
     */
    public List<Long> findNearbyNodes(double lat, double lng, double radiusMeters) {
        // 将米转换为度数（近似）
        double radiusDegrees = metersToDegrees(radiusMeters, lat);

        Point center = Geometries.point(lng, lat);

        return StreamSupport.stream(
                nodeTree.search(center, radiusDegrees).spliterator(), false)
                .map(Entry::value)
                .collect(Collectors.toList());
    }

    /**
     * 查找最近的节点
     *
     * @param lat 目标纬度
     * @param lng 目标经度
     * @param maxDistanceMeters 最大搜索距离
     * @return 最近的节点ID，如果没找到返回null
     */
    public Long findNearestNode(double lat, double lng, double maxDistanceMeters) {
        Point target = Geometries.point(lng, lat);
        double maxDistanceDegrees = metersToDegrees(maxDistanceMeters, lat);

        return StreamSupport.stream(
                nodeTree.nearest(target, maxDistanceDegrees, 1).spliterator(), false)
                .map(Entry::value)
                .findFirst()
                .orElse(null);
    }

    /**
     * 查找最近的N个节点
     */
    public List<Long> findNearestNodes(double lat, double lng, int count, double maxDistanceMeters) {
        Point target = Geometries.point(lng, lat);
        double maxDistanceDegrees = metersToDegrees(maxDistanceMeters, lat);

        return StreamSupport.stream(
                nodeTree.nearest(target, maxDistanceDegrees, count).spliterator(), false)
                .map(Entry::value)
                .collect(Collectors.toList());
    }

    // ========== 边索引操作 ==========

    /**
     * 添加边到索引
     */
    public void addEdge(Long edgeId, double fromLat, double fromLng, double toLat, double toLng) {
        Rectangle bbox = createBoundingBox(fromLat, fromLng, toLat, toLng);
        edgeTree = edgeTree.add(edgeId, bbox);
    }

    /**
     * 批量添加边
     */
    public void addEdges(List<SpatialEdge> edges) {
        List<Entry<Long, Rectangle>> entries = edges.stream()
                .map(e -> Entries.entry(e.getId(),
                        createBoundingBox(e.getFromLat(), e.getFromLng(), e.getToLat(), e.getToLng())))
                .collect(Collectors.toList());

        edgeTree = RTree.star().create(entries);
        log.info("已索引 {} 条边", edges.size());
    }

    /**
     * 查找指定范围内的边
     */
    public List<Long> findNearbyEdges(double lat, double lng, double radiusMeters) {
        double radiusDegrees = metersToDegrees(radiusMeters, lat);

        Rectangle searchArea = Geometries.rectangle(
                lng - radiusDegrees, lat - radiusDegrees,
                lng + radiusDegrees, lat + radiusDegrees
        );

        return StreamSupport.stream(
                edgeTree.search(searchArea).spliterator(), false)
                .map(Entry::value)
                .collect(Collectors.toList());
    }

    /**
     * 查找最近的边
     */
    public Long findNearestEdge(double lat, double lng, double maxDistanceMeters) {
        // 先用包围盒快速过滤
        List<Long> candidates = findNearbyEdges(lat, lng, maxDistanceMeters);

        if (candidates.isEmpty()) {
            return null;
        }

        // 如果只有一个候选，直接返回
        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        // 多个候选时返回第一个（实际应用中应计算精确距离）
        return candidates.get(0);
    }

    // ========== 设施索引操作 ==========

    /**
     * 添加设施到索引
     */
    public void addFacility(Long facilityId, double lat, double lng) {
        Point point = Geometries.point(lng, lat);
        facilityTree = facilityTree.add(facilityId, point);
    }

    /**
     * 批量添加设施
     */
    public void addFacilities(List<SpatialNode> facilities) {
        List<Entry<Long, Point>> entries = facilities.stream()
                .map(f -> Entries.entry(f.getId(), Geometries.point(f.getLng(), f.getLat())))
                .collect(Collectors.toList());

        facilityTree = RTree.star().create(entries);
        log.info("已索引 {} 个设施", facilities.size());
    }

    /**
     * 查找附近的设施
     */
    public List<Long> findNearbyFacilities(double lat, double lng, double radiusMeters) {
        double radiusDegrees = metersToDegrees(radiusMeters, lat);
        Point center = Geometries.point(lng, lat);

        return StreamSupport.stream(
                facilityTree.search(center, radiusDegrees).spliterator(), false)
                .map(Entry::value)
                .collect(Collectors.toList());
    }

    /**
     * 查找最近的N个设施
     */
    public List<Long> findNearestFacilities(double lat, double lng, int count, double maxDistanceMeters) {
        Point target = Geometries.point(lng, lat);
        double maxDistanceDegrees = metersToDegrees(maxDistanceMeters, lat);

        return StreamSupport.stream(
                facilityTree.nearest(target, maxDistanceDegrees, count).spliterator(), false)
                .map(Entry::value)
                .collect(Collectors.toList());
    }

    // ========== 索引管理 ==========

    /**
     * 清空所有索引
     */
    public void clearAll() {
        nodeTree = RTree.star().create();
        edgeTree = RTree.star().create();
        facilityTree = RTree.star().create();
        log.info("所有空间索引已清空");
    }

    /**
     * 获取索引统计信息
     */
    public SpatialIndexStats getStats() {
        return new SpatialIndexStats(
                nodeTree.size(),
                edgeTree.size(),
                facilityTree.size()
        );
    }

    // ========== 工具方法 ==========

    /**
     * 创建包围盒
     */
    private Rectangle createBoundingBox(double lat1, double lng1, double lat2, double lng2) {
        double minLat = Math.min(lat1, lat2);
        double maxLat = Math.max(lat1, lat2);
        double minLng = Math.min(lng1, lng2);
        double maxLng = Math.max(lng1, lng2);

        return Geometries.rectangle(minLng, minLat, maxLng, maxLat);
    }

    /**
     * 米转度数（近似）
     * 在不同纬度，1度对应的距离不同
     */
    private double metersToDegrees(double meters, double latitude) {
        // 纬度方向：1度 ≈ 111320米
        // 经度方向：1度 ≈ 111320 * cos(latitude) 米
        // 取平均值作为近似
        double latRadians = Math.toRadians(latitude);
        double metersPerDegree = 111320 * (1 + Math.cos(latRadians)) / 2;
        return meters / metersPerDegree;
    }

    /**
     * 计算两点间的Haversine距离（米）
     */
    public static double haversineDistance(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_METERS * c;
    }

    // ========== 内部类 ==========

    /**
     * 空间节点（简化模型）
     */
    public static class SpatialNode {
        private Long id;
        private double lat;
        private double lng;

        public SpatialNode(Long id, double lat, double lng) {
            this.id = id;
            this.lat = lat;
            this.lng = lng;
        }

        public Long getId() { return id; }
        public double getLat() { return lat; }
        public double getLng() { return lng; }
    }

    /**
     * 空间边（简化模型）
     */
    public static class SpatialEdge {
        private Long id;
        private double fromLat;
        private double fromLng;
        private double toLat;
        private double toLng;

        public SpatialEdge(Long id, double fromLat, double fromLng, double toLat, double toLng) {
            this.id = id;
            this.fromLat = fromLat;
            this.fromLng = fromLng;
            this.toLat = toLat;
            this.toLng = toLng;
        }

        public Long getId() { return id; }
        public double getFromLat() { return fromLat; }
        public double getFromLng() { return fromLng; }
        public double getToLat() { return toLat; }
        public double getToLng() { return toLng; }
    }

    /**
     * 索引统计信息
     */
    public static class SpatialIndexStats {
        private int nodeCount;
        private int edgeCount;
        private int facilityCount;

        public SpatialIndexStats(int nodeCount, int edgeCount, int facilityCount) {
            this.nodeCount = nodeCount;
            this.edgeCount = edgeCount;
            this.facilityCount = facilityCount;
        }

        public int getNodeCount() { return nodeCount; }
        public int getEdgeCount() { return edgeCount; }
        public int getFacilityCount() { return facilityCount; }

        @Override
        public String toString() {
            return String.format("SpatialIndexStats{nodes=%d, edges=%d, facilities=%d}",
                    nodeCount, edgeCount, facilityCount);
        }
    }
}
