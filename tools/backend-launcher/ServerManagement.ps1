$script:ManagementScriptRoot = $PSScriptRoot
. (Join-Path $script:ManagementScriptRoot 'ManagementCore.ps1')
function Show-ServerManagement {
    $paths = Get-HaiYanLauncherPaths
    $configPath = Join-Path $paths.DataRoot 'cloud-management.json'
    $window = [Windows.Forms.Form]::new()
    $window.Text = '海燕传信 · 服务器管理'; $window.ClientSize = [Drawing.Size]::new(760, 625)
    $window.StartPosition = 'CenterParent'; $window.Font = [Drawing.Font]::new('Microsoft YaHei UI', 9)
    $window.AutoScaleMode = 'Dpi'; $window.MinimizeBox = $false
    $tabs = [Windows.Forms.TabControl]::new(); $tabs.Dock = 'Fill'; $window.Controls.Add($tabs)
    $cloud = [Windows.Forms.TabPage]::new('云服务器'); $roster = [Windows.Forms.TabPage]::new('QQ 名单'); $tabs.TabPages.Add($cloud); $tabs.TabPages.Add($roster)
    function LabelAt($parent, $text, $x, $y, $w) {
        $label = [Windows.Forms.Label]::new(); $label.Text = $text; $label.SetBounds($x,$y,$w,24); $parent.Controls.Add($label); return $label
    }
    function TextAt($parent, $x, $y, $w, $value) {
        $box = [Windows.Forms.TextBox]::new(); $box.SetBounds($x,$y,$w,28); $box.Text = $value; $parent.Controls.Add($box); return $box
    }
    function ButtonAt($parent, $text, $x, $y, $w, $action) {
        $button = [Windows.Forms.Button]::new(); $button.Text=$text; $button.SetBounds($x,$y,$w,36); $button.Add_Click($action); $parent.Controls.Add($button); return $button
    }
    $null = LabelAt $cloud 'SSH 服务器地址' 20 22 140
    $hostBox = TextAt $cloud 160 19 240 ''
    $null = LabelAt $cloud '端口' 415 22 40
    $portBox = TextAt $cloud 460 19 80 '22'
    $null = LabelAt $cloud 'SSH 用户' 20 62 140
    $userBox = TextAt $cloud 160 59 240 ''
    $null = LabelAt $cloud '私钥路径（可留空）' 20 102 140
    $keyBox = TextAt $cloud 160 99 450 ''
    $null = ButtonAt $cloud '选择' 620 95 80 { $dialog=[Windows.Forms.OpenFileDialog]::new(); if($dialog.ShowDialog($window) -eq 'OK'){$keyBox.Text=$dialog.FileName}; $dialog.Dispose() }
    $null = LabelAt $cloud 'systemd 服务名' 20 142 140
    $serviceBox = TextAt $cloud 160 139 240 'haiyan'
    $null = LabelAt $cloud '云端名单目录' 20 182 140
    $dirBox = TextAt $cloud 160 179 450 '/var/lib/haiyan/QQ_Whitelist'
    $null = LabelAt $cloud '自定义远程命令' 20 222 140
    $commandBox = TextAt $cloud 160 219 450 ''
    $null = LabelAt $cloud '先保存配置。SSH 使用系统的 known_hosts；首次连接请在终端确认主机指纹。' 20 261 680
    $null = LabelAt $cloud '启停需要远端用户具有相应 sudo 权限；命令在交互终端执行。' 20 286 680
    function Save-Cloud {
        $config = [pscustomobject]@{hostName=$hostBox.Text.Trim();port=$portBox.Text.Trim();user=$userBox.Text.Trim();keyPath=$keyBox.Text.Trim();service=$serviceBox.Text.Trim();rosterDir=$dirBox.Text.Trim();customCommand=$commandBox.Text}
        Test-CloudSettings $config
        if ($config.keyPath -and !(Test-Path -LiteralPath $config.keyPath -PathType Leaf)) { throw 'SSH 私钥文件不存在。' }
        Write-HaiYanJson $configPath $config
    }
    function Run-Cloud([string]$operation, [string]$source = '') {
        try {
            Save-Cloud
            $worker = Join-Path $script:ManagementScriptRoot 'CloudWorker.ps1'
            $args = '-NoProfile -ExecutionPolicy Bypass -File "' + $worker + '" -Operation ' + $operation + ' -ConfigPath "' + $configPath + '"'
            if ($source) { $args += ' -RosterPath "' + $source + '"' }
            Start-Process -FilePath (Join-Path $env:WINDIR 'System32/WindowsPowerShell/v1.0/powershell.exe') -ArgumentList $args -WindowStyle Normal
        } catch { [Windows.Forms.MessageBox]::Show($window, $_.Exception.Message, '服务器管理', 'OK', 'Warning') | Out-Null }
    }
    $null = ButtonAt $cloud '保存配置' 20 330 120 { try { Save-Cloud; [Windows.Forms.MessageBox]::Show($window,'配置已保存。','服务器管理') | Out-Null } catch { [Windows.Forms.MessageBox]::Show($window,$_.Exception.Message,'服务器管理','OK','Warning') | Out-Null } }
    $null = ButtonAt $cloud '查看状态' 150 330 120 { Run-Cloud 'status' }
    $null = ButtonAt $cloud '查看日志' 280 330 120 { Run-Cloud 'logs' }
    $null = ButtonAt $cloud '启动' 20 380 120 { Run-Cloud 'start' }
    $null = ButtonAt $cloud '停止' 150 380 120 { Run-Cloud 'stop' }
    $null = ButtonAt $cloud '重启' 280 380 120 { Run-Cloud 'restart' }
    $null = ButtonAt $cloud '运行自定义命令' 410 380 160 { Run-Cloud 'custom' }
    $null = LabelAt $cloud '自定义命令由服务器的远程 shell 执行，请确认内容和目标。' 20 430 680
    $null = LabelAt $roster '选择一个 UTF-8 Markdown 文件：文件名即群号，每行“QQ号 名称”。' 20 24 680
    $fileBox = TextAt $roster 20 58 560 ''
    $null = ButtonAt $roster '选择文件' 595 54 110 { $dialog=[Windows.Forms.OpenFileDialog]::new(); $dialog.Filter='Markdown 名单 (*.md)|*.md'; if($dialog.ShowDialog($window) -eq 'OK'){$fileBox.Text=$dialog.FileName}; $dialog.Dispose() }
    $null = LabelAt $roster '更新会替换同名群名单并保留旧文件备份；不会更改其他群名单。' 20 108 680
    $null = LabelAt $roster '名单文件更新后立即生效；已停用 QQ 的注册资格仍由网站后台控制。' 20 136 680
    $null = ButtonAt $roster '更新本地名单' 20 185 150 {
        try { $count=Update-LocalRoster $fileBox.Text (Join-Path $paths.ProjectRoot 'QQ_Whitelist'); [Windows.Forms.MessageBox]::Show($window,('本地名单已更新：' + $count + ' 名成员。'),'QQ 名单') | Out-Null }
        catch { [Windows.Forms.MessageBox]::Show($window,$_.Exception.Message,'QQ 名单','OK','Warning') | Out-Null }
    }
    $null = ButtonAt $roster '上传到云端' 185 185 150 { try { $null=Test-Roster $fileBox.Text; Run-Cloud 'upload' $fileBox.Text } catch { [Windows.Forms.MessageBox]::Show($window,$_.Exception.Message,'QQ 名单','OK','Warning') | Out-Null } }
    $null = ButtonAt $roster '打开本地名单目录' 350 185 170 { Start-Process explorer.exe -ArgumentList ('"' + (Join-Path $paths.ProjectRoot 'QQ_Whitelist') + '"') }
    if (Test-Path -LiteralPath $configPath) {
        try { $c=Get-Content -LiteralPath $configPath -Raw -Encoding UTF8 | ConvertFrom-Json; $hostBox.Text=$c.hostName; $portBox.Text=$c.port; $userBox.Text=$c.user; $keyBox.Text=$c.keyPath; $serviceBox.Text=$c.service; $dirBox.Text=$c.rosterDir; $commandBox.Text=$c.customCommand }
        catch { [Windows.Forms.MessageBox]::Show($window,('云端配置读取失败：' + $_.Exception.Message),'服务器管理','OK','Warning') | Out-Null }
    }
    [void]$window.ShowDialog($form); $window.Dispose()
}
