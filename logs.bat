@echo off
chcp 65001 > nul
echo [Sentinel-Trade] 正在查看系统日志... (按 Ctrl+C 退出)
echo.
powershell -ExecutionPolicy Bypass -File "%~dp0manage.ps1" logs
pause
