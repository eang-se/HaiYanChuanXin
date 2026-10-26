param([Parameter(Mandatory=$true)][ValidateRange(1,65535)][int]$Port)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'BackendLauncher.psm1') -Force
Initialize-HaiYanLauncher -ProjectRoot (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent)
$resultFile = Join-Path (Get-HaiYanLauncherPaths).DataRoot ('firewall-' + $Port + '.json')
try {
    $principal = [Security.Principal.WindowsPrincipal]::new([Security.Principal.WindowsIdentity]::GetCurrent())
    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) { throw '需要管理员权限才能添加 Windows 防火墙规则。' }
    $name = 'HaiYanChuanXin-LAN-TCP-' + $Port
    $java = Get-HaiYanJava
    $rule = Get-NetFirewallRule -Name $name -ErrorAction SilentlyContinue
    if ($rule) {
        $portFilter = $rule | Get-NetFirewallPortFilter
        $addressFilter = $rule | Get-NetFirewallAddressFilter
        $programFilter = $rule | Get-NetFirewallApplicationFilter
        if ($rule.Direction -ne 'Inbound' -or $rule.Action -ne 'Allow' -or $portFilter.LocalPort -ne [string]$Port -or
            $addressFilter.RemoteAddress -ne 'LocalSubnet' -or $programFilter.Program -ne $java) {
            throw '已有同名防火墙规则的配置不一致，请检查 HaiYanChuanXin-LAN-TCP 规则。'
        }
        Enable-NetFirewallRule -Name $name | Out-Null
    } else {
        New-NetFirewallRule -Name $name -DisplayName ('海燕传信后端 · 局域网 TCP ' + $Port) -Group 'HaiYanChuanXin Backend' `
            -Direction Inbound -Action Allow -Enabled True -Profile Private -Protocol TCP -LocalPort $Port `
            -RemoteAddress LocalSubnet -Program $java -EdgeTraversalPolicy Block | Out-Null
    }
    $result = @{ success=$true; message=('已允许同一局域网设备访问 TCP ' + $Port + '。') }
} catch { $result = @{ success=$false; message=$_.Exception.Message } }
[IO.File]::WriteAllText($resultFile, ($result | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
if (-not $result.success) { exit 1 }
