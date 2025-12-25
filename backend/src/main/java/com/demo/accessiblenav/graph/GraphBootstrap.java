package com.demo.accessiblenav.graph;

import com.demo.accessiblenav.graph.dto.GraphImportRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class GraphBootstrap implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(GraphBootstrap.class);

    @Value("${app.graph.seed-path:data/gbuc-jianggao/graph-import.json}")
    private String seedPath;

    private final NodeRepository nodeRepository;
    private final EdgeRepository edgeRepository;
    private final GraphImportService graphImportService;
    private final ObjectMapper objectMapper;

    public GraphBootstrap(NodeRepository nodeRepository,
                          EdgeRepository edgeRepository,
                          GraphImportService graphImportService,
                          ObjectMapper objectMapper) {
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
        this.graphImportService = graphImportService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) {
        String raw = seedPath == null ? "" : seedPath.trim();
        // Allow disabling seed in test/IT/prod by setting app.graph.seed-path=disabled (or empty).
        if (raw.isEmpty() || "disabled".equalsIgnoreCase(raw)) {
            return;
        }
        if (nodeRepository.count() > 0 || edgeRepository.count() > 0) {
            return;
        }

        Path resolved = resolvePath(raw);
        if (resolved == null) {
            logger.info("Graph seed file not found: {}", raw);
            return;
        }

        try (InputStream input = Files.newInputStream(resolved)) {
            GraphImportRequest req = objectMapper.readValue(input, GraphImportRequest.class);
            graphImportService.importGraphForBootstrap(req, false);
            logger.info("Seeded graph from {}", resolved.toAbsolutePath());
        } catch (Exception e) {
            logger.warn("Failed to seed graph from {}: {}", resolved, e.getMessage());
        }
    }

    private Path resolvePath(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        Path path = Paths.get(raw);
        if (Files.isRegularFile(path)) {
            return path;
        }
        Path cwd = Paths.get("").toAbsolutePath();
        Path candidate = cwd.resolve(raw);
        if (Files.isRegularFile(candidate)) {
            return candidate;
        }
        Path parent = cwd.getParent();
        if (parent != null) {
            Path parentCandidate = parent.resolve(raw);
            if (Files.isRegularFile(parentCandidate)) {
                return parentCandidate;
            }
        }
        return null;
    }
}
