package com.demo.accessiblenav.navigation;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertThrows;

class NavigationWebSocketControllerTest {

    @Test
    void startNavigationShouldRejectAnonymousPrincipal() {
        NavigationSessionService sessionService = new NavigationSessionService(null, null, null);
        NavigationWebSocketController controller = new NavigationWebSocketController(sessionService);
        NavigationWebSocketController.NavigationStartRequest request =
                new NavigationWebSocketController.NavigationStartRequest();
        request.setStartLat(23.27);
        request.setStartLng(113.20);
        request.setEndLat(23.28);
        request.setEndLng(113.21);
        request.setDestinationName("library");
        request.setMode("WALK");

        assertThrows(AccessDeniedException.class, () -> controller.startNavigation(request, null));
    }
}
