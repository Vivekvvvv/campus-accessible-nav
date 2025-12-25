package com.demo.accessiblenav.route.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public class RouteResponse {

    private String mode;
    private double distanceM;
    private long durationSec;
    private int riskCount;
    private List<Instruction> instructions;
    private List<LngLat> path;
    private Debug debug;
    private List<String> explain;
    private SnapInfo startSnap;
    private SnapInfo endSnap;
    private List<LevelPoint> pathWithLevel;
    private List<LevelTransition> levelTransitions;
    private ModeDiff modeDiff;
    @Schema(description = "Effective routing policy after applying request/profile/default rules")
    private RoutingPolicy routingPolicy;

    public RouteResponse() {
    }

    public RouteResponse(String mode,
                         double distanceM,
                         long durationSec,
                         int riskCount,
                         List<Instruction> instructions,
                         List<LngLat> path,
                         Debug debug) {
        this(mode, distanceM, durationSec, riskCount, instructions, path, debug, null, null, null, null, null, null, null);
    }

    public RouteResponse(String mode,
                         double distanceM,
                         long durationSec,
                         int riskCount,
                         List<Instruction> instructions,
                         List<LngLat> path,
                         Debug debug,
                         List<String> explain) {
        this(mode, distanceM, durationSec, riskCount, instructions, path, debug, explain, null, null, null, null, null, null);
    }

    public RouteResponse(String mode,
                         double distanceM,
                         long durationSec,
                         int riskCount,
                         List<Instruction> instructions,
                         List<LngLat> path,
                         Debug debug,
                         List<String> explain,
                         SnapInfo startSnap,
                         SnapInfo endSnap,
                         List<LevelPoint> pathWithLevel,
                         List<LevelTransition> levelTransitions,
                         ModeDiff modeDiff,
                         RoutingPolicy routingPolicy) {
        this.mode = mode;
        this.distanceM = distanceM;
        this.durationSec = durationSec;
        this.riskCount = riskCount;
        this.instructions = instructions;
        this.path = path;
        this.debug = debug;
        this.explain = explain;
        this.startSnap = startSnap;
        this.endSnap = endSnap;
        this.pathWithLevel = pathWithLevel;
        this.levelTransitions = levelTransitions;
        this.modeDiff = modeDiff;
        this.routingPolicy = routingPolicy;
    }

    public String getMode() {
        return mode;
    }

    public double getDistanceM() {
        return distanceM;
    }

    public long getDurationSec() {
        return durationSec;
    }

    public int getRiskCount() {
        return riskCount;
    }

    public List<Instruction> getInstructions() {
        return instructions;
    }

    public List<LngLat> getPath() {
        return path;
    }

    public Debug getDebug() {
        return debug;
    }

    public List<String> getExplain() {
        return explain;
    }

    public SnapInfo getStartSnap() {
        return startSnap;
    }

    public SnapInfo getEndSnap() {
        return endSnap;
    }

    public List<LevelPoint> getPathWithLevel() {
        return pathWithLevel;
    }

    public List<LevelTransition> getLevelTransitions() {
        return levelTransitions;
    }

    public ModeDiff getModeDiff() {
        return modeDiff;
    }

    public RoutingPolicy getRoutingPolicy() {
        return routingPolicy;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public void setDistanceM(double distanceM) {
        this.distanceM = distanceM;
    }

    public void setDurationSec(long durationSec) {
        this.durationSec = durationSec;
    }

    public void setRiskCount(int riskCount) {
        this.riskCount = riskCount;
    }

    public void setInstructions(List<Instruction> instructions) {
        this.instructions = instructions;
    }

    public void setPath(List<LngLat> path) {
        this.path = path;
    }

    public void setDebug(Debug debug) {
        this.debug = debug;
    }

    public void setExplain(List<String> explain) {
        this.explain = explain;
    }

    public void setStartSnap(SnapInfo startSnap) {
        this.startSnap = startSnap;
    }

    public void setEndSnap(SnapInfo endSnap) {
        this.endSnap = endSnap;
    }

    public void setPathWithLevel(List<LevelPoint> pathWithLevel) {
        this.pathWithLevel = pathWithLevel;
    }

    public void setLevelTransitions(List<LevelTransition> levelTransitions) {
        this.levelTransitions = levelTransitions;
    }

    public void setModeDiff(ModeDiff modeDiff) {
        this.modeDiff = modeDiff;
    }

    public void setRoutingPolicy(RoutingPolicy routingPolicy) {
        this.routingPolicy = routingPolicy;
    }

    public static class Instruction {
        private String action;
        private String text;
        private double distanceM;

        public Instruction(String action, String text, double distanceM) {
            this.action = action;
            this.text = text;
            this.distanceM = distanceM;
        }

        public String getAction() {
            return action;
        }

        public String getText() {
            return text;
        }

        public double getDistanceM() {
            return distanceM;
        }
    }

    public static class LngLat {
        private double lng;
        private double lat;

        public LngLat(double lng, double lat) {
            this.lng = lng;
            this.lat = lat;
        }

        public double getLng() {
            return lng;
        }

        public double getLat() {
            return lat;
        }
    }

    public static class LevelPoint {
        private double lng;
        private double lat;
        private int level;

        public LevelPoint(double lng, double lat, int level) {
            this.lng = lng;
            this.lat = lat;
            this.level = level;
        }

        public double getLng() {
            return lng;
        }

        public double getLat() {
            return lat;
        }

        public int getLevel() {
            return level;
        }
    }

    public static class LevelTransition {
        private int fromLevel;
        private int toLevel;
        private double lng;
        private double lat;
        private String via;

        public LevelTransition(int fromLevel, int toLevel, double lng, double lat, String via) {
            this.fromLevel = fromLevel;
            this.toLevel = toLevel;
            this.lng = lng;
            this.lat = lat;
            this.via = via;
        }

        public int getFromLevel() {
            return fromLevel;
        }

        public int getToLevel() {
            return toLevel;
        }

        public double getLng() {
            return lng;
        }

        public double getLat() {
            return lat;
        }

        public String getVia() {
            return via;
        }
    }

    public static class SnapInfo {
        private String type;
        private double distanceM;
        private double lng;
        private double lat;
        private Integer level;
        private Long edgeId;
        private Long nodeId;
        private Long fromNodeId;
        private Long toNodeId;

        public SnapInfo(String type,
                        double distanceM,
                        double lng,
                        double lat,
                        Integer level,
                        Long edgeId,
                        Long nodeId,
                        Long fromNodeId,
                        Long toNodeId) {
            this.type = type;
            this.distanceM = distanceM;
            this.lng = lng;
            this.lat = lat;
            this.level = level;
            this.edgeId = edgeId;
            this.nodeId = nodeId;
            this.fromNodeId = fromNodeId;
            this.toNodeId = toNodeId;
        }

        public String getType() {
            return type;
        }

        public double getDistanceM() {
            return distanceM;
        }

        public double getLng() {
            return lng;
        }

        public double getLat() {
            return lat;
        }

        public Integer getLevel() {
            return level;
        }

        public Long getEdgeId() {
            return edgeId;
        }

        public Long getNodeId() {
            return nodeId;
        }

        public Long getFromNodeId() {
            return fromNodeId;
        }

        public Long getToNodeId() {
            return toNodeId;
        }
    }

    public static class ModeDiff {
        private String compareMode;
        private double compareDistanceM;
        private long compareDurationSec;
        private List<String> notes;

        public ModeDiff(String compareMode,
                        double compareDistanceM,
                        long compareDurationSec,
                        List<String> notes) {
            this.compareMode = compareMode;
            this.compareDistanceM = compareDistanceM;
            this.compareDurationSec = compareDurationSec;
            this.notes = notes;
        }

        public String getCompareMode() {
            return compareMode;
        }

        public double getCompareDistanceM() {
            return compareDistanceM;
        }

        public long getCompareDurationSec() {
            return compareDurationSec;
        }

        public List<String> getNotes() {
            return notes;
        }
    }

    public static class RoutingPolicy {
        @Schema(description = "Whether this request applied user accessibility profile defaults", example = "true")
        private boolean profileApplied;
        @Schema(description = "Effective route strategy", example = "SAFEST")
        private String strategy;
        @Schema(description = "Effective slope penalty weight", example = "0.8")
        private double slopeWeight;
        @Schema(description = "Strategy source: REQUEST/PROFILE/DEFAULT", example = "PROFILE")
        private String strategySource;
        @Schema(description = "Slope weight source: REQUEST/PROFILE/DEFAULT", example = "PROFILE")
        private String slopeWeightSource;
        @Schema(description = "User profile mobility mode when available", example = "WHEELCHAIR")
        private String profileMobilityMode;
        @Schema(description = "Fine-grained route strategy weights")
        private RouteStrategyWeights strategyWeights;
        @Schema(description = "Whether passability dynamic penalty is enabled", example = "true")
        private boolean passabilityPenaltyEnabled;
        @Schema(description = "Effective minimum clamp for passability", example = "0.05")
        private double passabilityMinClamp;
        @Schema(description = "Effective passability dynamic weight factor", example = "1.0")
        private double passabilityWeightFactor;
        @Schema(description = "Passability policy source: DEFAULT/TENANT_POLICY", example = "TENANT_POLICY")
        private String passabilityPolicySource;

        public RoutingPolicy(boolean profileApplied,
                             String strategy,
                             double slopeWeight,
                             String strategySource,
                             String slopeWeightSource,
                             String profileMobilityMode,
                             RouteStrategyWeights strategyWeights,
                             boolean passabilityPenaltyEnabled,
                             double passabilityMinClamp,
                             double passabilityWeightFactor,
                             String passabilityPolicySource) {
            this.profileApplied = profileApplied;
            this.strategy = strategy;
            this.slopeWeight = slopeWeight;
            this.strategySource = strategySource;
            this.slopeWeightSource = slopeWeightSource;
            this.profileMobilityMode = profileMobilityMode;
            this.strategyWeights = strategyWeights;
            this.passabilityPenaltyEnabled = passabilityPenaltyEnabled;
            this.passabilityMinClamp = passabilityMinClamp;
            this.passabilityWeightFactor = passabilityWeightFactor;
            this.passabilityPolicySource = passabilityPolicySource;
        }

        public boolean isProfileApplied() {
            return profileApplied;
        }

        public String getStrategy() {
            return strategy;
        }

        public double getSlopeWeight() {
            return slopeWeight;
        }

        public String getStrategySource() {
            return strategySource;
        }

        public String getSlopeWeightSource() {
            return slopeWeightSource;
        }

        public String getProfileMobilityMode() {
            return profileMobilityMode;
        }

        public RouteStrategyWeights getStrategyWeights() {
            return strategyWeights;
        }

        public boolean isPassabilityPenaltyEnabled() {
            return passabilityPenaltyEnabled;
        }

        public double getPassabilityMinClamp() {
            return passabilityMinClamp;
        }

        public double getPassabilityWeightFactor() {
            return passabilityWeightFactor;
        }

        public String getPassabilityPolicySource() {
            return passabilityPolicySource;
        }
    }

    public static class Debug {
        private long startNodeId;
        private long endNodeId;
        private int visited;
        private int relaxations;

        public Debug(long startNodeId, long endNodeId, int visited, int relaxations) {
            this.startNodeId = startNodeId;
            this.endNodeId = endNodeId;
            this.visited = visited;
            this.relaxations = relaxations;
        }

        public long getStartNodeId() {
            return startNodeId;
        }

        public long getEndNodeId() {
            return endNodeId;
        }

        public int getVisited() {
            return visited;
        }

        public int getRelaxations() {
            return relaxations;
        }
    }
}
