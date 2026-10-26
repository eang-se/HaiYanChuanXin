$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$statePath = Join-Path $projectRoot '.runtime/server.json'
if (!(Test-Path -LiteralPath $statePath)) { Write-Host 'No recorded server process.'; exit 0 }
$state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
$processInfo = Get-CimInstance Win32_Process -Filter "ProcessId = $($state.pid)" -ErrorAction SilentlyContinue
if ($processInfo) {
    $expectedJar = Join-Path $projectRoot 'backend/target/haiyan-chuanxin.jar'
    if (!$processInfo.CommandLine.Contains($expectedJar) -or
            [math]::Abs($processInfo.CreationDate.ToUniversalTime().Ticks - ([datetime]$state.creationTime).ToUniversalTime().Ticks) -ge 10) {
        throw 'Process identity changed; refusing to stop an unrelated process.'
    }
    Stop-Process -Id $state.pid
    Write-Host 'Local server stopped.'
} else { Write-Host 'Recorded process has already exited.' }
Remove-Item -LiteralPath $statePath -Force

