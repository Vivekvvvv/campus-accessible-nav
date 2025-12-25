package com.demo.accessiblenav.facility;

import com.demo.accessiblenav.exception.ResourceNotFoundException;
import com.demo.accessiblenav.facility.dto.FacilityDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 无障碍设施服务
 */
@Service
@Transactional(readOnly = true)
public class FacilityService {

    private final FacilityRepository facilityRepository;

    public FacilityService(FacilityRepository facilityRepository) {
        this.facilityRepository = facilityRepository;
    }

    /**
     * 查找附近的设施
     */
    public List<FacilityDto> findNearby(double lat, double lng, int radiusMeters, FacilityType type) {
        // 将米转换为度（近似值）
        double latDelta = radiusMeters / 111320.0;
        double lngDelta = radiusMeters / (111320.0 * Math.cos(Math.toRadians(lat)));

        double minLat = lat - latDelta;
        double maxLat = lat + latDelta;
        double minLng = lng - lngDelta;
        double maxLng = lng + lngDelta;

        List<FacilityEntity> facilities;
        if (type != null) {
            facilities = facilityRepository.findInBoundingBoxByType(minLat, maxLat, minLng, maxLng, type);
        } else {
            facilities = facilityRepository.findInBoundingBox(minLat, maxLat, minLng, maxLng);
        }

        // 计算实际距离并过滤
        return facilities.stream()
                .map(f -> {
                    FacilityDto dto = toDto(f);
                    double distance = calculateDistance(lat, lng, f.getLat(), f.getLng());
                    dto.setDistanceMeters(distance);
                    return dto;
                })
                .filter(dto -> dto.getDistanceMeters() <= radiusMeters)
                .sorted(Comparator.comparingDouble(FacilityDto::getDistanceMeters))
                .collect(Collectors.toList());
    }

    /**
     * 根据建筑名称查找设施
     */
    public List<FacilityDto> findByBuilding(String buildingName) {
        return facilityRepository.findByBuildingName(buildingName)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * 根据类型查找设施
     */
    public List<FacilityDto> findByType(FacilityType type) {
        return facilityRepository.findByFacilityType(type)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * 获取单个设施详情
     */
    public FacilityDto getById(Long id) {
        FacilityEntity entity = facilityRepository.findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new ResourceNotFoundException("Facility", id));
        return toDto(entity);
    }

    /**
     * 获取所有建筑名称列表
     */
    public List<String> getAllBuildingNames() {
        return facilityRepository.findAllBuildingNames();
    }

    /**
     * 获取设施统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalCount", facilityRepository.count());
        stats.put("operationalCount", facilityRepository.countByIsOperationalTrue());

        Map<String, Long> byType = new LinkedHashMap<>();
        for (FacilityType type : FacilityType.values()) {
            byType.put(type.name(), facilityRepository.countByFacilityType(type));
        }
        stats.put("byType", byType);

        return stats;
    }

    /**
     * 获取所有设施类型
     */
    public List<Map<String, String>> getAllFacilityTypes() {
        return Arrays.stream(FacilityType.values())
                .map(type -> {
                    Map<String, String> map = new LinkedHashMap<>();
                    map.put("type", type.name());
                    map.put("displayName", type.getDisplayName());
                    return map;
                })
                .collect(Collectors.toList());
    }

    // === 私有方法 ===

    private FacilityDto toDto(FacilityEntity entity) {
        FacilityDto dto = new FacilityDto();
        dto.setId(entity.getId());
        dto.setFacilityType(entity.getFacilityType());
        dto.setFacilityTypeName(entity.getFacilityType().getDisplayName());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setBuildingName(entity.getBuildingName());
        dto.setFloorLevel(entity.getFloorLevel());
        dto.setLat(entity.getLat());
        dto.setLng(entity.getLng());
        dto.setOpeningHours(entity.getOpeningHours());
        dto.setIsOperational(entity.getIsOperational());
        dto.setLastVerifiedAt(entity.getLastVerifiedAt());
        dto.setPhotoUrl(entity.getPhotoUrl());
        dto.setContactInfo(entity.getContactInfo());
        dto.setNotes(entity.getNotes());

        if (entity.getFeatures() != null) {
            dto.setFeatures(entity.getFeatures().stream()
                    .map(f -> {
                        FacilityDto.FeatureDto featureDto = new FacilityDto.FeatureDto();
                        featureDto.setFeatureType(f.getFeatureType());
                        featureDto.setFeatureValue(f.getFeatureValue());
                        featureDto.setNotes(f.getNotes());
                        return featureDto;
                    })
                    .collect(Collectors.toList()));
        }

        return dto;
    }

    /**
     * 使用 Haversine 公式计算两点间的距离（米）
     */
    private double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371000; // 地球半径（米）
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
