#Requires -Version 5.1
<#
    MiniCube - fabrication de l'executable et de l'installeur Windows.

    Deux livrables sont produits dans dist\ :

      dist\MiniCube\MiniCube.exe        application autonome, runtime Java inclus
      dist\MiniCube-Setup-<version>.exe installeur (raccourcis, desinstallation)

    L'utilisateur final n'a donc besoin ni de Java, ni du .bat.

    Utilisation :  .\package.bat
#>

$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

$AppName = 'MiniCube'
$MainClass = 'com.minicube.launcher.Bootstrap'
$Jar = 'MiniCube.jar'

function Write-Step($text) { Write-Host ""; Write-Host "==> $text" -ForegroundColor Cyan }
function Write-Ok($text) { Write-Host "    $text" -ForegroundColor Green }
function Write-Detail($text) { Write-Host "    $text" -ForegroundColor DarkGray }

# ---------------------------------------------------------------------------
# Version : lue dans Constants.java, seule source de verite
# ---------------------------------------------------------------------------

$constants = 'src\main\java\com\minicube\launcher\core\Constants.java'
$match = Select-String -Path $constants -Pattern 'APP_VERSION\s*=\s*"([^"]+)"'
if (-not $match) {
    Write-Host "[ERREUR] Version introuvable dans $constants" -ForegroundColor Red
    exit 1
}
$Version = $match.Matches[0].Groups[1].Value

Write-Host ""
Write-Host "  $AppName $Version - fabrication des livrables" -ForegroundColor White
Write-Host "  --------------------------------------------" -ForegroundColor DarkGray

# ---------------------------------------------------------------------------
# 1. Compilation
# ---------------------------------------------------------------------------

Write-Step "Compilation"
& powershell -NoProfile -ExecutionPolicy Bypass -File "$PSScriptRoot\build.ps1" | Out-Null
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $Jar)) {
    Write-Host "[ERREUR] La compilation a echoue. Lancez .\build.bat pour voir le detail." -ForegroundColor Red
    exit 1
}
Write-Ok "$Jar pret"

# ---------------------------------------------------------------------------
# 2. Recherche de jpackage
# ---------------------------------------------------------------------------

Write-Step "Outils"
function Find-Jpackage {
    if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\jpackage.exe")) {
        return "$env:JAVA_HOME\bin\jpackage.exe"
    }
    $roots = @(
        'C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Microsoft',
        'C:\Program Files\Java', 'C:\Program Files\Amazon Corretto',
        'C:\Program Files\Zulu', (Join-Path $env:USERPROFILE '.jdks')
    )
    foreach ($root in $roots) {
        if (-not (Test-Path -LiteralPath $root)) { continue }
        $found = Get-ChildItem -LiteralPath $root -Directory -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending |
            ForEach-Object { Join-Path $_.FullName 'bin\jpackage.exe' } |
            Where-Object { Test-Path $_ } |
            Select-Object -First 1
        if ($found) { return $found }
    }
    return $null
}

$jpackage = Find-Jpackage
if (-not $jpackage) {
    Write-Host "[ERREUR] jpackage introuvable." -ForegroundColor Red
    Write-Host "  Il est fourni avec le JDK 21 : winget install EclipseAdoptium.Temurin.21.JDK" -ForegroundColor Yellow
    exit 1
}
Write-Ok "jpackage : $jpackage"

$innoCandidates = @(
    "$env:LOCALAPPDATA\Programs\Inno Setup 6\ISCC.exe",
    "${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe",
    "$env:ProgramFiles\Inno Setup 6\ISCC.exe"
)
$inno = $innoCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if ($inno) { Write-Ok "Inno Setup : $inno" }
else { Write-Detail "Inno Setup absent : seul l'executable autonome sera produit" }

# ---------------------------------------------------------------------------
# 3. Executable autonome (jpackage)
# ---------------------------------------------------------------------------

Write-Step "Executable autonome"

# jpackage recopie TOUT le contenu du dossier d'entree dans l'application : le jar
# est donc isole dans un dossier qui ne contient que lui.
# $input est une variable automatique de PowerShell : la reutiliser romprait le
# fonctionnement du pipeline dans ce script.
$stageDir = 'build\package-input'
Remove-Item $stageDir -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $stageDir -Force | Out-Null
Copy-Item $Jar -Destination $stageDir

$appDir = "dist\$AppName"
Remove-Item $appDir -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path 'dist' -Force | Out-Null

# Modules du JDK reellement utilises. Les enumerer plutot que d'embarquer tout le
# JDK divise la taille par trois. jdk.crypto.ec est indispensable : sans lui, les
# echanges TLS modernes echouent, donc toute connexion a Microsoft ou a Mojang.
$modules = @(
    'java.base', 'java.desktop', 'java.logging', 'java.management',
    'java.naming', 'java.net.http', 'java.prefs', 'java.xml',
    'jdk.crypto.ec', 'jdk.unsupported'
) -join ','

Write-Detail "assemblage du runtime et de l'application..."
& $jpackage `
    --type app-image `
    --name $AppName `
    --app-version $Version `
    --input $stageDir `
    --main-jar $Jar `
    --main-class $MainClass `
    --icon 'installer\MiniCube.ico' `
    --dest 'dist' `
    --vendor 'MiniCube' `
    --description 'Launcher Minecraft moderne' `
    --copyright 'MiniCube' `
    --add-modules $modules `
    --java-options '-Dfile.encoding=UTF-8'

if ($LASTEXITCODE -ne 0 -or -not (Test-Path "$appDir\$AppName.exe")) {
    Write-Host "[ERREUR] jpackage a echoue." -ForegroundColor Red
    exit 1
}
$appSize = (Get-ChildItem $appDir -Recurse -File | Measure-Object -Property Length -Sum).Sum
Write-Ok "$appDir\$AppName.exe ($([math]::Round($appSize / 1MB)) Mo, runtime Java inclus)"

# ---------------------------------------------------------------------------
# 4. Installeur (Inno Setup)
# ---------------------------------------------------------------------------

$setup = "dist\$AppName-Setup-$Version.exe"
if ($inno) {
    Write-Step "Installeur"
    Remove-Item $setup -Force -ErrorAction SilentlyContinue
    & $inno "/DAppVersion=$Version" 'installer\MiniCube.iss' | Out-Null
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $setup)) {
        Write-Host "[ERREUR] La compilation de l'installeur a echoue." -ForegroundColor Red
        exit 1
    }
    $setupSize = (Get-Item $setup).Length
    Write-Ok "$setup ($([math]::Round($setupSize / 1MB, 1)) Mo)"
}

# ---------------------------------------------------------------------------
# Termine
# ---------------------------------------------------------------------------

Write-Host ""
Write-Host "  Livrables prets." -ForegroundColor Green
Write-Host ""
Write-Host "  Executable autonome :" -ForegroundColor White
Write-Host "      $appDir\$AppName.exe" -ForegroundColor Cyan
if ($inno) {
    Write-Host "  Installeur a distribuer :" -ForegroundColor White
    Write-Host "      $setup" -ForegroundColor Cyan
}
Write-Host ""
Write-Host "  Ni Java ni le .bat ne sont necessaires sur la machine d'arrivee." -ForegroundColor DarkGray
Write-Host ""
