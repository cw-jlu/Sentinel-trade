@echo off
echo [Sentinel-Trade] 正在启动系统...
powershell -ExecutionPolicy Bypass -File .\manage.ps1 start
echo.
echo 系统已在后台运行。
pause
