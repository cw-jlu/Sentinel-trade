@echo off
echo [Sentinel-Trade] 正在获取系统状态...
powershell -ExecutionPolicy Bypass -File .\manage.ps1 status
echo.
pause
