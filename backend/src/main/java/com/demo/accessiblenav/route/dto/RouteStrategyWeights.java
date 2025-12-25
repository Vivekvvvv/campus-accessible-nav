package com.demo.accessiblenav.route.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

@Schema(name = "RouteStrategyWeights", description = "Fine-grained routing penalties")
public class RouteStrategyWeights {

    @Schema(description = "Penalty multiplier for stair segments", example = "0.8")
    @DecimalMin(value = "0.0", message = "stairsPenalty must be >= 0")
    @DecimalMax(value = "3.0", message = "stairsPenalty must be <= 3")
    private Double stairsPenalty;

    @Schema(description = "Penalty multiplier for steep-slope segments", example = "0.6")
    @DecimalMin(value = "0.0", message = "slopePenalty must be >= 0")
    @DecimalMax(value = "1.0", message = "slopePenalty must be <= 1")
    private Double slopePenalty;

    @Schema(description = "Penalty multiplier for construction/inaccessible segments", example = "0.5")
    @DecimalMin(value = "0.0", message = "constructionPenalty must be >= 0")
    @DecimalMax(value = "3.0", message = "constructionPenalty must be <= 3")
    private Double constructionPenalty;

    public RouteStrategyWeights() {
    }

    public RouteStrategyWeights(Double stairsPenalty, Double slopePenalty, Double constructionPenalty) {
        this.stairsPenalty = stairsPenalty;
        this.slopePenalty = slopePenalty;
        this.constructionPenalty = constructionPenalty;
    }

    public Double getStairsPenalty() {
        return stairsPenalty;
    }

    public void setStairsPenalty(Double stairsPenalty) {
        this.stairsPenalty = stairsPenalty;
    }

    public Double getSlopePenalty() {
        return slopePenalty;
    }

    public void setSlopePenalty(Double slopePenalty) {
        this.slopePenalty = slopePenalty;
    }

    public Double getConstructionPenalty() {
        return constructionPenalty;
    }

    public void setConstructionPenalty(Double constructionPenalty) {
        this.constructionPenalty = constructionPenalty;
    }
}
