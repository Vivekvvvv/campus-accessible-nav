package com.demo.accessiblenav.exception;

/**
 * 业务错误码枚举
 * 统一管理所有业务错误码和错误消息
 */
public enum ErrorCode {

    // 路由相关错误 (ROUTE_xxx)
    ROUTE_NOT_FOUND("ROUTE_001", "未找到可行路线"),
    ROUTE_START_OUT_OF_BOUNDS("ROUTE_002", "起点超出校园范围"),
    ROUTE_END_OUT_OF_BOUNDS("ROUTE_003", "终点超出校园范围"),
    ROUTE_SNAP_FAILED("ROUTE_004", "无法将坐标吸附到路网"),
    ROUTE_CALCULATION_TIMEOUT("ROUTE_005", "路径计算超时"),

    // 图数据相关错误 (GRAPH_xxx)
    GRAPH_NOT_LOADED("GRAPH_001", "路网数据未加载"),
    GRAPH_IMPORT_FAILED("GRAPH_002", "路网导入失败"),
    GRAPH_VALIDATION_FAILED("GRAPH_003", "路网验证失败"),
    GRAPH_SNAPSHOT_NOT_FOUND("GRAPH_004", "路网快照不存在"),
    GRAPH_VERSION_CONFLICT("GRAPH_005", "路网版本冲突"),

    // 坐标相关错误 (COORD_xxx)
    INVALID_LATITUDE("COORD_001", "纬度值无效，范围应为 -90 到 90"),
    INVALID_LONGITUDE("COORD_002", "经度值无效，范围应为 -180 到 180"),
    INVALID_COORDINATES("COORD_003", "坐标超出有效范围"),

    // 障碍上报相关错误 (OBS_xxx)
    OBSTACLE_NOT_FOUND("OBS_001", "障碍上报记录不存在"),
    OBSTACLE_ALREADY_REPORTED("OBS_002", "该位置已有障碍上报"),
    OBSTACLE_ALREADY_REVIEWED("OBS_003", "该障碍已被审核"),
    OBSTACLE_EDGE_NOT_FOUND("OBS_004", "关联的路段不存在"),

    // 用户认证相关错误 (AUTH_xxx)
    USER_NOT_FOUND("AUTH_001", "用户不存在"),
    USER_ALREADY_EXISTS("AUTH_002", "用户名已存在"),
    INVALID_CREDENTIALS("AUTH_003", "用户名或密码错误"),
    TOKEN_EXPIRED("AUTH_004", "令牌已过期"),
    TOKEN_INVALID("AUTH_005", "令牌无效"),
    REFRESH_TOKEN_EXPIRED("AUTH_006", "刷新令牌已过期"),
    REFRESH_TOKEN_INVALID("AUTH_007", "刷新令牌无效"),
    UNAUTHORIZED("AUTH_008", "未授权访问"),
    FORBIDDEN("AUTH_009", "权限不足"),

    // 文件相关错误 (FILE_xxx)
    FILE_TYPE_NOT_ALLOWED("FILE_001", "不支持的文件类型"),
    FILE_TOO_LARGE("FILE_002", "文件大小超过限制"),
    FILE_UPLOAD_FAILED("FILE_003", "文件上传失败"),
    FILE_NOT_FOUND("FILE_004", "文件不存在"),

    // 图变更请求相关错误 (CHANGE_xxx)
    CHANGE_REQUEST_NOT_FOUND("CHANGE_001", "变更请求不存在"),
    CHANGE_REQUEST_ALREADY_PROCESSED("CHANGE_002", "变更请求已被处理"),
    CHANGE_REQUEST_INVALID_STATUS("CHANGE_003", "变更请求状态无效"),

    // 导航会话相关错误 (NAV_xxx)
    NAV_SESSION_NOT_FOUND("NAV_001", "导航会话不存在"),
    NAV_SESSION_NOT_ACTIVE("NAV_002", "导航会话未处于可操作状态"),
    NAV_INVALID_MODE("NAV_003", "导航模式无效"),

    // 通用错误 (GENERAL_xxx)
    INVALID_PARAMETER("GENERAL_001", "参数无效"),
    RESOURCE_NOT_FOUND("GENERAL_002", "资源不存在"),
    OPERATION_FAILED("GENERAL_003", "操作失败"),
    INTERNAL_ERROR("GENERAL_999", "系统内部错误");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s", code, message);
    }
}
