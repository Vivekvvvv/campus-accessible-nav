#!/bin/bash
#
# 数据库恢复脚本
# 用法: ./restore.sh <备份文件或备份目录>
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

set -euo pipefail

# 配置
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-${POSTGRES_PORT:-5432}}"
DB_NAME="${DB_NAME:-${POSTGRES_DB:-accessible_nav}}"
DB_USER="${DB_USER:-${DB_USERNAME:-${SPRING_DATASOURCE_USERNAME:-${POSTGRES_USER:-postgres}}}}"
DB_PASSWORD="${DB_PASSWORD:-${SPRING_DATASOURCE_PASSWORD:-${POSTGRES_PASSWORD:-${PGPASSWORD:-}}}}"

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

# 显示用法
usage() {
    echo "用法: $0 <备份文件或备份目录>"
    echo ""
    echo "示例:"
    echo "  $0 ./backups/20240101_120000"
    echo "  $0 ./backups/20240101_120000/accessible_nav_full_20240101_120000.sql.gz"
    echo ""
    echo "环境变量:"
    echo "  DB_HOST     - 数据库主机 (默认: localhost)"
    echo "  DB_PORT     - 数据库端口 (默认: 5432)"
    echo "  DB_NAME     - 数据库名称 (默认: accessible_nav)"
    echo "  DB_USER     - 数据库用户 (默认: postgres)"
    echo "  DB_URL      - JDBC URL（优先解析 host/port/db）"
    echo "  DB_PASSWORD - 数据库密码（自动映射为 PGPASSWORD）"
    echo "  PGPASSWORD  - 数据库密码"
    exit 1
}

# 检查依赖
check_dependencies() {
    if ! command -v pg_restore &> /dev/null; then
        log_error "pg_restore 命令未找到，请安装 PostgreSQL 客户端工具"
        exit 1
    fi
}

# 验证备份文件
verify_backup() {
    local backup_file="$1"

    log_info "验证备份文件..."

    # 检查校验和
    if [ -f "${backup_file}.sha256" ]; then
        log_info "验证校验和..."
        if sha256sum -c "${backup_file}.sha256" > /dev/null 2>&1; then
            log_info "校验和验证通过"
        else
            log_error "校验和验证失败!"
            exit 1
        fi
    else
        log_warn "未找到校验和文件，跳过校验和验证"
    fi

    # 验证文件完整性
    if gzip -t "${backup_file}" 2>/dev/null; then
        log_info "文件完整性验证通过"
    else
        log_error "备份文件已损坏!"
        exit 1
    fi
}

# 查找备份文件
find_backup_file() {
    local path="$1"

    if [ -f "${path}" ]; then
        echo "${path}"
        return
    fi

    if [ -d "${path}" ]; then
        # 在目录中查找最新的备份文件
        # Backward-compatible: accept both legacy *.sql.gz and the newer *.dump.gz naming.
        local backup_file=$(find "${path}" \( -name "*.dump.gz" -o -name "*.sql.gz" \) -type f | sort -r | head -1)
        if [ -n "${backup_file}" ]; then
            echo "${backup_file}"
            return
        fi
    fi

    log_error "未找到有效的备份文件: ${path}"
    exit 1
}

# 确认恢复操作
confirm_restore() {
    local backup_file="$1"

    echo ""
    log_warn "=========================================="
    log_warn "警告: 此操作将覆盖数据库 ${DB_NAME} 中的所有数据!"
    log_warn "=========================================="
    echo ""
    log_info "备份文件: ${backup_file}"
    log_info "目标数据库: ${DB_NAME}@${DB_HOST}:${DB_PORT}"
    echo ""

    read -p "确认要继续恢复吗? (输入 'yes' 确认): " confirm
    if [ "${confirm}" != "yes" ]; then
        log_info "恢复操作已取消"
        exit 0
    fi
}

# 恢复数据库
restore_database() {
    local backup_file="$1"

    log_info "开始恢复数据库..."

    # 断开所有现有连接
    log_info "断开数据库现有连接..."
    psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres -c "
        SELECT pg_terminate_backend(pg_stat_activity.pid)
        FROM pg_stat_activity
        WHERE pg_stat_activity.datname = '${DB_NAME}'
        AND pid <> pg_backend_pid();
    " 2>/dev/null || true

    # 删除并重建数据库
    log_info "重建数据库..."
    psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres -c "DROP DATABASE IF EXISTS ${DB_NAME};"
    psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d postgres -c "CREATE DATABASE ${DB_NAME};"

    # 创建PostGIS扩展
    log_info "创建PostGIS扩展..."
    psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" -c "CREATE EXTENSION IF NOT EXISTS postgis;"

    # 恢复数据
    log_info "恢复数据中..."
    gunzip -c "${backup_file}" | pg_restore \
        -h "${DB_HOST}" \
        -p "${DB_PORT}" \
        -U "${DB_USER}" \
        -d "${DB_NAME}" \
        -v \
        --no-owner \
        --no-privileges \
        --clean \
        --if-exists \
        2>&1 | while read line; do
            echo -e "${GREEN}[RESTORE]${NC} ${line}"
        done

    log_info "数据恢复完成"
}

# 验证恢复结果
verify_restore() {
    log_info "验证恢复结果..."

    # 检查表数量
    local table_count=$(psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" -t -c "
        SELECT COUNT(*) FROM information_schema.tables
        WHERE table_schema = 'public' AND table_type = 'BASE TABLE';
    " | tr -d ' ')

    log_info "已恢复 ${table_count} 个表"

    # 检查关键表数据
    local node_count=$(psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" -t -c "
        SELECT COUNT(*) FROM t_node;
    " 2>/dev/null | tr -d ' ' || echo "0")

    local edge_count=$(psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" -t -c "
        SELECT COUNT(*) FROM t_edge;
    " 2>/dev/null | tr -d ' ' || echo "0")

    log_info "节点数量: ${node_count}"
    log_info "边数量: ${edge_count}"
}

# 主函数
main() {
    if [ $# -lt 1 ]; then
        usage
    fi

    local input_path="$1"

    log_info "=========================================="
    log_info "开始数据库恢复"
    log_info "=========================================="

    check_dependencies

    local backup_file=$(find_backup_file "${input_path}")
    log_info "使用备份文件: ${backup_file}"

    verify_backup "${backup_file}"
    confirm_restore "${backup_file}"
    restore_database "${backup_file}"
    verify_restore

    log_info "=========================================="
    log_info "恢复完成!"
    log_info "=========================================="
}

main "$@"
