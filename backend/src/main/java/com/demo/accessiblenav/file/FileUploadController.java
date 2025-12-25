package com.demo.accessiblenav.file;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/files")
@Tag(name = "文件上传", description = "图片文件上传服务")
public class FileUploadController {

    // 从配置文件读取上传目录，默认为 ./uploads
    @Value("${file.upload.dir:./uploads}")
    private String uploadDir;

    // 从配置文件读取访问 URL 前缀，默认为 /uploads
    @Value("${file.upload.url-prefix:/uploads}")
    private String urlPrefix;

    // 允许的图片类型
    private static final Set<String> ALLOWED_IMAGE_TYPES = new HashSet<>(Arrays.asList(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp"
    ));

    // 最大文件大小：5MB
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    /**
     * 上传单张照片
     */
    @PostMapping("/upload")
    @Operation(
            summary = "上传单个文件",
            description = "上传单张图片文件，支持JPG、PNG、GIF、WEBP格式，最大5MB"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "上传成功，返回文件URL"),
            @ApiResponse(responseCode = "400", description = "文件格式或大小不符合要求")
    })
    public ResponseEntity<?> uploadFile(
            @Parameter(description = "图片文件", required = true)
            @RequestParam("file") MultipartFile file) {
        try {
            // 验证文件
            String error = validateFile(file);
            if (error != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error);
            }

            // 保存文件
            String savedFileName = saveFile(file);

            // 返回可访问的 URL
            String fileUrl = urlPrefix + "/" + savedFileName;

            Map<String, Object> result = new HashMap<>();
            result.put("url", fileUrl);
            result.put("filename", savedFileName);
            result.put("size", file.getSize());

            return ResponseEntity.ok(result);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Upload failed: " + e.getMessage(), e);
        }
    }

    /**
     * 批量上传照片
     */
    @PostMapping("/upload-multiple")
    @Operation(
            summary = "批量上传文件",
            description = "批量上传图片文件，最多5张，每张最大5MB"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "上传结果，包含成功和失败的文件信息"),
            @ApiResponse(responseCode = "400", description = "文件数量超限或未选择文件")
    })
    public ResponseEntity<?> uploadMultipleFiles(
            @Parameter(description = "图片文件数组", required = true)
            @RequestParam("files") MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No files selected");
        }

        if (files.length > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Too many files (max 5)");
        }

        List<Map<String, Object>> uploadedFiles = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (MultipartFile file : files) {
            try {
                String error = validateFile(file);
                if (error != null) {
                    errors.add(file.getOriginalFilename() + ": " + error);
                    continue;
                }

                String savedFileName = saveFile(file);
                String fileUrl = urlPrefix + "/" + savedFileName;

                Map<String, Object> fileInfo = new HashMap<>();
                fileInfo.put("url", fileUrl);
                fileInfo.put("filename", savedFileName);
                fileInfo.put("size", file.getSize());
                uploadedFiles.add(fileInfo);

            } catch (Exception e) {
                errors.add(file.getOriginalFilename() + ": " + e.getMessage());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("uploaded", uploadedFiles);
        if (!errors.isEmpty()) {
            result.put("errors", errors);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 验证文件
     */
    private String validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            return "File is empty";
        }

        // 检查文件大小
        if (file.getSize() > MAX_FILE_SIZE) {
            return "File size exceeds 5MB limit";
        }

        // 检查文件类型
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            return "Only image files allowed (JPG, PNG, GIF, WEBP)";
        }

        // 检查文件扩展名
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            return "Invalid filename";
        }

        String ext = getFileExtension(originalFilename).toLowerCase();
        if (!Arrays.asList("jpg", "jpeg", "png", "gif", "webp").contains(ext)) {
            return "Unsupported file extension";
        }

        return null;
    }

    /**
     * 保存文件到磁盘
     */
    private String saveFile(MultipartFile file) throws IOException {
        // 确保上传目录存在
        File dir = new File(uploadDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // 生成唯一文件名：时间戳 + UUID + 原扩展名
        String originalFilename = file.getOriginalFilename();
        String ext = getFileExtension(originalFilename);
        String timestamp = String.valueOf(Instant.now().toEpochMilli());
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String savedFileName = timestamp + "_" + uuid + "." + ext;

        // 保存文件
        Path filePath = Paths.get(uploadDir, savedFileName);
        Files.copy(file.getInputStream(), filePath);

        return savedFileName;
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < filename.length() - 1) {
            return filename.substring(dotIndex + 1);
        }
        return "";
    }
}
