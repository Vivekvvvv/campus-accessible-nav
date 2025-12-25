package com.demo.accessiblenav.route;

import com.demo.accessiblenav.profile.AccessibilityProfileEntity;
import com.demo.accessiblenav.profile.AccessibilityProfileRepository;
import com.demo.accessiblenav.route.dto.RouteRequest;
import com.demo.accessiblenav.route.dto.RouteStrategy;
import com.demo.accessiblenav.route.dto.RouteStrategyWeights;
import com.demo.accessiblenav.route.dto.TravelMode;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

@Component
public class RoutePreferencePolicyResolver {

    private final AccessibilityProfileRepository accessibilityProfileRepository;

    public RoutePreferencePolicyResolver(AccessibilityProfileRepository accessibilityProfileRepository) {
        this.accessibilityProfileRepository = accessibilityProfileRepository;
    }

    public ResolvedRoutingPolicy resolve(RouteRequest request, Authentication auth) {
        String userId = resolveUserId(auth);
        Optional<AccessibilityProfileEntity> profile = Optional.empty();
        if (userId != null) {
            profile = accessibilityProfileRepository.findByUserId(userId);
        }

        RouteStrategy strategy = request.getStrategy();
        Double slopeWeight = request.getSlopeWeight();
        RouteStrategyWeights weights = request.getStrategyWeights();

        String strategySource = "REQUEST";
        String slopeWeightSource = "REQUEST";

        if (strategy == null) {
            if (profile.isPresent()) {
                strategy = chooseStrategy(profile.get(), request.getMode());
                strategySource = "PROFILE";
            } else {
                strategy = RouteStrategy.BALANCED;
                strategySource = "DEFAULT";
            }
        }

        if (slopeWeight == null) {
            if (profile.isPresent()) {
                slopeWeight = chooseSlopeWeight(profile.get(), request.getMode(), strategy);
                slopeWeightSource = "PROFILE";
            } else {
                slopeWeight = defaultSlopeWeight(strategy, request.getMode());
                slopeWeightSource = "DEFAULT";
            }
        }

        if (weights == null) {
            if (profile.isPresent()) {
                weights = chooseStrategyWeights(profile.get(), request.getMode(), strategy, slopeWeight);
            } else {
                weights = defaultStrategyWeights(strategy, request.getMode(), slopeWeight);
            }
        } else {
            weights = sanitizeStrategyWeights(weights, strategy, request.getMode(), slopeWeight);
        }

        double effectiveSlope = clampSlopeWeight(slopeWeight);
        String mobilityMode = profile.map(AccessibilityProfileEntity::getMobilityMode).orElse(null);

        return new ResolvedRoutingPolicy(
                profile.isPresent(),
                strategy,
                effectiveSlope,
                strategySource,
                slopeWeightSource,
                mobilityMode,
                weights
        );
    }

    private static String resolveUserId(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        String name = auth.getName();
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        if ("anonymousUser".equalsIgnoreCase(name.trim())) {
            return null;
        }
        return name.trim();
    }

    private static RouteStrategy chooseStrategy(AccessibilityProfileEntity profile, TravelMode mode) {
        boolean avoidStairs = Boolean.TRUE.equals(profile.getAvoidStairs());
        boolean avoidSlope = Boolean.TRUE.equals(profile.getAvoidSlope());
        boolean avoidConstruction = Boolean.TRUE.equals(profile.getAvoidConstruction());

        String mobilityMode = String.valueOf(profile.getMobilityMode()).toUpperCase(Locale.ROOT);
        boolean accessibilityFirstMode = "WHEELCHAIR".equals(mobilityMode)
                || "VISUAL_IMPAIRMENT".equals(mobilityMode)
                || "STROLLER".equals(mobilityMode);

        if (avoidStairs || avoidSlope || avoidConstruction || accessibilityFirstMode || mode == TravelMode.WHEELCHAIR) {
            return RouteStrategy.SAFEST;
        }
        return RouteStrategy.BALANCED;
    }

    private static double chooseSlopeWeight(AccessibilityProfileEntity profile, TravelMode mode, RouteStrategy strategy) {
        if (strategy == RouteStrategy.SHORTEST) {
            return 0.0;
        }

        boolean avoidSlope = Boolean.TRUE.equals(profile.getAvoidSlope());
        double maxSlopePercent = profile.getMaxSlopePercent() == null ? 12.0 : profile.getMaxSlopePercent();
        if (avoidSlope) {
            if (maxSlopePercent <= 6.0) {
                return 0.95;
            }
            if (maxSlopePercent <= 8.0) {
                return 0.85;
            }
            return mode == TravelMode.WHEELCHAIR ? 0.8 : 0.7;
        }

        if (maxSlopePercent <= 6.0) {
            return 0.75;
        }
        if (maxSlopePercent <= 8.0) {
            return 0.6;
        }
        if (maxSlopePercent <= 12.0) {
            return 0.4;
        }
        return defaultSlopeWeight(strategy, mode);
    }

    private static double defaultSlopeWeight(RouteStrategy strategy, TravelMode mode) {
        if (strategy == RouteStrategy.SHORTEST) {
            return 0.0;
        }
        if (strategy == RouteStrategy.SAFEST) {
            return mode == TravelMode.WHEELCHAIR ? 0.8 : 0.6;
        }
        return 0.2;
    }

    private static double clampSlopeWeight(Double value) {
        double v = value == null ? 0.2 : value;
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }

    private static RouteStrategyWeights chooseStrategyWeights(AccessibilityProfileEntity profile,
                                                              TravelMode mode,
                                                              RouteStrategy strategy,
                                                              Double slopeWeight) {
        RouteStrategyWeights defaults = defaultStrategyWeights(strategy, mode, slopeWeight);
        if (profile == null) {
            return defaults;
        }

        Double stairsPenalty = defaults.getStairsPenalty();
        if (Boolean.TRUE.equals(profile.getAvoidStairs())) {
            stairsPenalty = Math.max(stairsPenalty, 1.2);
        }

        Double constructionPenalty = defaults.getConstructionPenalty();
        if (Boolean.TRUE.equals(profile.getAvoidConstruction())) {
            constructionPenalty = Math.max(constructionPenalty, 1.0);
        }

        return sanitizeStrategyWeights(new RouteStrategyWeights(
                stairsPenalty,
                defaults.getSlopePenalty(),
                constructionPenalty
        ), strategy, mode, slopeWeight);
    }

    private static RouteStrategyWeights defaultStrategyWeights(RouteStrategy strategy,
                                                               TravelMode mode,
                                                               Double slopeWeight) {
        double slopePenalty = clampSlopeWeight(slopeWeight);
        double stairsPenalty;
        double constructionPenalty;

        if (strategy == RouteStrategy.SHORTEST) {
            stairsPenalty = 0.0;
            constructionPenalty = 0.0;
            slopePenalty = 0.0;
        } else if (strategy == RouteStrategy.SAFEST) {
            stairsPenalty = mode == TravelMode.WHEELCHAIR ? 1.6 : 1.1;
            constructionPenalty = 1.0;
        } else {
            stairsPenalty = mode == TravelMode.WHEELCHAIR ? 0.8 : 0.3;
            constructionPenalty = 0.3;
        }

        return sanitizeStrategyWeights(
                new RouteStrategyWeights(stairsPenalty, slopePenalty, constructionPenalty),
                strategy,
                mode,
                slopeWeight
        );
    }

    private static RouteStrategyWeights sanitizeStrategyWeights(RouteStrategyWeights weights,
                                                                RouteStrategy strategy,
                                                                TravelMode mode,
                                                                Double slopeWeight) {
        double fallbackSlope = clampSlopeWeight(slopeWeight);
        RouteStrategyWeights defaults = defaultStrategyWeightsNoRecursion(strategy, mode, fallbackSlope);
        if (weights == null) {
            return defaults;
        }

        double stairs = clamp(weights.getStairsPenalty(), 0.0, 3.0, defaults.getStairsPenalty());
        double slope = clamp(weights.getSlopePenalty(), 0.0, 1.0, defaults.getSlopePenalty());
        double construction = clamp(weights.getConstructionPenalty(), 0.0, 3.0, defaults.getConstructionPenalty());

        return new RouteStrategyWeights(stairs, slope, construction);
    }

    private static RouteStrategyWeights defaultStrategyWeightsNoRecursion(RouteStrategy strategy,
                                                                          TravelMode mode,
                                                                          Double slopeWeight) {
        double slopePenalty = clampSlopeWeight(slopeWeight);
        if (strategy == RouteStrategy.SHORTEST) {
            return new RouteStrategyWeights(0.0, 0.0, 0.0);
        }
        if (strategy == RouteStrategy.SAFEST) {
            return new RouteStrategyWeights(mode == TravelMode.WHEELCHAIR ? 1.6 : 1.1, slopePenalty, 1.0);
        }
        return new RouteStrategyWeights(mode == TravelMode.WHEELCHAIR ? 0.8 : 0.3, slopePenalty, 0.3);
    }

    private static double clamp(Double value, double min, double max, double fallback) {
        double v = value == null ? fallback : value;
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    public static final class ResolvedRoutingPolicy {
        private final boolean profileApplied;
        private final RouteStrategy strategy;
        private final double slopeWeight;
        private final String strategySource;
        private final String slopeWeightSource;
        private final String profileMobilityMode;
        private final RouteStrategyWeights strategyWeights;

        public ResolvedRoutingPolicy(boolean profileApplied,
                                     RouteStrategy strategy,
                                     double slopeWeight,
                                     String strategySource,
                                     String slopeWeightSource,
                                     String profileMobilityMode,
                                     RouteStrategyWeights strategyWeights) {
            this.profileApplied = profileApplied;
            this.strategy = strategy;
            this.slopeWeight = slopeWeight;
            this.strategySource = strategySource;
            this.slopeWeightSource = slopeWeightSource;
            this.profileMobilityMode = profileMobilityMode;
            this.strategyWeights = strategyWeights;
        }

        public boolean isProfileApplied() {
            return profileApplied;
        }

        public RouteStrategy getStrategy() {
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
    }
}
