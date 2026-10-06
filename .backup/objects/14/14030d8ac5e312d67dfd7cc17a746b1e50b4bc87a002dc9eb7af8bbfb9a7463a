@echo off
setlocal enabledelayedexpansion
title GPU-SC multi-worker launcher
set N=4
set BASEPORT=25565
cd /d "%~dp0"

echo ==========================================================
echo   GPU Stream Computing - multi worker launcher
echo   instances : %N%
echo   pack dir  : %CD%
echo ==========================================================
echo.

for /L %%i in (1,1,%N%) do (
    set /a PORT=!BASEPORT!+%%i
    set DEST=%CD%\..\gpusc-w%%i

    if not exist "!DEST!\libraries" (
        echo [%%i] copying pack  -^>  "!DEST!"   (about 500MB, please wait)
        robocopy "%CD%" "!DEST!" /E /NFL /NDL /NJH /NJS /NP >nul
    ) else (
        echo [%%i] reuse existing "!DEST!"
    )

    powershell -NoProfile -Command "(Get-Content -LiteralPath '!DEST!\server.properties') -replace '^server-port=.*', 'server-port=!PORT!' | Set-Content -LiteralPath '!DEST!\server.properties'"

    echo [%%i] port=!PORT!  launching...
    start "gpusc worker %%i (port !PORT!)" /D "!DEST!" cmd /c run.bat
)

echo.
echo ==========================================================
echo   %N% 个工人已启动：
echo     - 每个窗口 = 一个独立实例（各领各的任务）
echo     - 关闭窗口即可停掉对应实例
echo     - 首次启动每个实例约需 1-3 分钟（要生成自己的世界）
echo ==========================================================
pause
