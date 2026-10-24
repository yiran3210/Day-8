param(
    [switch]$Installer
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$releaseInput = Join-Path $root 'target\release-input'
$dist = Join-Path $root 'dist'
$icon = Join-Path $root 'src\main\resources\icons\agenda-icon.ico'
$portable = Join-Path $dist 'Day 8'

$portableZip = Join-Path $dist 'Day8-v0.2.1-win-x64-portable.zip'

if (-not $env:JAVA_HOME) {
    throw 'JAVA_HOME is not set. Point it to your JDK 25 installation.'
}

$javac = Join-Path $env:JAVA_HOME 'bin\javac.exe'
$jpackage = Join-Path $env:JAVA_HOME 'bin\jpackage.exe'
if (-not (Test-Path $javac) -or -not (Test-Path $jpackage)) {
    throw 'JAVA_HOME does not contain javac.exe and jpackage.exe.'
}

$javacVersion = & $javac -version
if ($LASTEXITCODE -ne 0 -or $javacVersion -notmatch '^javac 25(\.|\s|$)') {
    throw "JDK 25 required; detected: $javacVersion"
}

if (-not (Test-Path $icon)) {
    throw "Missing Windows icon: $icon"
}
$iconBytes = [System.IO.File]::ReadAllBytes($icon)
if ($iconBytes.Length -lt 4 -or $iconBytes[0] -ne 0 -or
    $iconBytes[1] -ne 0 -or $iconBytes[2] -ne 1 -or $iconBytes[3] -ne 0) {
    throw 'agenda-icon.ico is not a real Windows ICO file.'
}

Push-Location $root
try {
    Write-Host '[1/4] Compile and verify' -ForegroundColor Cyan
    & mvn clean verify
    if ($LASTEXITCODE -ne 0) { throw 'Maven verification failed.' }

    Write-Host '[2/4] Gather runtime dependencies' -ForegroundColor Cyan
    if (Test-Path $releaseInput) {
        Remove-Item $releaseInput -Recurse -Force
    }
    New-Item -ItemType Directory -Path $releaseInput -Force | Out-Null
    & mvn dependency:copy-dependencies '-DincludeScope=runtime' "-DoutputDirectory=$releaseInput"
    if ($LASTEXITCODE -ne 0) { throw 'Dependency copying failed.' }

    $jar = Get-ChildItem (Join-Path $root 'target') -File -Filter 'personal-agenda-*.jar' |
        Where-Object { $_.Name -notmatch '-(sources|javadoc|tests)\.jar$' } |
        Select-Object -First 1
    if ($null -eq $jar) { throw 'No personal-agenda application jar was produced.' }
    Copy-Item $jar.FullName -Destination $releaseInput

    if (-not (Get-ChildItem $releaseInput -Filter 'javafx-graphics-*-win.jar' -File)) {
        throw 'Missing Windows JavaFX native runtime jar (javafx-graphics-*-win.jar).'
    }
    if (-not (Get-ChildItem $releaseInput -Filter 'sqlite-jdbc-*.jar' -File)) {
        throw 'Missing SQLite JDBC jar.'
    }

    Write-Host '[3/4] Build self-contained Windows app-image' -ForegroundColor Cyan
    New-Item -ItemType Directory -Path $dist -Force | Out-Null
    if (Test-Path $portable) { Remove-Item $portable -Recurse -Force }

    & $jpackage `
        --type app-image `
        --name 'Day 8' `
        --app-version 0.2.1 `
        --input $releaseInput `
        --main-jar $jar.Name `
        --main-class dev.personalagenda.Launcher `
        --icon $icon `
        --java-options '--enable-native-access=ALL-UNNAMED' `
        --dest $dist
    if ($LASTEXITCODE -ne 0) { throw 'jpackage app-image failed.' }

    $exe = Join-Path $portable 'Day 8.exe'
    if (-not (Test-Path $exe)) { throw "Missing launcher: $exe" }

    Write-Host '[4/4] Create portable ZIP' -ForegroundColor Cyan
    Compress-Archive -Path $portable -DestinationPath $portableZip -Force
    Write-Host "Portable app: $exe" -ForegroundColor Green
    Write-Host "Portable zip: $portableZip" -ForegroundColor Green

    if ($Installer) {
        Write-Host '[Optional] Build Windows installer (requires WiX Toolset)' -ForegroundColor Cyan
        & $jpackage `
            --type exe `
            --name PersonalAgenda `
            --app-version 0.2.1 `
            --app-image $portable `
            --dest $dist `
            --win-menu `
            --win-shortcut `
            --win-dir-chooser
        if ($LASTEXITCODE -ne 0) { throw 'jpackage installer failed. Check WiX installation.' }
        Write-Host "Installer output directory: $dist" -ForegroundColor Green
    }
}
finally {
    Pop-Location
}
