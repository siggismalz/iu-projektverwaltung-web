$ErrorActionPreference = 'Stop'
$projektPfad = $PSScriptRoot
$jarPfad = Join-Path $projektPfad 'backend\target\projektverwaltung-1.0.0.jar'
if (-not (Test-Path -LiteralPath $jarPfad)) { & (Join-Path $projektPfad 'Bauen.ps1') }

# Der Build kann JAVA_HOME setzen; Java deshalb erst danach auswählen.
$jdkStandard = 'C:\Program Files\Zulu\zulu-21'
if (-not $env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $jdkStandard 'bin\java.exe'))) {
    $env:JAVA_HOME = $jdkStandard
}
$javaProgramm = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
Push-Location (Join-Path $projektPfad 'backend')
try {
    Write-Host 'Projektverwaltung: http://127.0.0.1:8080'
    Write-Host 'Beenden mit Strg+C. Daten bleiben im Ordner backend\daten erhalten.'
    & $javaProgramm -jar $jarPfad
    if ($LASTEXITCODE -ne 0) { throw 'Die Anwendung konnte nicht erfolgreich ausgeführt werden.' }
} finally { Pop-Location }
