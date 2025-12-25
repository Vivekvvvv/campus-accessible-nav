package com.demo.accessiblenav.favorites;

import com.demo.accessiblenav.favorites.dto.FavoritePlaceDto;
import com.demo.accessiblenav.favorites.dto.FavoritePlaceUpsertRequest;
import com.demo.accessiblenav.favorites.dto.QuickRouteDto;
import com.demo.accessiblenav.favorites.dto.QuickRouteUpsertRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class FavoritesService {

    private final FavoriteGroupRepository favoriteGroupRepository;
    private final FavoritePlaceRepository favoritePlaceRepository;
    private final QuickRouteRepository quickRouteRepository;

    public FavoritesService(FavoriteGroupRepository favoriteGroupRepository,
                            FavoritePlaceRepository favoritePlaceRepository,
                            QuickRouteRepository quickRouteRepository) {
        this.favoriteGroupRepository = favoriteGroupRepository;
        this.favoritePlaceRepository = favoritePlaceRepository;
        this.quickRouteRepository = quickRouteRepository;
    }

    @Transactional(readOnly = true)
    public List<FavoritePlaceDto> listPlaces(String userId) {
        return favoritePlaceRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toPlaceDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public FavoritePlaceDto createPlace(String userId, FavoritePlaceUpsertRequest req) {
        FavoritePlaceEntity place = new FavoritePlaceEntity();
        place.setUserId(userId);
        applyPlace(place, req, userId);
        return toPlaceDto(Objects.requireNonNull(favoritePlaceRepository.save(place)));
    }

    @Transactional
    public FavoritePlaceDto updatePlace(String userId, Long id, FavoritePlaceUpsertRequest req) {
        FavoritePlaceEntity place = favoritePlaceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("favorite place not found"));
        requireOwned(userId, place.getUserId());
        applyPlace(place, req, userId);
        return toPlaceDto(Objects.requireNonNull(favoritePlaceRepository.save(place)));
    }

    @Transactional
    public void deletePlace(String userId, Long id) {
        FavoritePlaceEntity place = favoritePlaceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("favorite place not found"));
        requireOwned(userId, place.getUserId());
        favoritePlaceRepository.delete(place);
    }

    @Transactional(readOnly = true)
    public List<QuickRouteDto> listQuickRoutes(String userId) {
        return quickRouteRepository.findByUserIdOrderByUpdatedAtDesc(userId)
                .stream()
                .map(this::toQuickRouteDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public QuickRouteDto createQuickRoute(String userId, QuickRouteUpsertRequest req) {
        QuickRouteEntity quickRoute = new QuickRouteEntity();
        quickRoute.setUserId(userId);
        applyQuickRoute(quickRoute, req, userId);
        return toQuickRouteDto(Objects.requireNonNull(quickRouteRepository.save(quickRoute)));
    }

    @Transactional
    public QuickRouteDto updateQuickRoute(String userId, Long id, QuickRouteUpsertRequest req) {
        QuickRouteEntity quickRoute = quickRouteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("quick route not found"));
        requireOwned(userId, quickRoute.getUserId());
        applyQuickRoute(quickRoute, req, userId);
        return toQuickRouteDto(Objects.requireNonNull(quickRouteRepository.save(quickRoute)));
    }

    @Transactional
    public void deleteQuickRoute(String userId, Long id) {
        QuickRouteEntity quickRoute = quickRouteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("quick route not found"));
        requireOwned(userId, quickRoute.getUserId());
        quickRouteRepository.delete(quickRoute);
    }

    private void applyPlace(FavoritePlaceEntity place, FavoritePlaceUpsertRequest req, String userId) {
        place.setName(req.getName().trim());
        place.setLat(req.getLat());
        place.setLng(req.getLng());
        place.setTags(joinTags(req.getTags()));

        if (req.getGroupId() == null) {
            place.setGroup(null);
            return;
        }

        FavoriteGroupEntity group = favoriteGroupRepository.findById(req.getGroupId())
                .orElseThrow(() -> new IllegalArgumentException("favorite group not found"));
        requireOwned(userId, group.getUserId());
        place.setGroup(group);
    }

    private void applyQuickRoute(QuickRouteEntity entity, QuickRouteUpsertRequest req, String userId) {
        FavoritePlaceEntity start = favoritePlaceRepository.findById(req.getStartPlaceId())
                .orElseThrow(() -> new IllegalArgumentException("start place not found"));
        FavoritePlaceEntity end = favoritePlaceRepository.findById(req.getEndPlaceId())
                .orElseThrow(() -> new IllegalArgumentException("end place not found"));

        requireOwned(userId, start.getUserId());
        requireOwned(userId, end.getUserId());

        entity.setName(req.getName().trim());
        entity.setStartPlace(start);
        entity.setEndPlace(end);
        entity.setTravelMode(normalizeMode(req.getTravelMode()));
    }

    private String normalizeMode(String modeRaw) {
        if (modeRaw == null || modeRaw.trim().isEmpty()) {
            return "WALK";
        }
        String mode = modeRaw.trim().toUpperCase(Locale.ROOT);
        if (!"WALK".equals(mode) && !"WHEELCHAIR".equals(mode)) {
            throw new IllegalArgumentException("travelMode must be WALK/WHEELCHAIR");
        }
        return mode;
    }

    private static void requireOwned(String userId, String ownerUserId) {
        if (userId == null || ownerUserId == null || !ownerUserId.equals(userId)) {
            throw new IllegalArgumentException("resource not owned by current user");
        }
    }

    private FavoritePlaceDto toPlaceDto(FavoritePlaceEntity entity) {
        FavoritePlaceDto dto = new FavoritePlaceDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setLat(entity.getLat());
        dto.setLng(entity.getLng());
        dto.setTags(splitTags(entity.getTags()));
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        if (entity.getGroup() != null) {
            dto.setGroupId(entity.getGroup().getId());
            dto.setGroupName(entity.getGroup().getName());
        }
        return dto;
    }

    private QuickRouteDto toQuickRouteDto(QuickRouteEntity entity) {
        QuickRouteDto dto = new QuickRouteDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setTravelMode(entity.getTravelMode());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setStartPlaceId(entity.getStartPlace() == null ? null : entity.getStartPlace().getId());
        dto.setEndPlaceId(entity.getEndPlace() == null ? null : entity.getEndPlace().getId());
        return dto;
    }

    private String joinTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        return tags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(x -> !x.isEmpty())
                .distinct()
                .collect(Collectors.joining(","));
    }

    private List<String> splitTags(String tags) {
        if (tags == null || tags.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(x -> !x.isEmpty())
                .collect(Collectors.toList());
    }
}
