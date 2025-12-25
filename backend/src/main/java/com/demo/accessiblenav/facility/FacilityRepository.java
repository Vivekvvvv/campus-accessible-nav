package com.demo.accessiblenav.facility;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FacilityRepository extends JpaRepository<FacilityEntity, Long> {

    List<FacilityEntity> findByFacilityType(FacilityType facilityType);

    List<FacilityEntity> findByBuildingName(String buildingName);

    List<FacilityEntity> findByIsOperationalTrue();

    @Query("SELECT f FROM FacilityEntity f WHERE f.facilityType = :type AND f.isOperational = true")
    List<FacilityEntity> findOperationalByType(@Param("type") FacilityType type);

    @Query("SELECT f FROM FacilityEntity f WHERE " +
           "f.lat BETWEEN :minLat AND :maxLat AND " +
           "f.lng BETWEEN :minLng AND :maxLng")
    List<FacilityEntity> findInBoundingBox(
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLng") double minLng,
            @Param("maxLng") double maxLng);

    @Query("SELECT f FROM FacilityEntity f WHERE " +
           "f.lat BETWEEN :minLat AND :maxLat AND " +
           "f.lng BETWEEN :minLng AND :maxLng AND " +
           "f.facilityType = :type")
    List<FacilityEntity> findInBoundingBoxByType(
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLng") double minLng,
            @Param("maxLng") double maxLng,
            @Param("type") FacilityType type);

    @Query("SELECT DISTINCT f.buildingName FROM FacilityEntity f WHERE f.buildingName IS NOT NULL ORDER BY f.buildingName")
    List<String> findAllBuildingNames();

    long countByFacilityType(FacilityType facilityType);

    long countByIsOperationalTrue();
}
