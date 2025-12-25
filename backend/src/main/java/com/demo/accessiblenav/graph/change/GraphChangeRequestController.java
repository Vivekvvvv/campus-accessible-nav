package com.demo.accessiblenav.graph.change;

import com.demo.accessiblenav.graph.dto.GraphChangeRequestCreateRequest;
import com.demo.accessiblenav.graph.dto.GraphChangeRequestDetailDto;
import com.demo.accessiblenav.graph.dto.GraphChangeRequestDto;
import com.demo.accessiblenav.graph.dto.GraphChangeRequestReviewRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/admin/graph/changes")
@Validated
public class GraphChangeRequestController {

    private final GraphChangeRequestService changeService;

    public GraphChangeRequestController(GraphChangeRequestService changeService) {
        this.changeService = changeService;
    }

    @PostMapping
    public GraphChangeRequestDto submit(@RequestBody @Valid GraphChangeRequestCreateRequest req) {
        return changeService.submit(req);
    }

    @GetMapping
    public List<GraphChangeRequestDto> list(@RequestParam(value = "status", required = false) String status,
                                            @RequestParam(value = "kind", required = false) String kind) {
        return changeService.list(status, kind);
    }

    @GetMapping("/{id}")
    public GraphChangeRequestDetailDto detail(@PathVariable("id") Long id) {
        return changeService.detail(id);
    }

    @PostMapping("/{id}/review")
    public GraphChangeRequestDto startReview(@PathVariable("id") Long id,
                                             @RequestBody(required = false) @Valid GraphChangeRequestReviewRequest req) {
        return changeService.startReview(id, req);
    }

    @PostMapping("/{id}/approve")
    public GraphChangeRequestDto approve(@PathVariable("id") Long id,
                                         @RequestBody(required = false) @Valid GraphChangeRequestReviewRequest req) {
        return changeService.approve(id, req);
    }

    @PostMapping("/{id}/reject")
    public GraphChangeRequestDto reject(@PathVariable("id") Long id,
                                        @RequestBody(required = false) @Valid GraphChangeRequestReviewRequest req) {
        return changeService.reject(id, req);
    }
}
