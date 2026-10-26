param([switch]$ConfigureOnly)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.Net.Http
[Windows.Forms.Application]::EnableVisualStyles()
Import-Module (Join-Path $PSScriptRoot 'BackendLauncher.psm1') -Force
Initialize-HaiYanLauncher -ProjectRoot (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent)

$form = [Windows.Forms.Form]::new()
$form.Text = '海燕传信 · 后端管理'
$form.ClientSize = [Drawing.Size]::new(600, 490)
$form.FormBorderStyle = 'FixedDialog'
$form.MaximizeBox = $false
$form.StartPosition = 'CenterScreen'
$form.BackColor = [Drawing.ColorTranslator]::FromHtml('#f5f8f5')
$form.Font = [Drawing.Font]::new('Microsoft YaHei UI', 10)
$form.AutoScaleMode = 'Dpi'
$paths = Get-HaiYanLauncherPaths
$iconPath = Join-Path $paths.DataRoot 'haiyan-backend.ico'
if (Test-Path -LiteralPath $iconPath) { $form.Icon = [Drawing.Icon]::new($iconPath) }

function Add-Label([string]$Text, [int]$X, [int]$Y, [int]$Width, [int]$Height) {
    $label = [Windows.Forms.Label]::new()
    $label.Text = $Text; $label.Location = [Drawing.Point]::new($X, $Y)
    $label.Size = [Drawing.Size]::new($Width, $Height)
    $label.ForeColor = [Drawing.ColorTranslator]::FromHtml('#233a31')
    $form.Controls.Add($label)
    return $label
}
function Add-Button([string]$Text, [int]$X, [int]$Y, [int]$Width, [scriptblock]$Click) {
    $button = [Windows.Forms.Button]::new()
    $button.Text = $Text; $button.Location = [Drawing.Point]::new($X, $Y)
    $button.Size = [Drawing.Size]::new($Width, 39)
    $button.FlatStyle = 'Flat'; $button.FlatAppearance.BorderColor = [Drawing.ColorTranslator]::FromHtml('#d5dfd8')
    $button.BackColor = [Drawing.Color]::White
    $button.Add_Click($Click); $form.Controls.Add($button)
    return $button
}
function Show-Error($ErrorRecord) {
    [Windows.Forms.MessageBox]::Show($form, $ErrorRecord.Exception.Message, '海燕传信后端', 'OK', 'Warning') | Out-Null
}

$title = Add-Label '海燕传信 · 后端服务' 30 24 540 40
$title.Font = [Drawing.Font]::new('Microsoft YaHei UI', 19, [Drawing.FontStyle]::Bold)
$null = Add-Label '双击桌面入口，按上次保存的配置启动。' 32 70 510 25
$null = Add-Label '监听地址' 32 119 280 24
$null = Add-Label '端口' 375 119 160 24
$addressBox = [Windows.Forms.TextBox]::new()
$addressBox.Location = [Drawing.Point]::new(32, 148); $addressBox.Size = [Drawing.Size]::new(320, 30)
$addressBox.AccessibleName = '监听地址'; $form.Controls.Add($addressBox)
$portBox = [Windows.Forms.NumericUpDown]::new()
$portBox.Location = [Drawing.Point]::new(375, 148); $portBox.Size = [Drawing.Size]::new(163, 30)
$portBox.Minimum = 1024; $portBox.Maximum = 65535; $portBox.AccessibleName = '端口'; $form.Controls.Add($portBox)
$hint = Add-Label '仅本机：127.0.0.1    全部网卡：0.0.0.0' 32 187 510 24
$hint.Font = [Drawing.Font]::new('Microsoft YaHei UI', 9)
$hint.ForeColor = [Drawing.ColorTranslator]::FromHtml('#6c7a72')
$script:statusLabel = Add-Label '准备启动…' 32 226 510 28
$script:urlLabel = Add-Label '' 32 258 510 27
$script:urlLabel.Font = [Drawing.Font]::new('Segoe UI', 10)
$script:lanLabel = Add-Label '' 32 288 540 28
$script:lanLabel.Font = [Drawing.Font]::new('Segoe UI', 10)
$script:firewallProcess = $null
$script:firewallResult = ''
$script:currentState = $null
$script:wasRunning = $false
$script:lastError = ''
$script:ready = $false
$script:probe = $null
$script:probeTask = $null
$script:observedPid = 0

function Clear-Probe {
    if ($script:probe) { $script:probe.Dispose() }
    $script:probe = $null; $script:probeTask = $null
}
function Update-Status {
    $managed = Get-HaiYanManagedProcess
    if (-not $managed) {
        Clear-Probe
        $script:ready = $false
        if ($script:wasRunning) {
            $script:lastError = '后端已退出，请查看日志。'
            $script:wasRunning = $false
        }
        $script:statusLabel.Text = $(if ($script:lastError) { $script:lastError } else { '后端未启动' })
        $script:urlLabel.Text = ''
        $script:lanLabel.Text = ''
        return
    }
    $script:currentState = $managed.State
    if ($script:observedPid -ne $managed.Process.Id) {
        Clear-Probe; $script:ready = $false; $script:observedPid = $managed.Process.Id
    }
    $script:wasRunning = $true
    $url = Get-HaiYanUrl $managed.State
    if (-not $script:ready) {
        if ($script:probeTask -and $script:probeTask.IsCompleted) {
            try {
                if (-not $script:probeTask.IsFaulted -and -not $script:probeTask.IsCanceled) {
                    $health = $script:probeTask.GetAwaiter().GetResult() | ConvertFrom-Json
                    $script:ready = $health.status -eq 'UP'
                }
            } catch { $script:ready = $false }
            Clear-Probe
        }
        if (-not $script:probeTask -and -not $script:ready) {
            $script:probe = [Net.Http.HttpClient]::new()
            $script:probe.Timeout = [TimeSpan]::FromSeconds(2)
            $script:probeTask = $script:probe.GetStringAsync($url + 'actuator/health')
        }
    }
    $script:statusLabel.Text = $(if ($script:ready) { '运行中  ·  PID ' + $managed.Process.Id } else { '启动中，请稍候…' })
    $script:urlLabel.Text = $url
    $lan = @(Get-HaiYanLanUrls $managed.State.port $managed.State.address)
    $script:lanLabel.Text = $(if ($lan.Count) { '局域网：' + ($lan -join '  ') } else { '当前仅本机可用；局域网访问请使用 0.0.0.0。' })
}
function Start-Backend {
    try {
        $script:lastError = ''; $script:ready = $false; Clear-Probe
        $script:currentState = Start-HaiYanBackend $addressBox.Text ([int]$portBox.Value)
        Update-Status
    } catch { $script:lastError = $_.Exception.Message; $script:statusLabel.Text = '启动失败'; Show-Error $_ }
}

$startButton = Add-Button '保存并启动' 32 332 160 { Start-Backend }
$startButton.BackColor = [Drawing.ColorTranslator]::FromHtml('#16785b')
$startButton.ForeColor = [Drawing.Color]::White
$startButton.FlatAppearance.BorderSize = 0
$null = Add-Button '停止后端' 205 332 160 {
    try { Stop-HaiYanBackend; $script:wasRunning = $false; $script:lastError = ''; Clear-Probe; Update-Status }
    catch { Show-Error $_ }
}
$null = Add-Button '打开网页' 378 332 160 {
    try {
        $managed = Get-HaiYanManagedProcess
        if (-not $managed) { throw '请先启动后端。' }
        if (-not $script:ready) { throw '后端正在启动，请稍候再打开网页。' }
        Start-Process (Get-HaiYanUrl $managed.State)
    } catch { Show-Error $_ }
}
$null = Add-Button '查看日志' 32 384 160 {
    try {
        if (-not $script:currentState) { throw '暂无启动日志。' }
        $state = $script:currentState
        $log = $state.stdout
        if ((Test-Path -LiteralPath $state.stderr) -and (Get-Item -LiteralPath $state.stderr).Length -gt 0) { $log = $state.stderr }
        Start-Process notepad.exe -ArgumentList ('"' + $log + '"') -WindowStyle Normal
    } catch { Show-Error $_ }
}
$null = Add-Button '放行局域网' 205 384 160 {
    try {
        if ($script:firewallProcess -and -not $script:firewallProcess.HasExited) { return }
        $port = [int]$portBox.Value
        $managed = Get-HaiYanManagedProcess
        if ($managed) { $port = [int]$managed.State.port }
        $script:firewallResult = Join-Path $paths.DataRoot ('firewall-' + $port + '.json')
        $firewallArgs = '-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "' + (Join-Path $PSScriptRoot 'Enable-LanFirewall.ps1') + '" -Port ' + $port
        $script:firewallProcess = Start-Process -FilePath (Join-Path $env:WINDIR 'System32/WindowsPowerShell/v1.0/powershell.exe') `
            -ArgumentList $firewallArgs -Verb RunAs -WindowStyle Hidden -PassThru
    } catch { Show-Error $_ }
}
$null = Add-Button '重启后端' 378 384 160 {
    try {
        Stop-HaiYanBackend
        $script:wasRunning = $false; $script:lastError = ''; Clear-Probe
        Start-Backend
    } catch { Show-Error $_ }
}
$footnote = Add-Label ('修改配置前先停止后端；首次局域网访问请点击“放行局域网”（需要管理员确认）。' + "`n" + '关闭此窗口后，后端会继续运行。') 32 439 535 46
$footnote.Font = [Drawing.Font]::new('Microsoft YaHei UI', 9)
$footnote.ForeColor = [Drawing.ColorTranslator]::FromHtml('#6c7a72')

try {
    $saved = Get-HaiYanSettings
    $addressBox.Text = $saved.address; $portBox.Value = $saved.port
} catch { $addressBox.Text = '127.0.0.1'; $portBox.Value = 8091; Show-Error $_; $ConfigureOnly = $true }
$timer = [Windows.Forms.Timer]::new(); $timer.Interval = 700
$timer.Add_Tick({
    try {
        Update-Status
        if ($script:firewallProcess -and $script:firewallProcess.HasExited) {
            $exitCode = $script:firewallProcess.ExitCode
            $script:firewallProcess.Dispose(); $script:firewallProcess = $null
            if (Test-Path -LiteralPath $script:firewallResult) {
                $result = [IO.File]::ReadAllText($script:firewallResult) | ConvertFrom-Json
                [Windows.Forms.MessageBox]::Show($form, $result.message, '海燕传信后端', 'OK', $(if ($result.success) { 'Information' } else { 'Warning' })) | Out-Null
            } elseif ($exitCode -ne 0) { throw '防火墙配置未完成，请重试。' }
        }
    } catch { $script:statusLabel.Text = '状态读取失败，请查看日志或重新打开面板。' }
})
$form.Add_Shown({
    $timer.Start()
    if (-not $ConfigureOnly) { Start-Backend } else { Update-Status }
})
$form.Add_FormClosed({ $timer.Stop(); $timer.Dispose(); Clear-Probe })
[Windows.Forms.Application]::Run($form)
