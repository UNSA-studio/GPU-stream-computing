# start-workers.ps1  -- GPU Stream Computing multi worker launcher
# Usage (recommended, keeps errors visible):
#   cd <worker pack folder>
#   powershell -ExecutionPolicy Bypass -File .\start-workers.ps1
param(
    [int]$N = 4,
    [int]$BasePort = 25565
)

$pack = $PSScriptRoot
if (-not $pack) { $pack = (Get-Location).Path }
Write-Host "pack dir : $pack"

if (-not (Test-Path (Join-Path $pack 'run.bat'))) {
    Write-Host "[ERROR] run.bat not found here." -ForegroundColor Red
    Write-Host "        Put this script INSIDE the worker pack folder (next to run.bat)." -ForegroundColor Red
    Write-Host "Press any key to exit..."
    [void]$Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
    exit 1
}

$parent = Split-Path $pack -Parent

for ($i = 1; $i -le $N; $i++) {
    $dest = Join-Path $parent ("gpusc-w" + $i)
    $port = $BasePort + $i
    Write-Host ""
    Write-Host "---- instance $i  ->  $dest   (port $port) ----"

    if (-not (Test-Path (Join-Path $dest 'libraries'))) {
        Write-Host "     copying pack (mods+libraries only, ~500MB)..."
        robocopy $pack $dest /E /R:2 /W:2 /XD world logs crash-reports /XF worker-pack.zip /NFL /NDL /NJH /NJS /NP | Out-Null
    } else {
        Write-Host "     reuse existing copy"
    }

    if (-not (Test-Path (Join-Path $dest 'run.bat'))) {
        Write-Host "[ERROR] copy failed for $dest" -ForegroundColor Red
        continue
    }

    $sp = Join-Path $dest 'server.properties'
    if (Test-Path $sp) {
        (Get-Content -LiteralPath $sp) -replace '^server-port=.*', "server-port=$port" |
            Set-Content -LiteralPath $sp
    } else {
        "server-port=$port" | Set-Content -LiteralPath $sp
    }
    "eula=true" | Set-Content -LiteralPath (Join-Path $dest 'eula.txt')

    Start-Process -FilePath "cmd.exe" -ArgumentList "/k", "run.bat" -WorkingDirectory $dest
    Write-Host "     launched"
}

Write-Host ""
Write-Host "=============================================="
Write-Host " $N worker windows launched (1 window = 1 instance)"
Write-Host " If a window shows an error, send me that text."
Write-Host " Press any key to exit..."
[void]$Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")