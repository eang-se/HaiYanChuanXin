$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$base='http://127.0.0.1:8091/api'
$runId=[guid]::NewGuid().ToString('N').Substring(0,10)
$nameA='accounta_'+$runId
$nameB='accountb_'+$runId
$password=[guid]::NewGuid().ToString('N')
$properties=@{}
Get-Content -LiteralPath (Join-Path $projectRoot '.local/application-local.properties') | ForEach-Object {if($_ -match '^([^#=]+)=(.*)$'){$properties[$matches[1]]=$matches[2]}}
function New-Client { $session=New-Object Microsoft.PowerShell.Commands.WebRequestSession; $csrf=Invoke-RestMethod "$base/auth/csrf" -WebSession $session; return @{Session=$session;Token=$csrf.token} }
function Call-Api($Client,[string]$Method,[string]$Path,$Body=$null,[int]$Expected=200) {
 $args=@{Uri=$base+$Path;Method=$Method;WebSession=$Client.Session;Headers=@{'X-CSRF-TOKEN'=$Client.Token};SkipHttpErrorCheck=$true}
 if($null-ne $Body){$args.ContentType='application/json';$args.Body=ConvertTo-Json -InputObject $Body -Depth 12 -Compress}
 $response=Invoke-WebRequest @args
 if([int]$response.StatusCode-ne$Expected){throw "$Method $Path expected $Expected; got $($response.StatusCode): $($response.Content)"}
 if($response.Content -and $response.Headers['Content-Type'] -like '*json*'){return $response.Content|ConvertFrom-Json}
 return $null
}
function Assert-True($Condition,[string]$Message){if(!$Condition){throw $Message}}
$guest=New-Client;$admin=New-Client;$a=New-Client;$b=New-Client;$mediaId=$null
try {
 [void](Call-Api $admin POST '/auth/login' @{username='admin';password=$properties['platform.bootstrap.password']})
 $settings=Call-Api $admin GET '/admin/settings'
 Assert-True ($settings.registrationEnabled -and $settings.registrationMode-eq'test') 'This test requires the existing test registration mode; it does not alter site settings.'
 [void](Call-Api $guest GET '/admin/settings' $null 401)
 [void](Call-Api $a POST '/auth/register' @{username='invalid-name';password=$password;qq='12345';code='0000'} 400)
 [void](Call-Api $a POST '/auth/register' @{username=$nameA;password=$password;qq='54321';code='0000'} 400)
 [void](Call-Api $a POST '/auth/register' @{username=$nameA;password=$password;qq='12345';code='1111'} 400)
 $verification=Invoke-WebRequest "$base/auth/verification-code" -Method Post -WebSession $a.Session -Headers @{'X-CSRF-TOKEN'=$a.Token} -ContentType 'application/json' -Body '{"qq":"12345","channel":"email"}' -SkipHttpErrorCheck
 Assert-True ($verification.StatusCode-in@(200,429)) 'Code endpoint failed'
 if($verification.StatusCode-eq200){Assert-True (($verification.Content|ConvertFrom-Json).testMode) 'Test delivery was not identified'}
 [void](Call-Api $a POST '/auth/register' @{username=$nameA;password=$password;qq='12345';code='0000'})
 [void](Call-Api $b POST '/auth/register' @{username=$nameB;password=$password;qq='12345';code='0000'})
 [void](Call-Api $a POST '/auth/login' @{username=$nameA;password=$password})
 [void](Call-Api $b POST '/auth/login' @{username=$nameB;password=$password})
 [void](Call-Api $b GET '/admin/settings' $null 403)
 $me=Call-Api $a GET '/auth/me'
 Assert-True ($me.penName-eq$nameA -and !$me.avatarUrl -and !$me.profileCompleted -and !$me.profilePromptSeen) 'New account defaults or first-login reminder failed'
 [void](Call-Api $b POST '/member/profile/reminder-dismiss')
 $skipped=Call-Api $b GET '/auth/me'
 Assert-True ($skipped.profilePromptSeen -and !$skipped.profileCompleted -and $skipped.penName-eq$nameB) 'Skipping reminder changed default profile'
 $bad=Invoke-WebRequest "$base/member/profile" -Method Put -WebSession $a.Session -Headers @{'X-CSRF-TOKEN'='invalid'} -ContentType 'application/json' -Body '{}' -SkipHttpErrorCheck
 Assert-True ($bad.StatusCode-eq403) 'CSRF protection failed'
 $imagePath=Join-Path $projectRoot ('.runtime/avatar-'+$runId+'.png')
 Add-Type -AssemblyName System.Drawing
 $bitmap=New-Object Drawing.Bitmap 20,20
 try {$bitmap.Save($imagePath,[Drawing.Imaging.ImageFormat]::Png)} finally {$bitmap.Dispose()}
 $uploaded=Invoke-RestMethod "$base/member/images" -Method Post -WebSession $a.Session -Headers @{'X-CSRF-TOKEN'=$a.Token} -Form @{file=Get-Item -LiteralPath $imagePath}
 Remove-Item -LiteralPath $imagePath
 $mediaId=$uploaded.url.Split('/')[-1]
 [void](Call-Api $b PUT '/member/profile' @{penName='不允许';avatarUrl=$uploaded.url} 400)
 [void](Call-Api $a PUT '/member/profile' @{penName='测试笔名';signature='测试签名';bio='测试简介';avatarUrl=$uploaded.url})
 $updated=Call-Api $a GET '/auth/me'
 Assert-True ($updated.penName-eq'测试笔名' -and $updated.signature-eq'测试签名' -and $updated.profileCompleted) 'Profile update failed'
 $avatar=Invoke-WebRequest ('http://127.0.0.1:8091'+$uploaded.url)
 Assert-True ($avatar.StatusCode-eq200) 'Public avatar failed'
 $author=Call-Api $guest GET "/public/authors/$($me.id)"
 Assert-True ($author.signature-eq'测试签名' -and $author.avatarUrl-eq$uploaded.url) 'Public author profile failed'
 $work=@{title='标签验证-'+$runId;genre='诗歌';body='<p>第一段</p><p>第二段</p><script>alert(1)</script>';tagIds=@(1);collectionTags=@('#合集：诗集-'+$runId);publish=$false}
 $draft=Call-Api $a POST '/member/works' $work
 [void](Call-Api $guest GET "/public/works/$($draft.id)" $null 404)
 [void](Call-Api $b PUT "/member/works/$($draft.id)" $work 404)
 $groups=Call-Api $a GET '/member/collections'
 $group=@($groups|Where-Object title -eq ('诗集-'+$runId))[0]
 [void](Call-Api $guest GET "/public/collections/$($group.id)" $null 404)
 $work.publish=$true
 [void](Call-Api $a PUT "/member/works/$($draft.id)" $work)
 $public=Call-Api $guest GET "/public/works/$($draft.id)"
 Assert-True (!$public.body.Contains('<script>') -and @($public.tags|Where-Object kind -eq 'COLLECTION').Count-eq1) 'HTML cleanup or collection tag missing'
 $groupPublic=Call-Api $guest GET "/public/collections/$($group.id)"
 Assert-True ($groupPublic.works.Count-eq1) 'Collection tag was not reflected in directory'
 $second=Call-Api $a POST '/member/works' @{title='第二篇-'+$runId;genre='诗歌';body='<p>第二篇正文</p>';collectionTags=@('诗集-'+$runId);publish=$true}
 $ordered=Call-Api $guest GET "/public/collections/$($group.id)"
 Assert-True ($ordered.works[0].id-eq$draft.id -and $ordered.works[1].id-eq$second.id) 'Collection append order failed'
 [void](Call-Api $a PUT "/member/collections/$($group.id)" @{title='诗集-'+$runId;description='';kind='诗集';published=$true;workIds=@($second.id,$draft.id)})
 [void](Call-Api $a PUT "/member/works/$($draft.id)" $work)
 $reordered=Call-Api $guest GET "/public/collections/$($group.id)"
 Assert-True ($reordered.works[0].id-eq$second.id) 'Article save lost manual chapter order'
 $work.collectionTags=@();[void](Call-Api $a PUT "/member/works/$($draft.id)" $work)
 $removed=Call-Api $guest GET "/public/collections/$($group.id)"
 Assert-True ($removed.works.Count-eq1 -and $removed.works[0].id-eq$second.id) 'Removing collection tag did not update directory'
 $tagId=@($public.tags|Where-Object kind -eq 'COLLECTION')[0].id
 [void](Call-Api $b POST '/member/works' @{title='越权';body='<p>不可加入他人合集</p>';tagIds=@($tagId);publish=$true} 403)
 [void](Call-Api $b PUT "/member/works/$($draft.id)/reaction" @{kind='LIKE';active=$true})
 [void](Call-Api $b PUT "/member/works/$($draft.id)/reaction" @{kind='BOOKMARK';active=$true})
 [void](Call-Api $b POST "/member/works/$($draft.id)/comments" @{body='测试评论'})
 $permissions=@{role='MEMBER';permissions=@{WRITE=$false;UPLOAD=$false;COMMENT=$false;COLLECTIONS=$false}}
 $meB=Call-Api $b GET '/auth/me'
 [void](Call-Api $admin PUT "/admin/accounts/$($meB.id)/permissions" $permissions)
 [void](Call-Api $b POST '/member/works' @{title='权限验证';body='<p>已限制投稿</p>';publish=$true} 403)
 [void](Call-Api $b POST "/member/works/$($draft.id)/comments" @{body='不允许'} 403)
 [void](Call-Api $b POST '/member/collections' @{title='不允许';description='';kind='选集';published=$false;workIds=@()} 403)
 $effective=Call-Api $b GET '/auth/me'
 Assert-True (!$effective.permissions.WRITE -and !$effective.permissions.UPLOAD) 'Permission override not effective'
 [void](Call-Api $a POST "/member/works/$($second.id)/withdraw")
 $withdrawn=Call-Api $guest GET "/public/collections/$($group.id)"
 Assert-True ($withdrawn.works.Count-eq0) 'Withdrawn chapter leaked'
 [void](Call-Api $a POST '/auth/logout')
 [void](Call-Api $a GET '/member/works' $null 401)
 Write-Host 'PASS: open QQ registration, code validation, account defaults, reminder dismissal, profiles, avatar ownership, public author data, CSRF, collection tags, privacy, append/reorder/removal, permissions, interactions, withdrawal and logout.'
} finally {
 $optionPath=Join-Path $projectRoot ('.local/account-test-'+$runId+'.cnf')
 [IO.File]::WriteAllText($optionPath,"[client]`nuser=haiyan_app`npassword=$($properties['spring.datasource.password'])`nhost=127.0.0.1`nport=3306`nprotocol=TCP`ndatabase=haiyan_chuanxin`n",[Text.UTF8Encoding]::new($false))
 $sql=@"
DELETE FROM collection_works WHERE collection_id IN (SELECT id FROM collections WHERE author_id IN (SELECT id FROM members WHERE username IN ('$nameA','$nameB')));
DELETE FROM work_tags WHERE work_id IN (SELECT id FROM works WHERE author_id IN (SELECT id FROM members WHERE username IN ('$nameA','$nameB')));
DELETE FROM work_media WHERE work_id IN (SELECT id FROM works WHERE author_id IN (SELECT id FROM members WHERE username IN ('$nameA','$nameB')));
DELETE FROM reactions WHERE member_id IN (SELECT id FROM members WHERE username IN ('$nameA','$nameB'));
DELETE FROM comments WHERE member_id IN (SELECT id FROM members WHERE username IN ('$nameA','$nameB'));
DELETE FROM works WHERE author_id IN (SELECT id FROM members WHERE username IN ('$nameA','$nameB'));
DELETE FROM tags WHERE owner_id IN (SELECT id FROM members WHERE username IN ('$nameA','$nameB'));
DELETE FROM collections WHERE author_id IN (SELECT id FROM members WHERE username IN ('$nameA','$nameB'));
DELETE FROM media_files WHERE owner_id IN (SELECT id FROM members WHERE username IN ('$nameA','$nameB'));
DELETE FROM member_permissions WHERE member_id IN (SELECT id FROM members WHERE username IN ('$nameA','$nameB'));
DELETE FROM members WHERE username IN ('$nameA','$nameB');
"@
 try{$sql | & 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' ('--defaults-extra-file='+$optionPath) --default-character-set=utf8mb4;if($LASTEXITCODE-ne0){throw 'Account test cleanup failed'}}finally{Remove-Item -LiteralPath $optionPath -Force}
 if($mediaId -and $mediaId-match'^[a-f0-9-]{36}$'){$testImage=Join-Path $projectRoot ('data/images/'+$mediaId+'.jpg');if(Test-Path -LiteralPath $testImage){Remove-Item -LiteralPath $testImage}}
}
