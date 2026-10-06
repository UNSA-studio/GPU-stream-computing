@echo off
setlocal enabledelayedexpansion
title GPU-SC multi-worker launcher v2

rem ============ config ============
set N=4
set BASEPORT=25565
rem ================================

cd /d "%~dp0"
set LOG=%~dp0start-workers.log
echo [%date% %time%] launcher start  pack=%CD%  > "%LOG%"

echo ==========================================================
echo   GPU Stream Computing - multi worker launcher (v2)
echo   pack dir : %CD%
echo   instances: %N%
echo ==========================================================
echo.

rem ---------- sanity check: are we inside the worker pack? ----------
if not exist "run.bat" (
    echo [ERROR] run.bat NOT found in this folder.
    echo         Put this script INSIDE the worker pack folder
    echo         ^(the folder that contains run.bat / libraries / mods^)
    echo.
    echo [错误] 当前目录下找不到 run.bat
    echo        请把这个脚本放到【工人包文件夹】里（和 run.bat 同一个目录）
    echo.
    echo ^(path = %CD%^)
    pause
    exit /b 1
)
if not exist "libraries\net\neoforged" (
    echo [ERROR] libraries folder is missing - the pack is incomplete.
    echo [错误] libraries 目录不完整，工人包没解压完整
    pause
    exit /b 1
)

for /L %%i in (1,1,%N%) do (
    set /a PORT=!BASEPORT!+%%i
    set DEST=%CD%\..\gpusc-w%%i

    echo ----------------------------------------------------------
    echo [%%i] target dir : !DEST!
    echo [%%i] server port: !PORT!

    if not exist "!DEST!\libraries" (
        echo [%%i] copying pack ... ^(about 500MB, may take 1-3 min^)
        robocopy "%CD%" "!DEST!" /E /NFL /NDL /NJH /NJS /NP >>"%LOG%" 2>&1
        if not exist "!DEST!\run.bat" (
            echo [%%i] [ERROR] copy failed!  See start-workers.log
            echo [%%i] [错误] 复制失败，请看 start-workers.log
            goto :done
        )
    ) else (
        echo [%%i] reuse existing copy
    )

    rem ---- set a unique port for this instance ----
    if exist "!DEST!\server.properties" (
        powershell -NoProfile -Command "(Get-Content -LiteralPath '!DEST!\server.properties') -replace '^server-port=.*','server-port=!PORT!' | Set-Content -LiteralPath '!DEST!\server.properties'" >>"%LOG%" 2>&1
    ) else (
        echo server-port=!PORT!> "!DEST!\server.properties"
    )

    rem ---- make sure eula is accepted ----
    echo eula=true> "!DEST!\eula.txt"

    rem ---- launch (cmd /k so the window STAYS OPEN even if it fails) ----
    echo [%%i] launching instance window...
    start "gpusc worker %%i  port !PORT!" /D "!DEST!" cmd /k run.bat
)

:done
echo.
echo ==========================================================
echo   Launcher finished.
echo   Each opened window = one worker instance.
echo   If a window closes right away, read the red text inside it
echo   ^(and send it to me^).
echo.
echo   已启动的窗口 = 工人实例；若有窗口闪退，请把窗口里的报错发我
echo ==========================================================
pause