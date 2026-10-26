#Requires -Version 7.4
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$runtimeRoot = Join-Path $projectRoot '.runtime'
New-Item -ItemType Directory -Path $runtimeRoot -Force | Out-Null
$statePath = Join-Path $runtimeRoot 'tunnel.json'
$frpcPath = 'C:\Program Files\SakuraFrpLauncher\frpc.exe'
$launcherConfig = 'C:\ProgramData\SakuraFrpService\config.json'
if (!(Test-Path -LiteralPath $frpcPath)) { throw 'SakuraFrp frpc is not installed at the expected path.' }
if (!(Test-Path -LiteralPath $launcherConfig)) { throw 'Log in to SakuraFrp Launcher first.' }
$health = Invoke-RestMethod 'http://127.0.0.1:8091/actuator/health' -TimeoutSec 5
if ($health.status -ne 'UP') { throw 'Start the local platform first using Start.cmd.' }
$expectedSite = Invoke-RestMethod 'http://127.0.0.1:8091/api/public/site' -TimeoutSec 5
if (Test-Path -LiteralPath $statePath) {
    $previous = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    if ($previous.pid) {
        $running = Get-Process -Id $previous.pid -ErrorAction SilentlyContinue
        if ($running -and $running.Path -eq $frpcPath -and
            $running.StartTime.ToUniversalTime().Ticks -eq ([datetime]$previous.processStart).ToUniversalTime().Ticks) {
            Write-Host ('Already running: ' + $previous.url)
            exit 0
        }
    }
}
$config = Get-Content -LiteralPath $launcherConfig -Raw | ConvertFrom-Json
if ([string]::IsNullOrWhiteSpace($config.token)) { throw 'Log in to SakuraFrp Launcher first.' }
$headers = @{ Authorization = 'Bearer ' + $config.token }
$nodes = Invoke-RestMethod 'https://api.natfrp.com/v4/nodes' -Headers $headers -TimeoutSec 20
$node = $nodes.'72'
if (!$node -or $node.vip -ne 0 -or !($node.flag -band 4) -or ($node.flag -band 512)) {
    throw 'The selected free node (Japan 1, ID 72) is unavailable. No tunnel was created.'
}
$marker = 'HaiYanChuanXin temporary web preview'
$tunnels = @(Invoke-RestMethod 'https://api.natfrp.com/v4/tunnels' -Headers $headers -TimeoutSec 20)
$own = @($tunnels | Where-Object { $_.name -eq 'HaiYanTemp' -and $_.note -eq $marker -and $_.type -eq 'tcp' -and $_.node -eq 72 -and $_.local_ip -eq '127.0.0.1' -and $_.local_port -eq 8091 })
if ($own.Count -gt 1) { throw 'Multiple matching tunnels found. Review them in SakuraFrp Launcher.' }
if ($own.Count -eq 1) { $tunnel = $own[0] }
else {
    $body = @{ name = 'HaiYanTemp'; type = 'tcp'; node = 72; local_ip = '127.0.0.1'; local_port = 8091; note = $marker } | ConvertTo-Json
    $tunnel = Invoke-RestMethod 'https://api.natfrp.com/v4/tunnels' -Method Post -Headers $headers -ContentType 'application/json' -Body $body -TimeoutSec 20
}
if (!$tunnel.id) { throw 'SakuraFrp did not return a tunnel ID.' }
$remotePort = 0
if (![int]::TryParse([string]$tunnel.remote, [ref]$remotePort) -or $remotePort -lt 1 -or $remotePort -gt 65535) { throw 'Invalid remote port returned by SakuraFrp.' }
$url = 'http://' + $node.host + ':' + $remotePort + '/'
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$stdout = Join-Path $runtimeRoot ('frpc-' + $timestamp + '.out.log')
$stderr = Join-Path $runtimeRoot ('frpc-' + $timestamp + '.err.log')
# Credentials are inherited only by the child process, never placed in command-line arguments or saved to the project.
$process = Start-Process -FilePath $frpcPath -ArgumentList @('-n', '--disable_log_color') -WorkingDirectory $runtimeRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr -Environment @{ NATFRP_TOKEN = [string]$config.token; NATFRP_TARGET = [string]$tunnel.id }
$state = [ordered]@{ tunnelId = $tunnel.id; name = 'HaiYanTemp'; node = 72; localPort = 8091; url = $url; pid = $process.Id; processStart = $process.StartTime.ToUniversalTime().ToString('o'); stdout = $stdout; stderr = $stderr }
$state | ConvertTo-Json | Set-Content -LiteralPath $statePath -Encoding utf8
$url | Set-Content -LiteralPath (Join-Path $runtimeRoot 'tunnel-address.txt') -Encoding utf8
Write-Host ('Tunnel created. Public URL: ' + $url)
$ready = $false
for ($attempt = 0; $attempt -lt 12; $attempt++) {
    $process.Refresh()
    if ($process.HasExited) { throw ('frpc exited. Review local logs: ' + $stdout + ' / ' + $stderr) }
    try {
        $site = Invoke-RestMethod ($url + 'api/public/site') -TimeoutSec 4
        if ($site.name -eq $expectedSite.name -and $site.description -eq $expectedSite.description) { $ready = $true; break }
    } catch { }
    Start-Sleep -Milliseconds 500
}
if ($ready) { Write-Host 'Public site API responded successfully.' }
else { Write-Warning 'Process started, but public access is not verified. Review frpc logs and node status.' }
Write-Host 'Stop the temporary channel with Stop-Tunnel.cmd. No auto-start was configured.'

