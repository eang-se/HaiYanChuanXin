Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'BackendLauncher.psm1') -Force
$project = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Initialize-HaiYanLauncher -ProjectRoot $project
$paths = Get-HaiYanLauncherPaths
$launcher = Join-Path $PSScriptRoot 'HaiYanBackend.ps1'
$executable = Join-Path $PSScriptRoot 'HaiYanBackend.exe'
if (-not (Test-Path -LiteralPath $executable) -or (Get-Item -LiteralPath (Join-Path $PSScriptRoot 'HaiYanBackendHost.cs')).LastWriteTime -gt (Get-Item -LiteralPath $executable).LastWriteTime) {
    $buildFile = Join-Path $PSScriptRoot ('HaiYanBackend-build-' + [Guid]::NewGuid().ToString('N') + '.exe')
    try {
        Add-Type -Path (Join-Path $PSScriptRoot 'HaiYanBackendHost.cs') -OutputAssembly $buildFile -OutputType WindowsApplication `
            -ReferencedAssemblies @('System.dll','System.Core.dll','System.Windows.Forms.dll',[Management.Automation.PSObject].Assembly.Location)
        Move-Item -LiteralPath $buildFile -Destination $executable -Force
    } finally { if (Test-Path -LiteralPath $buildFile) { Remove-Item -LiteralPath $buildFile } }
}
$desktop = [Environment]::GetFolderPath('Desktop')
$shortcutPath = Join-Path $desktop '海燕传信后端启动.lnk'
$shell = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut($shortcutPath)
if ((Test-Path -LiteralPath $shortcutPath) -and $shortcut.Arguments -notlike ('*' + $launcher + '*') -and $shortcut.TargetPath -ne $executable -and $shortcut.Arguments -notlike ('*' + (Join-Path $project 'tools/Start-Local.ps1') + '*')) {
    throw '桌面已有同名入口且不属于此启动器，请先为它改名。'
}

Add-Type -AssemblyName System.Drawing
$iconPath = Join-Path $paths.DataRoot 'haiyan-backend.ico'
$bitmap = [Drawing.Bitmap]::new(64,64)
$graphics = [Drawing.Graphics]::FromImage($bitmap)
$graphics.SmoothingMode = 'AntiAlias'
$graphics.Clear([Drawing.ColorTranslator]::FromHtml('#245747'))
$font = [Drawing.Font]::new('Segoe UI', 39, [Drawing.FontStyle]::Bold, [Drawing.GraphicsUnit]::Pixel)
$format = [Drawing.StringFormat]::new()
$format.Alignment = 'Center'; $format.LineAlignment = 'Center'
$graphics.DrawString('H', $font, [Drawing.Brushes]::White, [Drawing.RectangleF]::new(0,0,64,64), $format)
$icon = [Drawing.Icon]::FromHandle($bitmap.GetHicon())
$stream = [IO.File]::Create($iconPath)
try { $icon.Save($stream) }
finally { $stream.Dispose(); $icon.Dispose(); $graphics.Dispose(); $bitmap.Dispose(); $font.Dispose(); $format.Dispose() }

$shortcut.TargetPath = $executable
$shortcut.Arguments = ''
$shortcut.WorkingDirectory = $project
$shortcut.Description = '海燕传信后端管理：监听地址、端口、启动停止、网页、日志及局域网。'
$shortcut.IconLocation = $iconPath + ',0'
$shortcut.Save()
Write-Output ('已创建：' + $shortcutPath)
