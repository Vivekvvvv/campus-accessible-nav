package com.demo.accessiblenav.events;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
@Tag(name = "实时事件", description = "服务端推送事件流（SSE）")
public class EventsController {

    private final EventStreamService eventStreamService;

    public EventsController(EventStreamService eventStreamService) {
        this.eventStreamService = eventStreamService;
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "订阅实时事件",
            description = "通过Server-Sent Events订阅实时事件流，包括障碍物更新、图数据变更等通知"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "成功建立SSE连接")
    })
    public SseEmitter events() {
        return eventStreamService.subscribe();
    }
}
