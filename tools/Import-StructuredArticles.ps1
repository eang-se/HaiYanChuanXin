param(
    [Parameter(Mandatory=$true)][string]$InputPath,
    [Parameter(Mandatory=$true)][string]$Database,
    [string]$CredentialSource = (Join-Path (Split-Path $PSScriptRoot -Parent) '.local/application-local.properties'),
    [string]$MySqlPath = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'Use-Environment.ps1')
if($Database -notmatch '^[A-Za-z0-9_]{1,64}$'){throw 'Invalid database name'}
$properties=@{}
Get-Content -LiteralPath $CredentialSource | ForEach-Object {if($_ -match '^\s*([^#!][^=:\s]*)\s*[=:]\s*(.*)$'){$properties[$matches[1]]=$matches[2]}}
if($properties['spring.datasource.url'] -notmatch '^jdbc:mysql://([^/:?]+)(?::([0-9]+))?/([A-Za-z0-9_]+)'){throw 'Invalid datasource URL'}
$dbHost=$matches[1];$dbPort=if($matches[2]){$matches[2]}else{'3306'}
if($dbHost -notin @('localhost','127.0.0.1')){throw 'Only a local MySQL server is supported'}
if(!$properties.ContainsKey('spring.datasource.username') -or !$properties.ContainsKey('spring.datasource.password')){throw 'Datasource credentials missing'}
$mysqlArgs=@("--host=$dbHost","--port=$dbPort","--user=$($properties['spring.datasource.username'])","--database=$Database",'--batch','--skip-column-names','--raw','--default-character-set=utf8mb4')
$rows=[ordered]@{}
$inputLines=if($InputPath -eq '-') {
    [Console]::InputEncoding=[Text.UTF8Encoding]::new($false)
    $lines=[Collections.Generic.List[string]]::new()
    while($null -ne ($line=[Console]::In.ReadLine())){$lines.Add($line)}
    $lines
} else {[IO.File]::ReadLines((Resolve-Path -LiteralPath $InputPath).Path,[Text.Encoding]::UTF8)}
foreach($line in $inputLines) {
    if([string]::IsNullOrWhiteSpace($line)){continue}
    $record=$line | ConvertFrom-Json -AsHashtable
    if($record.status -ne 'converted' -or $record.id -notmatch '^[wv]:[1-9][0-9]{0,17}$' -or $record.sha256 -notmatch '^[a-f0-9]{64}$' -or $record.document.type -ne 'doc'){throw 'Input contains a skipped or invalid conversion; no changes made'}
    if($rows.Contains($record.id)){throw 'Duplicate article or version ID'}
    $rows[$record.id]=$record
}
if($rows.Count -eq 0){throw 'No converted documents supplied'}
$workIds=@($rows.Keys | Where-Object {$_ -like 'w:*'} | ForEach-Object {$_.Substring(2)})
$versionIds=@($rows.Keys | Where-Object {$_ -like 'v:*'} | ForEach-Object {$_.Substring(2)})
$env:MYSQL_PWD=$properties['spring.datasource.password']
try {
    $selects=[Collections.Generic.List[string]]::new()
    if($workIds.Count){$selects.Add("SELECT HEX(JSON_OBJECT('id',CONCAT('w:',id),'body',body)) FROM works WHERE id IN ($($workIds -join ','))")}
    if($versionIds.Count){$selects.Add("SELECT HEX(JSON_OBJECT('id',CONCAT('v:',id),'body',body)) FROM work_versions WHERE id IN ($($versionIds -join ','))")}
    $encoded=(($selects -join ' UNION ALL ')+';') | & $MySqlPath @mysqlArgs
    if($LASTEXITCODE -ne 0){throw 'Could not read conversion inputs'}
    $bodies=@{}
    foreach($hex in $encoded){if($hex -notmatch '^[0-9A-Fa-f]+$'){throw 'Unexpected database result'};$entry=[Text.Encoding]::UTF8.GetString([Convert]::FromHexString($hex)) | ConvertFrom-Json -AsHashtable;$bodies[$entry.id]=$entry.body}
    if($bodies.Count -ne $rows.Count){throw 'Article or version disappeared; no changes made'}
    $validation=[Collections.Generic.List[string]]::new()
    foreach($id in $rows.Keys){
        if(!$bodies.ContainsKey($id)){throw 'Article or version disappeared; no changes made'}
        $record=$rows[$id]
        $validation.Add((ConvertTo-Json -InputObject @{body=$bodies[$id];sha256=$record.sha256;document=$record.document} -Depth 100 -Compress))
    }
    $jar=Join-Path $root 'backend/target/haiyan-chuanxin.jar'
    if(!(Test-Path -LiteralPath $jar)){throw 'Build the application JAR before validating documents'}
    $oldEncoding=$OutputEncoding;$OutputEncoding=[Text.UTF8Encoding]::new($false)
    try {$validation | & (Join-Path $env:JAVA_HOME 'bin/java.exe') '-Dloader.main=cn.haiyan.platform.service.DocumentMigrationValidator' -cp $jar 'org.springframework.boot.loader.launch.PropertiesLauncher';if($LASTEXITCODE -ne 0){throw 'Backend document validation failed; no changes made'}}finally{$OutputEncoding=$oldEncoding}

    $layout="CASE WHEN w.body_layout REGEXP '^(left|center),(normal|relaxed|airy),(compact|normal|loose),(left|center),(true|false)$' THEN JSON_OBJECT('bodyAlign',SUBSTRING_INDEX(w.body_layout,',',1),'lineSpacing',SUBSTRING_INDEX(SUBSTRING_INDEX(w.body_layout,',',2),',',-1),'paragraphSpacing',SUBSTRING_INDEX(SUBSTRING_INDEX(w.body_layout,',',3),',',-1),'headingAlign',SUBSTRING_INDEX(SUBSTRING_INDEX(w.body_layout,',',4),',',-1),'indent',IF(SUBSTRING_INDEX(w.body_layout,',',1)='left' AND SUBSTRING_INDEX(w.body_layout,',',-1)='true',JSON_EXTRACT('true','$'),JSON_EXTRACT('false','$'))) ELSE JSON_OBJECT('bodyAlign','left','lineSpacing',CASE WHEN w.genre='诗歌' THEN 'airy' WHEN w.genre='随笔' THEN 'normal' ELSE 'relaxed' END,'paragraphSpacing',CASE WHEN w.genre='诗歌' THEN 'compact' WHEN w.genre='散文' THEN 'loose' ELSE 'normal' END,'headingAlign',IF(w.genre='小说','center','left'),'indent',IF(w.genre='小说',JSON_EXTRACT('true','$'),JSON_EXTRACT('false','$'))) END"
    $sql=[Collections.Generic.List[string]]::new()
    $sql.Add('START TRANSACTION;')
    if($workIds.Count){$sql.Add("SELECT id FROM works WHERE id IN ($($workIds -join ',')) ORDER BY id FOR UPDATE;")}
    if($versionIds.Count){$sql.Add("SELECT id FROM work_versions WHERE id IN ($($versionIds -join ',')) ORDER BY id FOR UPDATE;")}
    foreach($id in $rows.Keys){
        $number=$id.Substring(2);$sha=$rows[$id].sha256
        # The false branch raises a MySQL JSON parse error regardless of SQL mode.
        # mysql stops on that error; disconnecting rolls the transaction back.
        if($id[0] -eq 'w'){$sql.Add("SELECT IF((SELECT COUNT(*) FROM works w WHERE w.id=$number AND SHA2(w.body,256)='$sha' AND NOT EXISTS(SELECT 1 FROM work_drafts d WHERE d.work_id=w.id))=1,1,JSON_EXTRACT('invalid-json','$'));")}
        else {$sql.Add("SELECT IF((SELECT COUNT(*) FROM work_versions v WHERE v.id=$number AND SHA2(v.body,256)='$sha' AND v.body_json IS NULL)=1,1,JSON_EXTRACT('invalid-json','$'));")}
    }
    foreach($id in $rows.Keys){
        $number=$id.Substring(2);$json=ConvertTo-Json -InputObject $rows[$id].document -Depth 100 -Compress
        $hex=[Convert]::ToHexString([Text.Encoding]::UTF8.GetBytes($json));$doc="CAST(CONVERT(UNHEX('$hex') USING utf8mb4) AS JSON)"
        if($id[0] -eq 'w'){
            $payload="JSON_OBJECT('title',w.title,'genre',w.genre,'excerpt',w.excerpt,'body',w.body,'cover',w.cover,'tagIds',COALESCE((SELECT JSON_ARRAYAGG(t.id) FROM work_tags wt JOIN tags t ON t.id=wt.tag_id WHERE wt.work_id=w.id AND t.kind='TOPIC' AND t.name<>'海燕精选'),JSON_ARRAY()),'byline',w.byline,'sourceNote',w.source_note,'notes',w.notes,'customGenre',w.custom_genre,'layout',$layout,'collectionId',w.collection_id,'penNameId',w.pen_name_id,'bodyJson',$doc)"
            $sql.Add("INSERT INTO work_drafts(work_id,payload,revision) SELECT w.id,$payload,w.edit_revision FROM works w WHERE w.id=$number;")
            $sql.Add("INSERT INTO work_draft_media(work_id,media_id) SELECT work_id,media_id FROM work_media WHERE work_id=$number;")
        } else {$sql.Add("UPDATE work_versions SET body_json=CONVERT(UNHEX('$hex') USING utf8mb4) WHERE id=$number AND body_json IS NULL;")}
    }
    $sql.Add('COMMIT;')
    $null=($sql -join "`n") | & $MySqlPath @mysqlArgs
    if($LASTEXITCODE -ne 0){throw 'Transaction failed or source changed; no changes committed'}
    Write-Output "Converted $($workIds.Count) article drafts and $($versionIds.Count) historical versions."
} finally {Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue}
