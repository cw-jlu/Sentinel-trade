@echo off
chcp 65001 > nul
setlocal
echo [Sentinel-Trade] 正在停止系统...

powershell -ExecutionPolicy Bypass -File "%~dp0manage.ps1" stop

if %ERRORLEVEL% neq 0 (
    echo.
    echo [警告] 停止过程中出现问题，请检查 Docker 状态。
) else (
    echo.
    echo [成功] 系统容器已停止。
)

pause
endlocal
