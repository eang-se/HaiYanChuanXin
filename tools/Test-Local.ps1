param([int]$Port = 8091)
$ErrorActionPreference = 'Stop'
$baseUrl = "http://127.0.0.1:$Port"
$health = Invoke-RestMethod "$baseUrl/actuator/health"
if ($health.status -ne 'UP') { throw 'Health endpoint is not UP.' }
$site = Invoke-RestMethod "$baseUrl/api/public/site"
if ($site.name -ne '海燕传信') { throw 'Database-backed site configuration mismatch.' }
$page = Invoke-WebRequest "$baseUrl/"
if ($page.Content -notmatch '<div id="app">') { throw 'Frontend entry is missing.' }
$assetMatch = [regex]::Match($page.Content, 'src="(/assets/[^\"]+\.js)"')
if (!$assetMatch.Success) { throw 'Built frontend script is missing.' }
$asset = Invoke-WebRequest ($baseUrl + $assetMatch.Groups[1].Value)
if ($asset.StatusCode -ne 200) { throw 'Frontend script cannot be loaded.' }
foreach ($path in @('/api/admin/environment', '/api/member/profile')) {
    $response = Invoke-WebRequest ($baseUrl + $path) -SkipHttpErrorCheck
    if ($response.StatusCode -ne 401) { throw "Private endpoint did not require authentication: $path" }
}
$secretResponse = Invoke-WebRequest "$baseUrl/.local/application-local.properties" -SkipHttpErrorCheck
if ($secretResponse.StatusCode -eq 200) { throw 'Local configuration is publicly accessible.' }
Write-Host 'PASS: database migration, MyBatis read, UTF-8 response, frontend HTML/JS, health, authentication boundary, and private config isolation.'

