#!/bin/bash

# Sentinel-Trade Management Script (Bash)
# Usage: ./manage.sh [start|stop|restart|status|logs|clean]

ACTION=${1:-"status"}
SERVICE=$2

DOCKER_COMPOSE="docker-compose"

show_help() {
    echo -e "\033[0;36mSentinel-Trade 管理脚本\033[0m"
    echo "用法: ./manage.sh [Action] [Service]"
    echo -e "\n可用操作:"
    echo "  start   - 启动所有服务 (构建并后台运行)"
    echo "  stop    - 停止并移除容器"
    echo "  restart - 重启服务"
    echo "  status  - 查看服务运行状态"
    echo "  logs    - 查看日志 (可选指定服务名，如: ./manage.sh logs ingestion)"
    echo "  clean   - 彻底清理 (移除容器、网络及磁盘数据卷)"
    echo "  ui      - 显示 Web 界面访问地址"
}

# 检查 Docker 环境
if ! command -v docker &> /dev/null; then
    echo -e "\033[0;31m错误: 未找到 Docker，请先安装 Docker 环境。\033[0m"
    exit 1
fi

case $ACTION in
    "start")
        echo -e "\033[0;32m🚀 正在启动 Sentinel-Trade 系统...\033[0m"
        $DOCKER_COMPOSE up -d --build
        echo -e "\n\033[0;32m✅ 系统已在后台启动。使用 './manage.sh status' 查看状态。\033[0m"
        ;;
    "stop")
        echo -e "\033[0;33m🛑 正在停止系统...\033[0m"
        $DOCKER_COMPOSE stop
        ;;
    "restart")
        echo -e "\033[0;36m🔄 正在重启服务...\033[0m"
        if [ -n "$SERVICE" ]; then
            $DOCKER_COMPOSE restart $SERVICE
        else
            $DOCKER_COMPOSE restart
        fi
        ;;
    "status")
        echo -e "\033[0;36m📊 服务当前状态:\033[0m"
        $DOCKER_COMPOSE ps
        ;;
    "logs")
        if [ -n "$SERVICE" ]; then
            $DOCKER_COMPOSE logs -f --tail=100 $SERVICE
        else
            $DOCKER_COMPOSE logs -f --tail=100
        fi
        ;;
    "clean")
        read -p "确定要清理所有容器和数据卷吗？数据将不可恢复 (y/n): " confirm
        if [ "$confirm" == "y" ]; then
            echo -e "\033[0;31m🧹 正在彻底清理系统...\033[0m"
            $DOCKER_COMPOSE down -v
        fi
        ;;
    "ui")
        echo -e "\033[0;36m🌐 Web 界面地址:\033[0m"
        echo "  - 前端看板: http://localhost:3000"
        echo "  - Flink 控制台: http://localhost:8081"
        ;;
    *)
        show_help
        ;;
esac
