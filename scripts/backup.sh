#!/bin/bash
#
# 数据库备份脚本
# 用法: ./backup.sh [full|incremental] [备份目录]
#
# 环境变量:
#   DB_HOST     - 数据库主机 (默认: localhost)
#   DB_PORT     - 数据库端口 (默认: 5432)
#   DB_NAME     - 数据库名称 (默认: accessible_nav)
#   DB_URL      - JDBC URL, 例如 jdbc:postgresql://localhost:5432/accessible_nav?currentSchema=public
#   DB_USER     - 数据库用户 (默认: postgres)
#   DB_USERNAME - DB_USER 别名
#   DB_PASSWORD - 数据库密码（若设置则自动映射到 PGPASSWORD）
#   PGPASSWORD  - 数据库密码
#   BACKUP_RETENTION_DAYS - 备份保留天数 (默认: 30)

set -euo pipefail

# 配置
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-${POSTGRES_PORT:-5432}}"
DB_NAME="${DB_NAME:-${POSTGRES_DB:-accessible_nav}}"
DB_USER="${DB_USER:-${DB_USERNAME:-${SPRING_DATASOURCE_USERNAME:-${POSTGRES_USER:-postgres}}}}"
DB_PASSWORD="${DB_PASSWORD:-${SPRING_DATASOURCE_PASSWORD:-${POSTGRES_PASSWORD:-${PGPASSWORD:-}}}}"
BACKUP_RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-30}"

resolve_db_from_url() {
    local jdbc_url="${DB_URL:-${SPRING_DATASOURCE_URL:-}}"
    if [ -z "${jdbc_url}" ]; then
        return
    fi

    local raw="${jdbc_url#jdbc:postgresql://}"
    local no_query="${raw%%\?*}"
    local host_port="${no_query%%/*}"
    local db_name="${no_query#*/}"

    if [ -n "${host_port}" ] && [ "${host_port}" != "${no_query}" ]; then
        if [[ "${host_port}" == *:* ]]; then
            DB_HOST="${host_port%%:*}"
            DB_PORT="${host_port##*:}"
        else
            DB_HOST="${host_port}"
        fi
    fi

    if [ -n "${db_name}" ] && [ "${db_name}" != "${no_query}" ]; then
        DB_NAME="${db_name}"
    fi
}

resolve_db_from_url

if [ -z "${PGPASSWORD:-}" ] && [ -n "${DB_PASSWORD}" ]; then
    export PGPASSWORD="${DB_PASSWORD}"
fi

# 参数处理
BACKUP_TYPE="${1:-full}"
BACKUP_BASE_DIR="${2:-./backups}"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="${BACKUP_BASE_DIR}/${TIMESTAMP}"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() {
    echo -e "${GREEN}[INFO]${NC} $(date '+%Y-%m-%d %H:%M:%S') $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $(date '+%Y-%m-%d %H:%M:%S') $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $(date '+%Y-%m-%d %H:%M:%S') $1"
}

# 检查依赖
check_dependencies() {
    if ! command -v pg_dump &> /dev/null; then
        log_error "pg_dump 命令未找到，请安装 PostgreSQL 客户端工具"
        exit 1
    fi
}

# 创建备份目录
create_backup_dir() {
    mkdir -p "${BACKUP_DIR}"
    log_info "创建备份目录: ${BACKUP_DIR}"
}

# 全量备份
full_backup() {
    log_info "开始全量备份..."

    # Custom format dump (pg_restore compatible). We gzip it for easier storage/transfer.
    local backup_file="${BACKUP_DIR}/${DB_NAME}_full_${TIMESTAMP}.dump.gz"

    pg_dump \
        -h "${DB_HOST}" \
        -p "${DB_PORT}" \
        -U "${DB_USER}" \
        -d "${DB_NAME}" \
        -Fc \
        -v \
        --no-owner \
        --no-privileges \
        | gzip > "${backup_file}"

    local backup_size=$(du -h "${backup_file}" | cut -f1)
    log_info "全量备份完成: ${backup_file} (${backup_size})"

    # 计算校验和
    sha256sum "${backup_file}" > "${backup_file}.sha256"
    log_info "已生成校验和文件"

    echo "${backup_file}"
}

# 清理旧备份
cleanup_old_backups() {
    log_info "清理超过 ${BACKUP_RETENTION_DAYS} 天的旧备份..."

    local deleted_count=0
    while IFS= read -r -d '' backup_dir; do
        rm -rf "${backup_dir}"
        deleted_count=$((deleted_count + 1))
        log_info "已删除: ${backup_dir}"
    done < <(find "${BACKUP_BASE_DIR}" -maxdepth 1 -type d -mtime +${BACKUP_RETENTION_DAYS} -print0 2>/dev/null || true)

    if [ "${deleted_count}" -gt 0 ]; then
        log_info "共清理 ${deleted_count} 个旧备份"
    else
        log_info "没有需要清理的旧备份"
    fi
}

# 备份验证
verify_backup() {
    local backup_file="$1"

    log_info "验证备份文件完整性..."

    # 验证校验和
    if [ -f "${backup_file}.sha256" ]; then
        if sha256sum -c "${backup_file}.sha256" > /dev/null 2>&1; then
            log_info "校验和验证通过"
        else
            log_error "校验和验证失败!"
            return 1
        fi
    fi

    # 验证文件可解压
    if gzip -t "${backup_file}" 2>/dev/null; then
        log_info "文件完整性验证通过"
    else
        log_error "备份文件已损坏!"
        return 1
    fi

    return 0
}

# 记录备份元数据
write_metadata() {
    local backup_file="$1"
    local metadata_file="${BACKUP_DIR}/metadata.json"

    cat > "${metadata_file}" << EOF
{
    "timestamp": "${TIMESTAMP}",
    "type": "${BACKUP_TYPE}",
    "database": "${DB_NAME}",
    "host": "${DB_HOST}",
    "backup_file": "$(basename ${backup_file})",
    "size_bytes": $(stat -f%z "${backup_file}" 2>/dev/null || stat -c%s "${backup_file}"),
    "checksum_file": "$(basename ${backup_file}).sha256"
}
EOF

    log_info "已写入备份元数据: ${metadata_file}"
}

# 主函数
main() {
    log_info "=========================================="
    log_info "开始数据库备份"
    log_info "备份类型: ${BACKUP_TYPE}"
    log_info "目标数据库: ${DB_NAME}@${DB_HOST}:${DB_PORT}"
    log_info "=========================================="

    check_dependencies
    create_backup_dir

    case "${BACKUP_TYPE}" in
        full)
            backup_file=$(full_backup)
            ;;
        *)
            log_error "不支持的备份类型: ${BACKUP_TYPE}"
            log_info "支持的类型: full"
            exit 1
            ;;
    esac

    verify_backup "${backup_file}"
    write_metadata "${backup_file}"
    cleanup_old_backups

    log_info "=========================================="
    log_info "备份完成!"
    log_info "备份目录: ${BACKUP_DIR}"
    log_info "=========================================="
}

main "$@"
