param([Parameter(Mandatory=$true)][string]$Operation, [Parameter(Mandatory=$true)][string]$ConfigPath, [string]$RosterPath)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'ManagementCore.ps1')
try {
    $c = Get-Content -LiteralPath $ConfigPath -Raw -Encoding UTF8 | ConvertFrom-Json
    Test-CloudSettings $c
    $target = $c.user + '@' + $c.hostName
    $sshArgs = @('-o','StrictHostKeyChecking=yes','-o','ConnectTimeout=10','-p',[string]$c.port)
    if ($c.keyPath) { $sshArgs += @('-i',[string]$c.keyPath) }
    if ($Operation -eq 'upload') {
        $count = Test-Roster $RosterPath
        $dir = $c.rosterDir.TrimEnd('/'); $name = [IO.Path]::GetFileName($RosterPath)
        $temp = $dir + '/.upload-' + [guid]::NewGuid().ToString('N')
        $scpArgs = @('-o','StrictHostKeyChecking=yes','-o','ConnectTimeout=10','-P',[string]$c.port)
        if ($c.keyPath) { $scpArgs += @('-i',[string]$c.keyPath) }
        & scp.exe @scpArgs -- $RosterPath ($target + ':' + $temp)
        if ($LASTEXITCODE -ne 0) { throw '上传失败，原名单未更改。' }
        $destination = $dir + '/' + $name
        $backup = $destination + '.backup-' + [guid]::NewGuid().ToString('N')
        $command = "if [ -e '$destination' ]; then cp -p -- '$destination' '$backup' || exit 1; fi; mv -f -- '$temp' '$destination'"
        & ssh.exe @sshArgs $target $command
        if ($LASTEXITCODE -ne 0) { throw ('更新失败；上传的临时文件可能留在 ' + $temp) }
        Write-Host ("更新成功：$destination，$count 名成员。已有文件的备份保存在同一目录。")
    } else {
        $command = switch ($Operation) {
            'status' { 'systemctl status ' + $c.service + ' --no-pager' }
            'logs' { 'journalctl -u ' + $c.service + ' -n 100 --no-pager' }
            'start' { 'sudo systemctl start ' + $c.service }
            'stop' { 'sudo systemctl stop ' + $c.service }
            'restart' { 'sudo systemctl restart ' + $c.service }
            'custom' { [string]$c.customCommand }
            default { throw '未知操作。' }
        }
        if (!$command.Trim()) { throw '请先填写自定义命令。' }
        Write-Host ('目标：' + $target + '  操作：' + $Operation)
        & ssh.exe -tt @sshArgs $target $command
        if ($LASTEXITCODE -ne 0) { throw ('远程命令退出代码：' + $LASTEXITCODE) }
        Write-Host '操作完成。'
    }
} catch { Write-Host ('操作失败：' + $_.Exception.Message) -ForegroundColor Red }
Read-Host '按 Enter 关闭窗口' | Out-Null
