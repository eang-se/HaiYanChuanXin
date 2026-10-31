param([Parameter(Mandatory=$true)][string]$Password,[string]$Actor='YuI',[string]$OtherActor='')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$base='http://127.0.0.1:8091/api'
$runId=[guid]::NewGuid().ToString('N').Substring(0,12)
$alias='关系验证_'+$runId
$workIds=[Collections.Generic.List[long]]::new();$groupIds=[Collections.Generic.List[long]]::new()
$penId=0L;$a=$null;$b=$null;$temporaryOther=$false
function New-Client([string]$Username){
 $session=New-Object Microsoft.PowerShell.Commands.WebRequestSession
 $csrf=Invoke-RestMethod "$base/auth/csrf" -WebSession $session
 $body=@{username=$Username;password=$Password}|ConvertTo-Json -Compress
 $me=Invoke-RestMethod "$base/auth/login" -Method Post -WebSession $session -Headers @{'X-CSRF-TOKEN'=$csrf.token} -ContentType 'application/json' -Body $body
 $csrf=Invoke-RestMethod "$base/auth/csrf" -WebSession $session
 return @{Session=$session;Token=$csrf.token;Id=[long]$me.id}
}
function Call-Api($Client,[string]$Method,[string]$Path,$Body=$null,[int]$Expected=200){
 $args=@{Uri=$base+$Path;Method=$Method;SkipHttpErrorCheck=$true}
 if($Client){$args.WebSession=$Client.Session;$args.Headers=@{'X-CSRF-TOKEN'=$Client.Token}}
 if($null-ne$Body){$args.ContentType='application/json';$args.Body=ConvertTo-Json -InputObject $Body -Depth 12 -Compress}
 $r=Invoke-WebRequest @args
 if([int]$r.StatusCode-ne$Expected){throw "$Method $Path expected $Expected, got $($r.StatusCode): $($r.Content)"}
 if($r.Content -and $r.Headers['Content-Type'] -like '*json*'){return $r.Content|ConvertFrom-Json}
}
function Run-Sql([string]$Sql){
 $settings=@{};Get-Content (Join-Path $projectRoot '.local/application-local.properties')|ForEach-Object{if($_ -match '^([^#=]+)=(.*)$'){$settings[$matches[1]]=$matches[2]}}
 $env:MYSQL_PWD=$settings['spring.datasource.password']
 try{$Sql|& 'C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe' --host=127.0.0.1 "--user=$($settings['spring.datasource.username'])" --database=haiyan_chuanxin --default-character-set=utf8mb4;if($LASTEXITCODE-ne0){throw 'Temporary account setup failed'}}finally{Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue}
}
function Assert-True($Value,[string]$Message){if(!$Value){throw $Message}}
try{
 $a=New-Client $Actor
 if(!$OtherActor){$OtherActor='relations_'+$runId;Run-Sql "INSERT INTO members(username,password_hash,pen_name,role,account_type,profile_completed,profile_prompt_seen) SELECT '$OtherActor',password_hash,'功能验证临时账号','MEMBER','PERSONAL',TRUE,TRUE FROM members WHERE id=$($a.Id);";$temporaryOther=$true}
 $b=New-Client $OtherActor
 $pen=Call-Api $a POST '/member/pen-names' @{name=$alias};$penId=[long]$pen.id
 $bound=Call-Api $b POST '/member/pen-names' @{name=$alias};Assert-True ($bound.id-eq$penId) 'Same pen name was not shared as one identity'
 foreach($suffix in @('A','B')){$g=Call-Api $a POST '/member/collections' @{title=('关系验证_'+$runId+'_'+$suffix);description='自动测试，结束后清理';kind='选集';published=$false;workIds=@()};$groupIds.Add([long]$g.id)}
 $payload=@{title='关系验证_'+$runId;genre='诗歌';body='<p>私密测试稿，结束后清理。</p>';tagIds=@();collectionId=$groupIds[0];penNameId=$penId;publish=$false}
 $one=Call-Api $a POST '/member/works' $payload;$workIds.Add([long]$one.id)
 $two=Call-Api $a POST '/member/works' $payload;$workIds.Add([long]$two.id)
 [void](Call-Api $b PUT "/member/works/$($one.id)" $payload 404)
 [void](Call-Api $b POST '/member/works' $payload 403)
 [void](Call-Api $a PUT "/member/collections/$($groupIds[1])" @{title=('关系验证_'+$runId+'_B');kind='选集';published=$false;workIds=@($one.id)} 400)
 $first=Call-Api $a GET "/public/collections/$($groupIds[0])";Assert-True ($first.works.Count-eq2) 'Rejected reassignment changed original directory'
 $payload.collectionId=$groupIds[1]
 [void](Call-Api $a PUT "/member/works/$($one.id)" $payload)
 $first=Call-Api $a GET "/public/collections/$($groupIds[0])";Assert-True ($first.works.Count-eq1) 'Changing collection did not replace the old membership'
 [void](Call-Api $a PUT "/member/works/$($two.id)" $payload)
 [void](Call-Api $a PUT "/member/collections/$($groupIds[1])" @{title=('关系验证_'+$runId+'_B');kind='选集';published=$false;workIds=@($two.id,$one.id)})
 [void](Call-Api $a PUT "/member/works/$($one.id)" $payload)
 $ordered=Call-Api $a GET "/public/collections/$($groupIds[1])";Assert-True ($ordered.works[0].id-eq$two.id -and $ordered.works[1].id-eq$one.id) 'Saving an article lost manual order'
 $payload.collectionId=0;[void](Call-Api $a PUT "/member/works/$($one.id)" $payload)
 $detached=Call-Api $a GET "/public/works/$($one.id)";Assert-True (!$detached.collection -and !$detached.collectionId -and $detached.penNameId-eq$penId) 'Unbinding collection changed the pen name'
 [void](Call-Api $a DELETE "/member/pen-names/$penId" $null 400)
 [void](Call-Api $null GET "/public/pen-names/$penId" $null 404)
 [void](Call-Api $null GET "/public/works/$($one.id)" $null 404)
 $payload.collectionTags=@('旧合集标签');[void](Call-Api $a PUT "/member/works/$($one.id)" $payload 400)
 $article=Call-Api $null GET '/public/works/22';Assert-True ($article.collection.id-eq9 -and $article.penNameWorkCount-ge2 -and @($article.tags|Where-Object kind -eq 'COLLECTION').Count-eq0) 'Yearbook relationship migration failed'
 $profile=Call-Api $null GET "/public/pen-names/$($article.penNameId)"; $works=Call-Api $null GET "/public/works?penName=$($article.penNameId)"
 Assert-True ($profile.name-eq'胡杰' -and $works.total-eq$profile.workCount -and $works.total-eq6) 'Pen name public directory mismatch'
 Write-Output 'PASS: single collection, replacement, append/reorder stability, ownership, pen binding, private drafts, legacy tag rejection and Hu Jie public profile.'
} finally {
 $p=@{};Get-Content (Join-Path $projectRoot '.local/application-local.properties')|ForEach-Object{if($_ -match '^([^#=]+)=(.*)$'){$p[$matches[1]]=$matches[2]}}
 $sql='START TRANSACTION;'
 if($workIds.Count){$ids=$workIds -join ',';$sql+="DELETE FROM work_tags WHERE work_id IN ($ids);DELETE FROM work_media WHERE work_id IN ($ids);DELETE FROM reactions WHERE work_id IN ($ids);DELETE FROM comments WHERE work_id IN ($ids);DELETE FROM works WHERE id IN ($ids) AND author_id=$($a.Id);"}
 if($groupIds.Count){$ids=$groupIds -join ',';$sql+="DELETE FROM collection_works WHERE collection_id IN ($ids);DELETE FROM collections WHERE id IN ($ids) AND author_id=$($a.Id);"}
 if($penId -gt 0){$sql+="DELETE FROM member_pen_names WHERE pen_name_id=$penId AND member_id IN ($($a.Id),$($b.Id));DELETE FROM pen_names WHERE id=$penId AND name='$alias';"}
 if($temporaryOther -and $OtherActor -match '^relations_[a-f0-9]{12}$'){
  $owner="(SELECT id FROM members WHERE username='$OtherActor')";$ownedWorks="(SELECT id FROM works WHERE author_id IN $owner)"
  $sql+="DELETE FROM work_tags WHERE work_id IN $ownedWorks;DELETE FROM work_media WHERE work_id IN $ownedWorks;DELETE FROM reactions WHERE work_id IN $ownedWorks;DELETE FROM comments WHERE work_id IN $ownedWorks;DELETE FROM collection_works WHERE work_id IN $ownedWorks;DELETE FROM works WHERE author_id IN $owner;DELETE FROM collections WHERE author_id IN $owner;DELETE FROM member_pen_names WHERE member_id IN $owner;DELETE FROM member_permissions WHERE member_id IN $owner;DELETE FROM members WHERE username='$OtherActor';"
 }
 $sql+='COMMIT;';$env:MYSQL_PWD=$p['spring.datasource.password']
 try{$sql|& 'C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe' --host=127.0.0.1 "--user=$($p['spring.datasource.username'])" --database=haiyan_chuanxin --default-character-set=utf8mb4;if($LASTEXITCODE-ne0){throw 'Test cleanup failed'}}finally{Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue}
 foreach($client in @($a,$b)){if($client){try{[void](Call-Api $client POST '/auth/logout')}catch{}}}
}
