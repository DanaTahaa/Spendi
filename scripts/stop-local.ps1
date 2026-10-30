$ErrorActionPreference='Stop'
$projectRoot=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$settings=Get-Content -LiteralPath (Join-Path $projectRoot '.local-spendi/connection.json') -Raw | ConvertFrom-Json
$headers=@{Authorization=('Bearer '+$settings.token)}
$state=Invoke-RestMethod -Uri 'http://127.0.0.1:18080/status' -Headers $headers -TimeoutSec 3
if($state.instance -ne $settings.instance){throw 'Local service identity does not match this project.'}
Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:18080/stop' -Headers $headers -TimeoutSec 5 | Out-Null
Write-Output 'Graceful shutdown requested; the PostgreSQL data directory is preserved.'
