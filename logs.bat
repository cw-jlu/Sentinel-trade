@echo off
echo [Sentinel-Trade] 正在查看系统日志... (按 Ctrl+C 退出)
echo.
powershell -ExecutionPolicy Bypass -File .\manage.ps1 logs
pause
