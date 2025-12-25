package com.demo.accessiblenav.client;

import com.demo.accessiblenav.audit.OperationLog;
import com.demo.accessiblenav.audit.OperationLogRepository;
import com.demo.accessiblenav.client.dto.ClientErrorRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.time.Instant;

@RestController
@RequestMapping("/api/client")
@Tag(name = "客户端错误", description = "前端错误上报接口")
public class ClientErrorController {

    private final OperationLogRepository repository;

    public ClientErrorController(OperationLogRepository repository) {
        this.repository = repository;
    }

    @PostMapping("/error")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "上报客户端错误",
            description = "前端应用上报JavaScript错误或异常信息用于监控和排查"
    )
    @ApiResponse(responseCode = "204", description = "上报成功")
    public void report(@RequestBody @Valid ClientErrorRequest req) {
        OperationLog log = new OperationLog();
        log.setActor("client");
        log.setActorRole("CLIENT");
        log.setAction("CLIENT_" + safeUpper(req.getType(), 24));

        String detail = "msg=" + safe(req.getMessage(), 300)
                + "; url=" + safe(req.getUrl(), 140)
                + "; meta=" + safe(req.getMeta(), 140);
        log.setDetail(safe(detail, 512));
        log.setCreatedAt(Instant.now());
        repository.save(log);
    }

    private String safe(String v, int max) {
        if (v == null) return "";
        String s = v.trim();
        if (s.length() <= max) return s;
        return s.substring(0, max);
    }

    private String safeUpper(String v, int max) {
        if (v == null) return "ERROR";
        String s = v.trim().toUpperCase();
        if (s.isEmpty()) return "ERROR";
        if (s.length() <= max) return s;
        return s.substring(0, max);
    }
}
