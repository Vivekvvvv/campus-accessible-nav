package com.demo.accessiblenav.graph;

import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/buildings")
public class BuildingController {

    private final BuildingRepository buildingRepository;

    public BuildingController(BuildingRepository buildingRepository) {
        this.buildingRepository = buildingRepository;
    }

    @GetMapping
    public List<BuildingEntity> listBuildings() {
        return buildingRepository.findAllByOrderByNameAsc();
    }

    @GetMapping("/{id}")
    public BuildingEntity getBuilding(@PathVariable("id") Long id) {
        return buildingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("building not found"));
    }

    @GetMapping("/{id}/floors")
    public Map<String, Object> getFloors(@PathVariable("id") Long id) {
        BuildingEntity building = buildingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("building not found"));
        Map<String, Object> result = new HashMap<>();
        result.put("buildingId", building.getId());
        result.put("name", building.getName());
        result.put("minLevel", building.getMinLevel());
        result.put("maxLevel", building.getMaxLevel());
        List<Integer> floors = new ArrayList<>();
        for (int i = building.getMinLevel(); i <= building.getMaxLevel(); i++) {
            floors.add(i);
        }
        result.put("floors", floors);
        return result;
    }
}
