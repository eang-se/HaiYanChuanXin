Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Initialize-HaiYanLauncher {
    param([string]$ProjectRoot, [string]$DataRoot, [string]$StateFile)
    $script:ProjectRoot = [IO.Path]::GetFullPath($ProjectRoot)
    if (-not $DataRoot) { $DataRoot = Join-Path $script:ProjectRoot '.runtime/backend-launcher' }
    $script:DataRoot = [IO.Path]::GetFullPath($DataRoot)
    [IO.Directory]::CreateDirectory($script:DataRoot) | Out-Null
    $script:SettingsFile = Join-Path $script:DataRoot 'settings.json'
    $script:StateFile = if ($StateFile) { [IO.Path]::GetFullPath($StateFile) } else { Join-Path $script:ProjectRoot '.runtime/server.json' }
}

function Test-HaiYanSettings {
    param([string]$Address, $Port)
    $Address = $Address.Trim()
    $ip = $null
    if ($Address -ne 'localhost' -and (-not [Net.IPAddress]::TryParse($Address, [ref]$ip) -or $ip.AddressFamily -ne 'InterNetwork')) {
        throw '监听地址请输入 IP 地址或 localhost，例如 127.0.0.1，不要填写 http:// 或端口。'
    }
    $number = 0
    if (-not [int]::TryParse([string]$Port, [ref]$number) -or $number -lt 1024 -or $number -gt 65535) {
        throw '端口必须是 1024 到 65535 之间的整数。'
    }
    return [pscustomobject]@{ address = $Address; port = $number }
}

function Write-HaiYanJson {
    param([string]$Path, $Value)
    $temp = $Path + '.' + [Guid]::NewGuid().ToString('N') + '.tmp'
    try {
        [IO.File]::WriteAllText($temp, ($Value | ConvertTo-Json -Depth 5), [Text.UTF8Encoding]::new($false))
        Move-Item -LiteralPath $temp -Destination $Path -Force
    } finally { if (Test-Path -LiteralPath $temp) { Remove-Item -LiteralPath $temp } }
}

function Get-HaiYanSettings {
    if (-not (Test-Path -LiteralPath $script:SettingsFile)) {
        $existing = Get-HaiYanManagedProcess
        if ($existing) { return Test-HaiYanSettings $existing.State.address $existing.State.port }
        return Test-HaiYanSettings '127.0.0.1' 8091
    }
    try {
        $saved = [IO.File]::ReadAllText($script:SettingsFile) | ConvertFrom-Json
        return Test-HaiYanSettings $saved.address $saved.port
    } catch { throw ('启动设置无法读取：' + $_.Exception.Message + "`n文件：" + $script:SettingsFile) }
}

function Save-HaiYanSettings {
    param([string]$Address, $Port)
    $settings = Test-HaiYanSettings $Address $Port
    Write-HaiYanJson $script:SettingsFile $settings
    return $settings
}

function Get-HaiYanJava {
    $manifest = Join-Path $script:ProjectRoot '.tools/toolchain.json'
    if (-not (Test-Path -LiteralPath $manifest)) { throw '未找到项目工具链，请先运行 tools/Install-Toolchain.ps1。' }
    $tools = [IO.File]::ReadAllText($manifest) | ConvertFrom-Json
    $java = Join-Path $tools.javaHome 'bin/java.exe'
    if (-not (Test-Path -LiteralPath $java)) { throw '项目 Java 路径不存在，请修复项目工具链。' }
    return $java
}

function Get-HaiYanJar {
    $path = Join-Path $script:ProjectRoot 'backend/target/haiyan-chuanxin.jar'
    if (-not (Test-Path -LiteralPath $path)) { throw '未找到后端包，请先运行项目中的 Build.cmd。' }
    return $path
}

function Get-HaiYanManagedProcess {
    if (-not (Test-Path -LiteralPath $script:StateFile)) { return $null }
    try {
        $state = [IO.File]::ReadAllText($script:StateFile) | ConvertFrom-Json
        $process = Get-Process -Id $state.pid -ErrorAction Stop
        # PowerShell 7 can deserialize ISO timestamps into DateTime, while 5.1 keeps strings.
        $started = if ($state.creationTime -is [DateTime]) { $state.creationTime.ToUniversalTime() } else {
            [DateTimeOffset]::Parse([string]$state.creationTime, [Globalization.CultureInfo]::InvariantCulture).UtcDateTime
        }
        if ($process.HasExited -or [math]::Abs($process.StartTime.ToUniversalTime().Ticks - $started.Ticks) -ge 10) { return $null }
        if ($process.Path -ne (Get-HaiYanJava) -or $state.jar -ne (Get-HaiYanJar)) { return $null }
        $native = Get-CimInstance Win32_Process -Filter ("ProcessId = " + [int]$state.pid)
        if (-not $native -or $native.CommandLine.IndexOf($state.jar, [StringComparison]::OrdinalIgnoreCase) -lt 0) { return $null }
        return [pscustomobject]@{ Process = $process; State = $state }
    } catch { return $null }
}

function Get-HaiYanUrl {
    param($Settings)
    $address = [string]$Settings.address
    if ($address -in @('0.0.0.0', 'localhost')) { $address = '127.0.0.1' }
    if ($address -eq '::') { $address = '::1' }
    if ($address.Contains(':')) { $address = '[' + $address + ']' }
    return 'http://' + $address + ':' + $Settings.port + '/'
}

function Get-HaiYanLanUrls {
    param([int]$Port, [string]$Address = '0.0.0.0')
    if ($Address -in @('localhost','127.0.0.1','::1')) { return }
    foreach ($adapter in [Net.NetworkInformation.NetworkInterface]::GetAllNetworkInterfaces()) {
        if ($adapter.OperationalStatus -ne 'Up' -or $adapter.NetworkInterfaceType -notin @('Ethernet','Wireless80211')) { continue }
        $properties = $adapter.GetIPProperties()
        if (-not @($properties.GatewayAddresses | Where-Object { $_.Address.AddressFamily -eq 'InterNetwork' -and $_.Address.ToString() -ne '0.0.0.0' }).Count) { continue }
        foreach ($unicast in $properties.UnicastAddresses) {
            $ip = $unicast.Address.ToString()
            if ($unicast.Address.AddressFamily -ne 'InterNetwork' -or $ip.StartsWith('127.') -or $ip.StartsWith('169.254.')) { continue }
            if ($Address -ne '0.0.0.0' -and $Address -ne $ip) { continue }
            'http://' + $ip + ':' + $Port + '/'
        }
    }
}

function Test-HaiYanPortAvailable {
    param($Settings)
    $address = $Settings.address
    if ($address -eq 'localhost') { $address = '127.0.0.1' }
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Parse($address), $Settings.port)
    $listener.Server.ExclusiveAddressUse = $true
    try { $listener.Start() }
    catch { throw ('无法使用 ' + $Settings.address + ':' + $Settings.port + '。地址可能不属于本机，或端口已被占用，请修改设置。') }
    finally { $listener.Stop() }
}

function Start-HaiYanBackend {
    param([string]$Address, $Port)
    $settings = Test-HaiYanSettings $Address $Port
    $lock = $null
    try {
        try { $lock = [IO.File]::Open((Join-Path $script:DataRoot 'launch.lock'), 'OpenOrCreate', 'ReadWrite', 'None') }
        catch { throw '另一个启动操作正在进行，请稍后重试。' }
        $existing = Get-HaiYanManagedProcess
        if ($existing) {
            if ($existing.State.address -ne $settings.address -or $existing.State.port -ne $settings.port) {
                throw '后端正在使用其他地址或端口，请先点击“停止后端”，再保存并启动。'
            }
            return $existing.State
        }
        Test-HaiYanPortAvailable $settings
        $java = Get-HaiYanJava
        $jar = Get-HaiYanJar
        $config = Join-Path $script:ProjectRoot '.local/application-local.properties'
        if (-not (Test-Path -LiteralPath $config)) { throw '数据库配置不存在，请先初始化数据库。' }
        $logDir = Split-Path $script:StateFile -Parent
        [IO.Directory]::CreateDirectory($logDir) | Out-Null
        $stamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
        $outLog = Join-Path $logDir ($stamp + '.out.log')
        $errLog = Join-Path $logDir ($stamp + '.err.log')
        $arguments = @('-Xms128m', '-Xmx768m', '-Dfile.encoding=UTF-8', '-jar', ('"' + $jar + '"'),
            ('"--spring.config.additional-location=file:' + $config.Replace('\','/') + '"'),
            ('--server.address=' + $settings.address), ('--server.port=' + $settings.port), '--spring.output.ansi.enabled=never')
        $process = Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory $script:ProjectRoot `
            -WindowStyle Hidden -RedirectStandardOutput $outLog -RedirectStandardError $errLog -PassThru
        $process.Refresh()
        $state = [pscustomobject]@{
            pid = $process.Id; creationTime = $process.StartTime.ToUniversalTime().ToString('o')
            jar = $jar; address = $settings.address; port = $settings.port
            stdout = $outLog; stderr = $errLog
        }
        Write-HaiYanJson $script:StateFile $state
        Save-HaiYanSettings $settings.address $settings.port | Out-Null
        return $state
    } finally { if ($lock) { $lock.Dispose() } }
}

function Stop-HaiYanBackend {
    $lock = $null
    try {
        try { $lock = [IO.File]::Open((Join-Path $script:DataRoot 'launch.lock'), 'OpenOrCreate', 'ReadWrite', 'None') }
        catch { throw '另一个启动操作正在进行，请稍后重试。' }
        $managed = Get-HaiYanManagedProcess
        if (-not $managed) { return }
        Stop-Process -Id $managed.Process.Id -ErrorAction Stop
        $managed.Process.WaitForExit(10000) | Out-Null
        Remove-Item -LiteralPath $script:StateFile -ErrorAction SilentlyContinue
    } finally { if ($lock) { $lock.Dispose() } }
}

function Get-HaiYanLauncherPaths {
    return [pscustomobject]@{ ProjectRoot = $script:ProjectRoot; DataRoot = $script:DataRoot; SettingsFile = $script:SettingsFile }
}

Export-ModuleMember -Function Initialize-HaiYanLauncher,Test-HaiYanSettings,Get-HaiYanSettings,Save-HaiYanSettings,Get-HaiYanJava,Get-HaiYanJar,Get-HaiYanManagedProcess,Get-HaiYanUrl,Get-HaiYanLanUrls,Start-HaiYanBackend,Stop-HaiYanBackend,Get-HaiYanLauncherPaths
