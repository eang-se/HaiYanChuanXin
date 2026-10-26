$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$statePath = Join-Path $projectRoot '.runtime/tunnel.json'
if (!(Test-Path -LiteralPath $statePath)) { Write-Host 'No temporary tunnel recorded.'; exit 0 }
$state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
if (!$state.pid) { Write-Host 'Temporary tunnel is already stopped.'; exit 0 }
$running = Get-Process -Id $state.pid -ErrorAction SilentlyContinue
if ($running) {
    if ($running.Path -ne 'C:\Program Files\SakuraFrpLauncher\frpc.exe' -or
        $running.StartTime.ToUniversalTime().Ticks -ne ([datetime]$state.processStart).ToUniversalTime().Ticks) {
        throw 'Process identity changed; refusing to stop an unrelated process.'
    }
    Stop-Process -Id $state.pid
}
$state.pid = $null
$state | ConvertTo-Json | Set-Content -LiteralPath $statePath -Encoding utf8
Write-Host 'Temporary tunnel stopped. The local platform and SakuraFrp Launcher remain running.'
Write-Host 'The offline tunnel entry is retained for reuse; no auto-start was configured.'

