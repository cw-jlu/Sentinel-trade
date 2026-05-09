@echo off
chcp 65001 > nul
setlocal
echo [Sentinel-Trade] 正在检查环境并启动系统...

:: 尝试执行 manage.ps1 start
powershell -ExecutionPolicy Bypass -File "%~dp0manage.ps1" start

if %ERRORLEVEL% neq 0 (
    echo.
    echo [错误] 系统启动失败，请检查 Docker 是否并正常运行。
) else (
    echo.
    echo [成功] 系统启动流程已完成。
)

pause
endlocal
