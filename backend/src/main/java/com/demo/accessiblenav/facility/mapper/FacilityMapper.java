package com.demo.accessiblenav.facility.mapper;

import com.demo.accessiblenav.facility.FacilityEntity;
import com.demo.accessiblenav.facility.FacilityFeatureEntity;
import com.demo.accessiblenav.facility.dto.FacilityDto;
import org.mapstruct.*;

import java.util.List;

/**
 * Facility对象映射器
 * 使用MapStruct实现Entity与DTO之间的转换
 */
@Mapper(componentModel = "spring")
public interface FacilityMapper {

    /**
     * Entity转DTO
     */
    @Mapping(target = "facilityTypeName", expression = "java(entity.getFacilityType() != null ? entity.getFacilityType().getDisplayName() : null)")
    @Mapping(target = "distanceMeters", ignore = true)
    FacilityDto toDto(FacilityEntity entity);

    /**
     * Entity列表转DTO列表
     */
    List<FacilityDto> toDtoList(List<FacilityEntity> entities);

    /**
     * Feature Entity转Feature DTO
     */
    FacilityDto.FeatureDto featureToDto(FacilityFeatureEntity feature);

    /**
     * Feature DTO转Feature Entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "facility", ignore = true)
    FacilityFeatureEntity featureToEntity(FacilityDto.FeatureDto dto);

    /**
     * 更新Entity（忽略null值）
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "features", ignore = true)
    @Mapping(target = "verifiedBy", ignore = true)
    void updateEntityFromDto(FacilityDto dto, @MappingTarget FacilityEntity entity);
}
