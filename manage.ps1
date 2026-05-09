
# Sentinel-Trade Management Script (PowerShell)
# Usage: ./manage.ps1 [start|stop|restart|status|logs|clean|ui]

param (
    [Parameter(Mandatory=$false, Position=0)]
    [ValidateSet("start", "stop", "restart", "status", "logs", "clean", "ui")]
    $Action = "status",

    [Parameter(Mandatory=$false, Position=1)]
    $Service = ""
)

function Resolve-ComposeMode {
    if (Get-Command docker-compose -ErrorAction SilentlyContinue) {
        return "standalone"
    }

    if (Get-Command docker -ErrorAction SilentlyContinue) {
        & docker compose version *> $null
        if ($LASTEXITCODE -eq 0) {
            return "plugin"
        }
    }

    return "none"
}

function Invoke-Compose {
    param([Parameter(ValueFromRemainingArguments=$true)][string[]]$ComposeArgs)

    if ($script:ComposeMode -eq "plugin") {
        & docker compose @ComposeArgs
    } else {
        & docker-compose @ComposeArgs
    }
}

function Show-UI {
    Write-Host "🌐 正在打开 Web 界面..." -ForegroundColor Cyan
    Start-Process "http://localhost:3000"
    Start-Process "http://localhost:8081"
}

function Show-Help {
    Write-Host "Sentinel-Trade 管理脚本" -ForegroundColor Cyan
    Write-Host "用法: ./manage.ps1 [Action] [Service]"
    Write-Host "`n可用操作:"
    Write-Host "  start   - 启动所有服务 (构建并后台运行) 并打开浏览器"
    Write-Host "  stop    - 停止服务 (不移除容器)"
    Write-Host "  restart - 重启服务"
    Write-Host "  status  - 查看服务运行状态"
    Write-Host "  logs    - 查看日志 (可选指定服务名，如: ./manage.ps1 logs ingestion)"
    Write-Host "  clean   - 彻底清理 (移除容器、网络及磁盘数据卷)"
    Write-Host "  ui      - 打开 Flink 控制台 (8081) 和前端看板 (3000)"
}

# 检查 Docker 环境
if (!(Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Error "错误: 未找到 Docker，请先安装 Docker Desktop。"
    exit 1
}

$script:ComposeMode = Resolve-ComposeMode
if ($script:ComposeMode -eq "none") {
    Write-Error "错误: 未找到可用的 Docker Compose 命令。请安装 docker-compose 或启用 'docker compose' 插件。"
    exit 1
}

switch ($Action) {
    "start" {
        Write-Host "🚀 正在启动 Sentinel-Trade 系统..." -ForegroundColor Green
        Invoke-Compose up -d --build
        if ($LASTEXITCODE -ne 0) {
            exit $LASTEXITCODE
        }
        
        Write-Host "⏳ 正在等待核心服务就绪..." -ForegroundColor Gray
        $maxRetries = 30
        $retryCount = 0
        $ready = $false
        
        while ($retryCount -lt $maxRetries -and -not $ready) {
            Start-Sleep -Seconds 2
            $status = Invoke-Compose ps --format json
            # 简单检查是否有 frontend 且状态非 Starting
            if ($status -like "*frontend*" -and $status -notlike "*starting*") {
                $ready = $true
            }
            $retryCount++
        }

        Write-Host "`n✅ 系统已启动。" -ForegroundColor Green
        Show-UI
    }
    "stop" {
        Write-Host "🛑 正在停止系统..." -ForegroundColor Yellow
        Invoke-Compose stop
        if ($LASTEXITCODE -ne 0) {
            exit $LASTEXITCODE
        }
    }
    "restart" {
        Write-Host "🔄 正在重启服务..." -ForegroundColor Cyan
        if ($Service -ne "") {
            Invoke-Compose restart $Service
        } else {
            Invoke-Compose restart
        }
        if ($LASTEXITCODE -ne 0) {
            exit $LASTEXITCODE
        }
    }
    "status" {
        Write-Host "📊 服务当前状态:" -ForegroundColor Cyan
        Invoke-Compose ps
        if ($LASTEXITCODE -ne 0) {
            exit $LASTEXITCODE
        }
    }
    "logs" {
        if ($Service -ne "") {
            Invoke-Compose logs -f --tail=100 $Service
        } else {
            Invoke-Compose logs -f --tail=100
        }
        if ($LASTEXITCODE -ne 0) {
            exit $LASTEXITCODE
        }
    }
    "clean" {
        $confirm = Read-Host "确定要清理所有容器和数据卷吗？数据将不可恢复 (y/n)"
        if ($confirm -eq "y") {
            Write-Host "🧹 正在彻底清理系统..." -ForegroundColor Red
            Invoke-Compose down -v
            if ($LASTEXITCODE -ne 0) {
                exit $LASTEXITCODE
            }
        }
    }
    "ui" {
        Show-UI
    }
    default {
        Show-Help
    }
}
