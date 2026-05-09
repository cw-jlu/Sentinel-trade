@echo off
chcp 65001 > nul
echo [Sentinel-Trade] 正在获取系统状态...
powershell -ExecutionPolicy Bypass -File "%~dp0manage.ps1" status
echo.
pause
