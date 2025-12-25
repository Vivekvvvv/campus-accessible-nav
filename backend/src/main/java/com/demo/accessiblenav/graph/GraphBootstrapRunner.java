package com.demo.accessiblenav.graph;

import com.demo.accessiblenav.graph.dto.GraphImportRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class GraphBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GraphBootstrapRunner.class);

    private final NodeRepository nodeRepository;
    private final EdgeRepository edgeRepository;
    private final GraphImportService graphImportService;
    private final ObjectMapper objectMapper;
    private final String graphImportPath;
    private final boolean forceImport;
    private final GraphBootstrapState bootstrapState;

    public GraphBootstrapRunner(NodeRepository nodeRepository,
                               EdgeRepository edgeRepository,
                               GraphImportService graphImportService,
                               ObjectMapper objectMapper,
                               GraphBootstrapState bootstrapState,
                               org.springframework.core.env.Environment env) {
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
        this.graphImportService = graphImportService;
        this.objectMapper = objectMapper;
        this.graphImportPath = env.getProperty("app.bootstrap.graph-import-path", "");
        this.forceImport = Boolean.parseBoolean(env.getProperty("app.bootstrap.graph-import-force", "false"));
        this.bootstrapState = bootstrapState;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String pathValue = graphImportPath == null ? "" : graphImportPath.trim();
        if (pathValue.isEmpty()) {
            bootstrapState.markReady();
            return;
        }

        long nodeCount = nodeRepository.count();
        long edgeCount = edgeRepository.count();
        if (nodeCount > 0 && edgeCount > 0 && !forceImport) {
            log.info("Graph bootstrap skipped: existing data detected (nodes={}, edges={})", nodeCount, edgeCount);
            bootstrapState.markReady();
            return;
        }

        Path path = Paths.get(pathValue);
        if (!path.isAbsolute()) {
            path = Paths.get(System.getProperty("user.dir")).resolve(path).normalize();
        }

        if (!Files.exists(path)) {
            log.warn("Graph bootstrap import path not found: {}", path);
            bootstrapState.markReady();
            return;
        }

        if (forceImport && nodeCount > 0 && edgeCount > 0) {
            log.warn("Graph bootstrap force enabled: replacing existing graph (nodes={}, edges={})", nodeCount, edgeCount);
        } else {
            log.info("Graph bootstrap importing from {} (existing nodes={}, edges={})", path, nodeCount, edgeCount);
        }
        try (InputStream in = Files.newInputStream(path)) {
            GraphImportRequest req = objectMapper.readValue(in, GraphImportRequest.class);
            graphImportService.importGraphForBootstrap(req, forceImport);
        }

        log.info("Graph bootstrap import done (nodes={}, edges={})", nodeRepository.count(), edgeRepository.count());
        bootstrapState.markReady();
    }
}
