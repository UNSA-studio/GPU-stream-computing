@echo off
rem ====================================================================
rem  GPU Stream Computing - multi worker launcher v3  (ASCII only / safe)
rem  Put this file INSIDE the worker pack folder (next to run.bat)
rem ====================================================================
cd /d "%~dp0"
if not exist "run.bat" goto norun

call :one 1
call :one 2
call :one 3
call :one 4

echo.
echo ==========================================================
echo  DONE - 4 worker windows launched (each = one instance)
echo  If a window shows an error, copy that text and send it.
echo ==========================================================
pause
exit /b 0

:one
set /a PORT=25565+%1
set DEST=%~dp0..\gpusc-w%1
set SRC=%~dp0.
echo ---- instance %1 : "%DEST%"  port %PORT% ----
if exist "%DEST%\libraries" goto reuse
echo      copying pack (mods+libraries only, about 500MB)...
robocopy "%SRC%" "%DEST%" /E /R:2 /W:2 /XD world logs crash-reports /XF worker-pack.zip /NFL /NDL /NJH /NJS /NP >nul
:reuse
if not exist "%DEST%\run.bat" goto copyfail
powershell -NoProfile -Command "(Get-Content -LiteralPath '%DEST%\server.properties') -replace '^server-port=.*','server-port=%PORT%' | Set-Content -LiteralPath '%DEST%\server.properties'"
echo eula=true> "%DEST%\eula.txt"
echo      launching window...
start "gpusc worker %1  port %PORT%" /D "%DEST%" cmd /k run.bat
exit /b 0

:copyfail
echo  [ERROR] copy failed for %DEST%
echo  [ERROR] check disk space / permissions
pause
exit /b 1

:norun
echo ==========================================================
echo  [ERROR] run.bat NOT found in this folder:
echo          %CD%
echo  Put this launcher INSIDE the worker pack folder
echo  (the folder containing run.bat / libraries / mods)
echo ==========================================================
pause
exit /b 1