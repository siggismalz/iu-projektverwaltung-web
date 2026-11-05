$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projektPfad = $PSScriptRoot

# Der Wrapper lädt Maven; ein installiertes Maven ist nicht erforderlich.
if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) {
    $jdkKandidaten = @('C:\Program Files\Zulu\zulu-21')
    foreach ($basis in @('C:\Program Files\Java', 'C:\Program Files\Eclipse Adoptium')) {
        if (Test-Path -LiteralPath $basis) {
            $jdkKandidaten += Get-ChildItem -LiteralPath $basis -Directory | Select-Object -ExpandProperty FullName
        }
    }
    $jdkPfad = $jdkKandidaten | Where-Object { Test-Path -LiteralPath (Join-Path $_ 'bin\javac.exe') } | Select-Object -First 1
    if (-not $jdkPfad) { throw 'Bitte JDK 21 oder neuer installieren und JAVA_HOME setzen.' }
    $env:JAVA_HOME = $jdkPfad
}
$env:PATH = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:PATH
if (-not (Get-Command npm.cmd -ErrorAction SilentlyContinue)) { throw 'Node.js mit npm wird zum Bauen benötigt.' }

Push-Location (Join-Path $projektPfad 'frontend')
try {
    & npm.cmd ci
    if ($LASTEXITCODE -ne 0) { throw 'Installation der Frontend-Abhängigkeiten fehlgeschlagen.' }
    & npm.cmd run build
    if ($LASTEXITCODE -ne 0) { throw 'Frontend-Build fehlgeschlagen.' }
} finally { Pop-Location }

$backendPfad = [IO.Path]::GetFullPath((Join-Path $projektPfad 'backend'))
$staticPfad = [IO.Path]::GetFullPath((Join-Path $backendPfad 'src\main\resources\static'))
if (-not $staticPfad.StartsWith($backendPfad + '\', [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Ungültiges Ziel für die generierten Frontend-Dateien.'
}
if (Test-Path -LiteralPath $staticPfad) { Remove-Item -LiteralPath $staticPfad -Recurse -Force }
New-Item -ItemType Directory -Path $staticPfad -Force | Out-Null
Copy-Item -Path (Join-Path $projektPfad 'frontend\dist\*') -Destination $staticPfad -Recurse

Push-Location $backendPfad
try {
    & .\mvnw.cmd verify
    if ($LASTEXITCODE -ne 0) { throw 'Backend-Build oder Tests fehlgeschlagen.' }
} finally { Pop-Location }
Write-Host 'Build und Tests erfolgreich. Anwendung mit Starten.ps1 starten.'
