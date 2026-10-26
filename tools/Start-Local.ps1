param([ValidateRange(1024,65535)][int]$Port = 8091, [switch]$Lan)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Use-Environment.ps1')
$runtimeRoot = Join-Path $projectRoot '.runtime'
New-Item -ItemType Directory -Path $runtimeRoot -Force | Out-Null
$statePath = Join-Path $runtimeRoot 'server.json'
$jarPath = Join-Path $projectRoot 'backend/target/haiyan-chuanxin.jar'
$configPath = Join-Path $projectRoot '.local/application-local.properties'
if (!(Test-Path -LiteralPath $jarPath)) { throw 'Run tools/Build.ps1 first.' }
if (!(Test-Path -LiteralPath $configPath)) { throw 'Run tools/Initialize-Database.ps1 first.' }
if (Test-Path -LiteralPath $statePath) {
    $previous = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    $running = Get-CimInstance Win32_Process -Filter "ProcessId = $($previous.pid)" -ErrorAction SilentlyContinue
    if ($running -and $running.CommandLine.Contains($jarPath) -and
            ([math]::Abs($running.CreationDate.ToUniversalTime().Ticks - ([datetime]$previous.creationTime).ToUniversalTime().Ticks) -lt 10)) {
        Write-Host "Already running at http://127.0.0.1:$($previous.port)/"
        exit 0
    }
}
$listener = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue
if ($listener) { throw "Port $Port is already in use. Choose -Port with another value." }
$bindAddress = if ($Lan) { '0.0.0.0' } else { '127.0.0.1' }
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
$stdoutPath = Join-Path $runtimeRoot ($timestamp + '.out.log')
$stderrPath = Join-Path $runtimeRoot ($timestamp + '.err.log')
$javaPath = Join-Path $env:JAVA_HOME 'bin/java.exe'
$arguments = @('-Xms128m', '-Xmx768m', '-jar', ('"' + $jarPath + '"'),
    ('"--spring.config.additional-location=file:' + $configPath.Replace('\','/') + '"'),
    "--server.port=$Port", "--server.address=$bindAddress")
$process = Start-Process -FilePath $javaPath -ArgumentList $arguments -WorkingDirectory $projectRoot -PassThru -WindowStyle Hidden -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath
$processInfo = Get-CimInstance Win32_Process -Filter "ProcessId = $($process.Id)"
[ordered]@{
    pid = $process.Id
    creationTime = $process.StartTime.ToUniversalTime().ToString('o')
    port = $Port
    address = $bindAddress
    jar = $jarPath
    stdout = $stdoutPath
    stderr = $stderrPath
} | ConvertTo-Json | Set-Content -LiteralPath $statePath -Encoding utf8
$ready = $false
for ($attempt = 0; $attempt -lt 45; $attempt++) {
    if ($process.HasExited) { throw "Server exited. See $stdoutPath and $stderrPath" }
    try {
        $health = Invoke-RestMethod "http://127.0.0.1:$Port/actuator/health" -TimeoutSec 2
        if ($health.status -eq 'UP') { $ready = $true; break }
    } catch { }
    Start-Sleep -Milliseconds 800
}
if (!$ready) { throw "Server not ready yet. See $stdoutPath" }
Write-Host "Started: http://127.0.0.1:$Port/"
Write-Host "Health: http://127.0.0.1:$Port/actuator/health"
if ($Lan) {
    Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' } | ForEach-Object {
        Write-Host "LAN candidate: http://$($_.IPAddress):$Port/ (same network; firewall may need a scoped inbound rule)"
    }
}

