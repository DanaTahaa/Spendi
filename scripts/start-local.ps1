param([string]$JavaHome=$env:JAVA_HOME,[string]$GradleUserHome,[int]$TimeoutSeconds=180)
$ErrorActionPreference='Stop'
$projectRoot=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$localRoot=Join-Path $projectRoot '.local-spendi'
New-Item -ItemType Directory -Path $localRoot -Force | Out-Null
$credentialFile=Join-Path $localRoot 'connection.json'
function Test-LocalService {
    if(!(Test-Path -LiteralPath $credentialFile)){return $false}
    try {
        $settings=Get-Content -LiteralPath $credentialFile -Raw | ConvertFrom-Json
        $headers=@{Authorization=('Bearer '+$settings.token)}
        $state=Invoke-RestMethod -Uri 'http://127.0.0.1:18080/status' -Headers $headers -TimeoutSec 2
        $ready=Invoke-RestMethod -Uri 'http://127.0.0.1:8080/health/ready' -TimeoutSec 2
        return $state.instance -eq $settings.instance -and $ready.status -eq 'ready'
    } catch {return $false}
}
if(Test-LocalService){Write-Output 'Local Spendi server is already ready at http://127.0.0.1:8080'; exit 0}
if(!$JavaHome){throw 'Pass -JavaHome pointing to JDK 21.'}
$java=Join-Path $JavaHome 'bin/java.exe'
$javaVersion=(& $java --version | Out-String)
if($javaVersion -notmatch '(?m)^(openjdk|java) 21(?:[. ]|$)'){throw 'The local launcher requires JDK 21.'}
Push-Location -LiteralPath $projectRoot
try {
    $gradleArgs=@('--no-daemon','--max-workers=2','-Dorg.gradle.jvmargs=-Xmx768m -Dfile.encoding=UTF-8','-PbackendOnly',':backend:prepareLocalServer','--console=plain')
    if($GradleUserHome){$gradleArgs+=@('--gradle-user-home',$GradleUserHome)}
    $env:JAVA_HOME=$JavaHome
    & (Join-Path $projectRoot 'gradlew.bat') @gradleArgs
    if($LASTEXITCODE -ne 0){throw 'Could not prepare the local server.'}
} finally {Pop-Location}
$classpath=Join-Path $projectRoot 'backend/build/local-server/lib/*'
$arguments=@('-Xmx256m','-cp',('"'+$classpath+'"'),'com.example.spendi.backend.LocalServer',('"'+$localRoot+'"'))
$process=Start-Process -FilePath $java -ArgumentList $arguments -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $localRoot 'server.log') -RedirectStandardError (Join-Path $localRoot 'server-error.log')
$deadline=(Get-Date).AddSeconds($TimeoutSeconds)
while((Get-Date) -lt $deadline){
    if(Test-LocalService){Write-Output "Local API ready at http://127.0.0.1:8080; private configuration: $credentialFile"; exit 0}
    if($process.HasExited){throw "Local server stopped. Check $localRoot/server-error.log"}
    Start-Sleep -Seconds 1
}
throw "Startup timed out. Check $localRoot/server-error.log before retrying."
