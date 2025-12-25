package com.demo.accessiblenav.tiles;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/tiles")
@Tag(name = "地图瓦片", description = "地图瓦片代理服务")
public class TileProxyController {

    private static final List<String> VECTOR_HOSTS = Arrays.asList(
            "https://webrd01.is.autonavi.com",
            "https://webrd02.is.autonavi.com",
            "https://webrd03.is.autonavi.com",
            "https://webrd04.is.autonavi.com"
    );

    private static final List<String> SAT_HOSTS = Arrays.asList(
            "https://webst01.is.autonavi.com",
            "https://webst02.is.autonavi.com",
            "https://webst03.is.autonavi.com",
            "https://webst04.is.autonavi.com"
    );

    private static final Set<Integer> ALLOWED_STYLES = new HashSet<>(Arrays.asList(6, 7, 8));

    private final RestTemplate restTemplate;

    public TileProxyController(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(4))
                .setReadTimeout(Duration.ofSeconds(8))
                .build();
    }

    @GetMapping(value = "/gaode", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(
            summary = "获取高德地图瓦片",
            description = "代理请求高德地图瓦片，支持矢量地图和卫星图"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "获取成功"),
            @ApiResponse(responseCode = "400", description = "参数无效"),
            @ApiResponse(responseCode = "502", description = "上游服务不可用")
    })
    public ResponseEntity<byte[]> proxyGaodeTile(
            @Parameter(description = "瓦片X坐标", required = true)
            @RequestParam("x") int x,
            @Parameter(description = "瓦片Y坐标", required = true)
            @RequestParam("y") int y,
            @Parameter(description = "缩放级别(0-20)", required = true)
            @RequestParam("z") int z,
            @Parameter(description = "地图样式(6=卫星图, 7=矢量图, 8=卫星路网)")
            @RequestParam(name = "style", defaultValue = "7") int style,
            @Parameter(description = "瓦片缩放比例(1或2)")
            @RequestParam(name = "scale", defaultValue = "1") int scale,
            @Parameter(description = "瓦片尺寸(1或2)")
            @RequestParam(name = "size", defaultValue = "1") int size,
            @Parameter(description = "语言(zh_cn/zh_en/en)")
            @RequestParam(name = "lang", defaultValue = "zh_cn") String lang
    ) {
        if (x < 0 || y < 0 || z < 0 || z > 20) {
            return ResponseEntity.badRequest().build();
        }
        if (!ALLOWED_STYLES.contains(style)) {
            return ResponseEntity.badRequest().build();
        }
        if (scale != 1 && scale != 2) {
            scale = 1;
        }
        if (size != 1 && size != 2) {
            size = 1;
        }
        if (!isAllowedLang(lang)) {
            lang = "zh_cn";
        }

        String host = pickHost(style == 7 ? VECTOR_HOSTS : SAT_HOSTS);

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(host + "/appmaptile")
                .queryParam("style", style)
                .queryParam("x", x)
                .queryParam("y", y)
                .queryParam("z", z)
                .queryParam("scale", scale)
                .queryParam("size", size);

        if (style != 6) {
            builder.queryParam("lang", lang);
        }

        ResponseEntity<byte[]> response = restTemplate.getForEntity(builder.toUriString(), byte[].class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }

        MediaType contentType = response.getHeaders().getContentType();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(contentType != null ? contentType : MediaType.IMAGE_PNG);
        headers.setCacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic());

        return new ResponseEntity<>(response.getBody(), headers, HttpStatus.OK);
    }

    private String pickHost(List<String> hosts) {
        int idx = ThreadLocalRandom.current().nextInt(hosts.size());
        return hosts.get(idx);
    }

    private boolean isAllowedLang(String lang) {
        if (!StringUtils.hasText(lang)) {
            return false;
        }
        String normalized = lang.trim().toLowerCase();
        return "zh_cn".equals(normalized) || "zh_en".equals(normalized) || "en".equals(normalized);
    }
}
