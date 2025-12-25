package com.demo.accessiblenav.favorites;

import com.demo.accessiblenav.common.ApiResponse;
import com.demo.accessiblenav.favorites.dto.FavoritePlaceDto;
import com.demo.accessiblenav.favorites.dto.FavoritePlaceUpsertRequest;
import com.demo.accessiblenav.favorites.dto.QuickRouteDto;
import com.demo.accessiblenav.favorites.dto.QuickRouteUpsertRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/favorites")
public class FavoritesController {

    private final FavoritesService favoritesService;

    public FavoritesController(FavoritesService favoritesService) {
        this.favoritesService = favoritesService;
    }

    @GetMapping("/places")
    public ResponseEntity<ApiResponse<List<FavoritePlaceDto>>> listPlaces(Principal principal) {
        List<FavoritePlaceDto> list = favoritesService.listPlaces(requireUserId(principal));
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @PostMapping("/places")
    public ResponseEntity<ApiResponse<FavoritePlaceDto>> createPlace(Principal principal,
                                                                     @Valid @RequestBody FavoritePlaceUpsertRequest request) {
        FavoritePlaceDto dto = favoritesService.createPlace(requireUserId(principal), request);
        return ResponseEntity.ok(ApiResponse.success("favorite place created", dto));
    }

    @PutMapping("/places/{id}")
    public ResponseEntity<ApiResponse<FavoritePlaceDto>> updatePlace(Principal principal,
                                                                     @PathVariable("id") Long id,
                                                                     @Valid @RequestBody FavoritePlaceUpsertRequest request) {
        FavoritePlaceDto dto = favoritesService.updatePlace(requireUserId(principal), id, request);
        return ResponseEntity.ok(ApiResponse.success("favorite place updated", dto));
    }

    @DeleteMapping("/places/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePlace(Principal principal,
                                                         @PathVariable("id") Long id) {
        favoritesService.deletePlace(requireUserId(principal), id);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @GetMapping("/quick-routes")
    public ResponseEntity<ApiResponse<List<QuickRouteDto>>> listQuickRoutes(Principal principal) {
        List<QuickRouteDto> list = favoritesService.listQuickRoutes(requireUserId(principal));
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @PostMapping("/quick-routes")
    public ResponseEntity<ApiResponse<QuickRouteDto>> createQuickRoute(Principal principal,
                                                                       @Valid @RequestBody QuickRouteUpsertRequest request) {
        QuickRouteDto dto = favoritesService.createQuickRoute(requireUserId(principal), request);
        return ResponseEntity.ok(ApiResponse.success("quick route created", dto));
    }

    @PutMapping("/quick-routes/{id}")
    public ResponseEntity<ApiResponse<QuickRouteDto>> updateQuickRoute(Principal principal,
                                                                       @PathVariable("id") Long id,
                                                                       @Valid @RequestBody QuickRouteUpsertRequest request) {
        QuickRouteDto dto = favoritesService.updateQuickRoute(requireUserId(principal), id, request);
        return ResponseEntity.ok(ApiResponse.success("quick route updated", dto));
    }

    @DeleteMapping("/quick-routes/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteQuickRoute(Principal principal,
                                                              @PathVariable("id") Long id) {
        favoritesService.deleteQuickRoute(requireUserId(principal), id);
        return ResponseEntity.ok(ApiResponse.success());
    }

    private static String requireUserId(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().trim().isEmpty()) {
            throw new IllegalStateException("missing authenticated user");
        }
        return principal.getName();
    }
}
