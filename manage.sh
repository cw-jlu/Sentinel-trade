#!/bin/bash

# Sentinel-Trade Management Script (Bash)
# Usage: ./manage.sh [start|stop|restart|status|logs|clean]

ACTION=${1:-"status"}
SERVICE=$2

compose() {
    "${DOCKER_COMPOSE_CMD[@]}" "$@"
}

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

if command -v docker-compose &> /dev/null; then
    DOCKER_COMPOSE_CMD=(docker-compose)
elif docker compose version > /dev/null 2>&1; then
    DOCKER_COMPOSE_CMD=(docker compose)
else
    echo -e "\033[0;31m错误: 未找到可用的 Docker Compose 命令。请安装 docker-compose 或启用 'docker compose' 插件。\033[0m"
    exit 1
fi

case $ACTION in
    "start")
        echo -e "\033[0;32m🚀 正在启动 Sentinel-Trade 系统...\033[0m"
        compose up -d --build
        if [ $? -ne 0 ]; then exit 1; fi
        
        echo -e "\n\033[0;32m✅ 系统已启动。\033[0m"
        echo -e "\033[0;36m🌐 正在尝试打开浏览器...\033[0m"
        
        # 在不同平台尝试打开浏览器
        URLS=("http://localhost:3000" "http://localhost:8081")
        for url in "${URLS[@]}"; do
            if command -v explorer.exe &> /dev/null; then # Windows (Git Bash/WSL)
                explorer.exe "$url"
            elif command -v open &> /dev/null; then       # macOS
                open "$url"
            elif command -v xdg-open &> /dev/null; then   # Linux
                xdg-open "$url" &> /dev/null
            fi
        done
        
        echo -e "\033[0;36m如浏览器未自动打开，请访问:\033[0m"
        echo "  - 前端看板: http://localhost:3000"
        echo "  - Flink 控制台: http://localhost:8081"
        ;;
    "stop")
        echo -e "\033[0;33m🛑 正在停止系统...\033[0m"
        compose stop
        ;;
    "restart")
        echo -e "\033[0;36m🔄 正在重启服务...\033[0m"
        if [ -n "$SERVICE" ]; then
            compose restart $SERVICE
        else
            compose restart
        fi
        ;;
    "status")
        echo -e "\033[0;36m📊 服务当前状态:\033[0m"
        compose ps
        ;;
    "logs")
        if [ -n "$SERVICE" ]; then
            compose logs -f --tail=100 $SERVICE
        else
            compose logs -f --tail=100
        fi
        ;;
    "clean")
        read -p "确定要清理所有容器和数据卷吗？数据将不可恢复 (y/n): " confirm
        if [ "$confirm" == "y" ]; then
            echo -e "\033[0;31m🧹 正在彻底清理系统...\033[0m"
            compose down -v
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
