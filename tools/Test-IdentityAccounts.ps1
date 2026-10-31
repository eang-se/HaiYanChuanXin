$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$base='http://127.0.0.1:8091/api'
$runId=[guid]::NewGuid().ToString('N').Substring(0,10)
$publicName='identity_pub_'+$runId
$batchTitle='identity_batch_'+$runId
$batchFile=Join-Path $root ('.runtime/'+$batchTitle+'.txt')
$names=@(0..5 | ForEach-Object {'identity_'+$runId+'_'+$_})
$password=[guid]::NewGuid().ToString('N')
$props=@{}
Get-Content -LiteralPath (Join-Path $root '.local/application-local.properties') | ForEach-Object {if($_ -match '^([^#=]+)=(.*)$'){$props[$matches[1]]=$matches[2]}}
function Sql([string]$query){
 $env:MYSQL_PWD=$props['spring.datasource.password']
 try {$output=& 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' --host=127.0.0.1 --user=haiyan_app --database=haiyan_chuanxin --batch --skip-column-names --default-character-set=utf8mb4 ('--execute='+$query);if($LASTEXITCODE-ne0){throw 'Test database operation failed'};return $output} finally {Remove-Item Env:MYSQL_PWD}
}
function New-Client {$session=New-Object Microsoft.PowerShell.Commands.WebRequestSession;$csrf=Invoke-RestMethod "$base/auth/csrf" -WebSession $session;return @{Session=$session;Token=$csrf.token}}
function Call-Api($client,[string]$method,[string]$path,$body=$null,[int]$expected=200){
 $args=@{Uri=$base+$path;Method=$method;WebSession=$client.Session;Headers=@{'X-CSRF-TOKEN'=$client.Token};SkipHttpErrorCheck=$true}
 if($null-ne$body){$args.ContentType='application/json';$args.Body=ConvertTo-Json -InputObject $body -Depth 10 -Compress}
 $response=Invoke-WebRequest @args
 if([int]$response.StatusCode-ne$expected){throw "$method $path expected $expected, got $($response.StatusCode): $($response.Content)"}
 if($response.Content -and $response.Headers['Content-Type'] -like '*json*'){return $response.Content|ConvertFrom-Json}
}
function Assert-True($condition,[string]$message){if(!$condition){throw $message}}
$jobs=@();$clients=@();$createdPublic=$false
try {
 $options=Invoke-RestMethod "$base/auth/registration-options"
 if(!$options.enabled -or !$options.testMode){throw 'Identity test needs open test registration; settings were not changed.'}
 [void](Sql "INSERT INTO members(username,password_hash,pen_name,role,account_type,profile_completed,profile_prompt_seen) VALUES('$publicName','!PUBLIC_IDENTITY_DISABLED!','临时身份','MEMBER','PUBLIC',TRUE,TRUE)")
 $createdPublic=$true
 $publicId=[long](Sql "SELECT id FROM members WHERE username='$publicName' AND account_type='PUBLIC'")
 for($i=0;$i-lt6;$i++){
  $client=New-Client;$clients+=,$client
  [void](Call-Api $client POST '/auth/register' @{username=$names[$i];password=$password;qq='12345';code='0000'})
  [void](Call-Api $client POST '/auth/login' @{username=$names[$i];password=$password})
 }
 $nameList=($names | ForEach-Object {"'$_'"}) -join ','
 [void](Sql "INSERT INTO public_account_members(public_member_id,member_id) SELECT $publicId,id FROM members WHERE username IN ($nameList) AND account_type='PERSONAL'")
 $guest=New-Client
 [void](Call-Api $guest POST '/auth/login' @{username=$publicName;password=$password} 401)
 $gate=[Threading.ManualResetEventSlim]::new($false)
 for($i=0;$i-lt6;$i++){
  $jobs+=Start-ThreadJob -ArgumentList @($base,$clients[$i].Session,$clients[$i].Token,$publicId,$gate,$i) -ScriptBlock {
   param($baseUri,$session,$token,$id,$startGate,$index)
   $startGate.Wait()
   $response=Invoke-WebRequest ($baseUri+'/auth/identity') -Method Post -WebSession $session -Headers @{'X-CSRF-TOKEN'=$token} -ContentType 'application/json' -Body (ConvertTo-Json -Compress @{id=$id}) -SkipHttpErrorCheck -TimeoutSec 30
   return @{index=$index;status=[int]$response.StatusCode}
  }
 }
 $gate.Set()
 $finished=@($jobs | Wait-Job -Timeout 35);Assert-True ($finished.Count-eq6) 'Concurrent identity switches did not finish'
 $results=@($jobs | Receive-Job);$ok=@($results|Where-Object {$_.status-eq200});$blocked=@($results|Where-Object {$_.status-eq409})
 Assert-True ($ok.Count-eq5 -and $blocked.Count-eq1) ('Expected five successes and one limit rejection; got '+(($results|ForEach-Object {$_.status}) -join ','))
 $active=[int]$ok[0].index;$blockedIndex=[int]$blocked[0].index
 $me=Call-Api $clients[$active] GET '/auth/me'
 Assert-True ($me.id-eq$publicId -and $me.accountType-eq'PUBLIC') 'Shared identity was not active'
 [void](Call-Api $clients[$active] PUT '/member/profile' @{penName='不可修改'} 403)
 [void](Call-Api $clients[$active] GET '/admin/settings' $null 403)
 [void](Sql "UPDATE members SET role='RESTRICTED' WHERE username='$($names[$active])'")
 [void](Call-Api $clients[$active] POST '/member/works' @{title='不可发布';body='<p>测试</p>';publish=$false} 403)
 [void](Sql "DELETE pam FROM public_account_members pam JOIN members p ON p.id=pam.public_member_id JOIN members a ON a.id=pam.member_id WHERE p.username='$publicName' AND a.username='$($names[$active])'")
 $after=Call-Api $clients[$active] GET '/auth/me'
 Assert-True ($after.username-eq$names[$active] -and $after.accountType-eq'PERSONAL') 'Revocation did not restore personal identity'
 [void](Call-Api $clients[$blockedIndex] POST '/auth/identity' @{id=$publicId})
 $selectedName=$names[$blockedIndex]
 [void](Sql "INSERT INTO public_account_members(public_member_id,member_id) SELECT p.id,a.id FROM members p JOIN members a ON a.username='$selectedName' WHERE p.username IN ('haiyan_editor','haiyan_activity','haiyan_tech') AND p.account_type='PUBLIC'")
 $editorId=[long](Sql "SELECT id FROM members WHERE username='haiyan_editor' AND account_type='PUBLIC'")
 $activityId=[long](Sql "SELECT id FROM members WHERE username='haiyan_activity' AND account_type='PUBLIC'")
 $techId=[long](Sql "SELECT id FROM members WHERE username='haiyan_tech' AND account_type='PUBLIC'")
 [void](Call-Api $clients[$blockedIndex] POST '/auth/identity' @{id=$editorId})
 $review=Call-Api $clients[$blockedIndex] GET '/admin/overview'
 Assert-True (@($review.members).Count-eq0 -and @($review.works).Count-gt0) 'Editor workspace should expose works but not member records'
 [IO.File]::WriteAllText($batchFile,"Sample draft text",[Text.UTF8Encoding]::new($false))
 $imported=Invoke-RestMethod "$base/member/import" -Method Post -WebSession $clients[$blockedIndex].Session -Headers @{'X-CSRF-TOKEN'=$clients[$blockedIndex].Token} -Form @{file=Get-Item -LiteralPath $batchFile}
 $draft=Call-Api $clients[$blockedIndex] POST '/member/works' @{title=$batchTitle;genre='其他';excerpt='';body=('<p>'+$imported.text+'</p>');cover='';tagIds=@();publish=$false}
 $ownedDraft=[int](Sql "SELECT COUNT(*) FROM works WHERE id=$($draft.id) AND author_id=$editorId AND title='$batchTitle' AND status='DRAFT'")
 Assert-True ($draft.status-eq'DRAFT' -and $ownedDraft-eq1) 'Editor document import did not produce an editor-owned draft'
 [void](Call-Api $clients[$blockedIndex] GET '/admin/settings' $null 403)
 [void](Call-Api $clients[$blockedIndex] POST '/admin/tags' @{name='不可创建';description='';isActivity=$true} 403)
 [void](Call-Api $clients[$blockedIndex] POST '/auth/identity' @{id=$activityId})
 [void](Call-Api $clients[$blockedIndex] GET '/admin/overview')
 [void](Call-Api $clients[$blockedIndex] POST '/admin/works/1/withdraw' $null 403)
 [void](Call-Api $clients[$blockedIndex] POST '/auth/identity' @{id=$techId})
 $site=Call-Api $clients[$blockedIndex] GET '/admin/settings'
 $site.registrationEnabled=!$site.registrationEnabled
 [void](Call-Api $clients[$blockedIndex] PUT '/admin/settings' $site 403)
 [void](Call-Api $clients[$blockedIndex] GET '/admin/accounts' $null 403)
 Write-Host 'PASS: public login denied, five distinct concurrent users, sixth rejected, permission intersection, revocation and released seat.'
} finally {
 if($jobs.Count){$jobs | Wait-Job -Timeout 35 | Out-Null;$jobs | Remove-Job -Force -ErrorAction SilentlyContinue}
 $quoted=($names | ForEach-Object {"'$_'"}) -join ','
 if($createdPublic){
  $sql="DELETE FROM work_tags WHERE work_id IN (SELECT w.id FROM works w JOIN members m ON m.id=w.author_id WHERE m.username='haiyan_editor' AND w.title='$batchTitle'); DELETE FROM works WHERE title='$batchTitle' AND author_id IN (SELECT id FROM members WHERE username='haiyan_editor' AND account_type='PUBLIC'); DELETE FROM public_identity_audit WHERE public_member_id IN (SELECT id FROM members WHERE username='$publicName') OR actor_member_id IN (SELECT id FROM members WHERE username IN ($quoted)); DELETE FROM member_permissions WHERE member_id IN (SELECT id FROM members WHERE username IN ($quoted)); DELETE FROM members WHERE username='$publicName' AND account_type='PUBLIC'; DELETE FROM members WHERE username IN ($quoted) AND account_type='PERSONAL';"
  [void](Sql $sql)
 }
 if(Test-Path -LiteralPath $batchFile){Remove-Item -LiteralPath $batchFile -Force}
}
