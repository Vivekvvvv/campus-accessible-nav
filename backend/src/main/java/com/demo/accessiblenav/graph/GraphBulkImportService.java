package com.demo.accessiblenav.graph;

import com.demo.accessiblenav.graph.change.GraphChangeKind;
import com.demo.accessiblenav.graph.change.GraphChangePayloadType;
import com.demo.accessiblenav.graph.change.GraphChangeRequestService;
import com.demo.accessiblenav.graph.dto.GraphChangePayload;
import com.demo.accessiblenav.graph.dto.GraphChangeRequestCreateRequest;
import com.demo.accessiblenav.graph.dto.GraphChangeRequestDto;
import com.demo.accessiblenav.graph.dto.GraphImportRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class GraphBulkImportService {

    private final ObjectMapper objectMapper;
    private final GraphChangeRequestService changeRequestService;

    public GraphBulkImportService(ObjectMapper objectMapper, GraphChangeRequestService changeRequestService) {
        this.objectMapper = objectMapper;
        this.changeRequestService = changeRequestService;
    }

    public GraphChangeRequestDto bulkImport(MultipartFile[] files, GraphChangeKind kind, GraphChangePayloadType payloadType, String note) {
        GraphImportRequest request = buildImportRequest(files);
        GraphChangePayload payload = new GraphChangePayload();
        payload.setNodes(request.getNodes());
        payload.setEdges(request.getEdges());
        GraphChangeRequestCreateRequest create = new GraphChangeRequestCreateRequest();
        create.setKind(kind);
        create.setPayloadType(payloadType);
        create.setNote(note);
        create.setPayload(payload);
        return changeRequestService.submit(create);
    }

    public GraphImportRequest buildImportRequest(MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no files uploaded");
        }
        GraphImportPayloadBuilder builder = new GraphImportPayloadBuilder();
        int parsedCount = 0;
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            GraphImportRequest parsed = parseFile(file);
            builder.merge(parsed);
            parsedCount++;
        }
        if (parsedCount == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no valid files found");
        }
        GraphImportRequest request = builder.build();
        if (request.getNodes() == null || request.getNodes().isEmpty()
                || request.getEdges() == null || request.getEdges().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no valid graph data found");
        }
        return request;
    }

    private GraphImportRequest parseFile(MultipartFile file) {
        try {
            String originalFilename = file.getOriginalFilename();
            String filename = originalFilename == null ? "" : originalFilename.toLowerCase();
            if (filename.endsWith(".geojson") || filename.endsWith(".json")) {
                JsonNode root = objectMapper.readTree(file.getInputStream());
                if (root.has("nodes") && root.has("edges")) {
                    return objectMapper.treeToValue(root, GraphImportRequest.class);
                }
                GraphImportPayloadBuilder builder = new GraphImportPayloadBuilder();
                builder.addGeoJson(root);
                return builder.build();
            }
            if (filename.endsWith(".csv")) {
                return parseCsv(file);
            }
            if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
                return parseExcel(file);
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported file type: " + filename);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file parse failed: " + e.getMessage(), e);
        }
    }

    private GraphImportRequest parseCsv(MultipartFile file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            List<double[]> coords = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("[,;\\t]");
                if (parts.length < 2) {
                    continue;
                }
                Double lat = safeParse(parts[0]);
                Double lng = safeParse(parts[1]);
                if (lat == null || lng == null) {
                    continue;
                }
                coords.add(new double[]{lng, lat});
            }
            GraphImportPayloadBuilder builder = new GraphImportPayloadBuilder();
            builder.addPath(coords);
            return builder.build();
        }
    }

    private GraphImportRequest parseExcel(MultipartFile file) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            List<double[]> coords = new ArrayList<>();
            int latCol = -1;
            int lngCol = -1;
            for (Row row : sheet) {
                if (row.getRowNum() == 0 && (latCol == -1 || lngCol == -1)) {
                    for (int i = 0; i < row.getLastCellNum(); i++) {
                        String header = row.getCell(i) == null ? null : row.getCell(i).toString().toLowerCase();
                        if (header != null) {
                            if (header.contains("lat")) {
                                latCol = i;
                            }
                            if (header.contains("lon") || header.contains("lng")) {
                                lngCol = i;
                            }
                        }
                    }
                    if (latCol != -1 && lngCol != -1) {
                        continue;
                    }
                }
                if (latCol == -1 || lngCol == -1) {
                    continue;
                }
                Double lat = safeParse(row.getCell(latCol));
                Double lng = safeParse(row.getCell(lngCol));
                if (lat == null || lng == null) {
                    continue;
                }
                coords.add(new double[]{lng, lat});
            }
            GraphImportPayloadBuilder builder = new GraphImportPayloadBuilder();
            builder.addPath(coords);
            return builder.build();
        }
    }

    private Double safeParse(String text) {
        if (text == null) {
            return null;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double safeParse(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null) {
            return null;
        }
        try {
            return cell.getNumericCellValue();
        } catch (Exception e) {
            try {
                return Double.parseDouble(cell.toString().trim());
            } catch (Exception ex) {
                return null;
            }
        }
    }
}
