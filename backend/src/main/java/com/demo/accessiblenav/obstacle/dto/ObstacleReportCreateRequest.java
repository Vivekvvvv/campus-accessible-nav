package com.demo.accessiblenav.obstacle.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public class ObstacleReportCreateRequest {

    // 若前端无法获取 edgeId，可传 null，由后端根据 submitterLat/Lng 查找最近边
    private Long edgeId;

    @NotBlank(message = "障碍类型不能为空")
    @Size(max = 32, message = "障碍类型长度不能超过 32 个字符")
    private String type;

    @Size(max = 512, message = "描述长度不能超过 512 个字符")
    private String reason;

    // 照片 URL 列表（支持多张照片）
    @Size(max = 10, message = "照片数量不能超过 10 张")
    private List<String> photoUrls;

    // 提交者位置
    @DecimalMin(value = "-90.0", message = "纬度值无效")
    @DecimalMax(value = "90.0", message = "纬度值无效")
    private Double submitterLat;

    @DecimalMin(value = "-180.0", message = "经度值无效")
    @DecimalMax(value = "180.0", message = "经度值无效")
    private Double submitterLng;

    // 提交者信息（可选）
    @Size(max = 64, message = "提交者名称长度不能超过 64 个字符")
    private String submitterName;

    public Long getEdgeId() {
        return edgeId;
    }

    public void setEdgeId(Long edgeId) {
        this.edgeId = edgeId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public List<String> getPhotoUrls() {
        return photoUrls;
    }

    public void setPhotoUrls(List<String> photoUrls) {
        this.photoUrls = photoUrls;
    }

    public Double getSubmitterLat() {
        return submitterLat;
    }

    public void setSubmitterLat(Double submitterLat) {
        this.submitterLat = submitterLat;
    }

    public Double getSubmitterLng() {
        return submitterLng;
    }

    public void setSubmitterLng(Double submitterLng) {
        this.submitterLng = submitterLng;
    }

    public String getSubmitterName() {
        return submitterName;
    }

    public void setSubmitterName(String submitterName) {
        this.submitterName = submitterName;
    }
}
