#Requires -Version 5.1
<#
    Lance MiniCube.

    Utilisation :  .\run.ps1
    Le prefixe .\ est obligatoire sous PowerShell.
#>

$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

$OutputJar = 'MiniCube.jar'

if (-not (Test-Path -LiteralPath $OutputJar)) {
    Write-Host "[ERREUR] $OutputJar est introuvable." -ForegroundColor Red
    Write-Host "  Compilez d'abord le projet :  .\build.ps1" -ForegroundColor Yellow
    exit 1
}

# Le launcher a besoin d'un Java 21 ou superieur pour s'executer, pas seulement
# pour compiler : on reutilise la meme recherche que le script de compilation.
function Get-JavaRuntime {
    $candidates = New-Object System.Collections.Generic.List[string]
    if ($env:JAVA_HOME) { $candidates.Add($env:JAVA_HOME) }
    $roots = @(
        'C:\Program Files\Eclipse Adoptium',
        'C:\Program Files\Microsoft',
        'C:\Program Files\Java',
        'C:\Program Files\Amazon Corretto',
        'C:\Program Files\Zulu'
    )
    foreach ($root in $roots) {
        if (-not (Test-Path -LiteralPath $root)) { continue }
        Get-ChildItem -LiteralPath $root -Directory -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending |
            ForEach-Object { $candidates.Add($_.FullName) }
    }
    foreach ($candidate in $candidates) {
        $releaseFile = Join-Path $candidate 'release'
        $java = Join-Path $candidate 'bin\java.exe'
        if (-not (Test-Path -LiteralPath $java)) { continue }
        if (-not (Test-Path -LiteralPath $releaseFile)) { continue }
        $match = Select-String -Path $releaseFile -Pattern 'JAVA_VERSION="([^"]+)"' -ErrorAction SilentlyContinue
        if (-not $match) { continue }
        if ($match.Matches[0].Groups[1].Value -match '^(\d+)' -and [int]$Matches[1] -ge 21) {
            return $java
        }
    }
    return 'java'
}

$java = Get-JavaRuntime
Write-Host "Demarrage d'MiniCube..." -ForegroundColor Cyan
& $java -jar $OutputJar
