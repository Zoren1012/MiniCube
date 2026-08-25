#Requires -Version 5.1
<#
    MiniCube - compilation sans Maven.

    Ce script fait tout ce que ferait Maven, mais avec le seul JDK :
      1. il localise un JDK 21 ou superieur,
      2. il telecharge les quatre bibliotheques necessaires dans lib/,
      3. il compile les sources dans build/classes,
      4. il assemble MiniCube.jar, autonome (JavaFX inclus).

    Utilisation depuis PowerShell, a la racine du projet :
        .\build.ps1

    Le prefixe .\ est obligatoire : PowerShell n'execute pas les scripts du
    dossier courant sans lui.
#>

$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

$JavafxVersion = '21.0.4'
$GsonVersion = '2.11.0'
$Platform = 'win'
$MainClass = 'com.minicube.launcher.Bootstrap'
$OutputJar = 'MiniCube.jar'

function Write-Step($text) { Write-Host ""; Write-Host "==> $text" -ForegroundColor Cyan }
function Write-Ok($text) { Write-Host "    $text" -ForegroundColor Green }
function Write-Detail($text) { Write-Host "    $text" -ForegroundColor DarkGray }

# ---------------------------------------------------------------------------
# 1. Recherche d'un JDK 21 ou superieur
# ---------------------------------------------------------------------------

function Get-JdkVersion($javaHome) {
    # Le fichier "release" livre avec chaque JDK evite de lancer un processus.
    $releaseFile = Join-Path $javaHome 'release'
    if (Test-Path -LiteralPath $releaseFile) {
        $match = Select-String -Path $releaseFile -Pattern 'JAVA_VERSION="([^"]+)"' -ErrorAction SilentlyContinue
        if ($match) {
            $raw = $match.Matches[0].Groups[1].Value
            if ($raw -match '^(\d+)') { return [int]$Matches[1] }
        }
    }
    # Repli : interroger javac directement.
    $javac = Join-Path $javaHome 'bin\javac.exe'
    if (-not (Test-Path -LiteralPath $javac)) { return 0 }
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $output = (& $javac -version 2>&1 | Out-String)
    $ErrorActionPreference = $previous
    if ($output -match 'javac\s+(\d+)') { return [int]$Matches[1] }
    return 0
}

function Find-Jdk {
    $homes = New-Object System.Collections.Generic.List[string]

    if ($env:JAVA_HOME) { $homes.Add($env:JAVA_HOME) }

    $roots = @(
        'C:\Program Files\Eclipse Adoptium',
        'C:\Program Files\Microsoft',
        'C:\Program Files\Java',
        'C:\Program Files\Amazon Corretto',
        'C:\Program Files\Zulu',
        'C:\Program Files\BellSoft',
        (Join-Path $env:USERPROFILE '.jdks')
    )
    foreach ($root in $roots) {
        if (-not (Test-Path -LiteralPath $root)) { continue }
        Get-ChildItem -LiteralPath $root -Directory -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending |
            ForEach-Object { $homes.Add($_.FullName) }
    }

    $onPath = Get-Command javac -ErrorAction SilentlyContinue
    if ($onPath) { $homes.Add((Split-Path (Split-Path $onPath.Source -Parent) -Parent)) }

    foreach ($candidate in $homes) {
        if (-not (Test-Path -LiteralPath (Join-Path $candidate 'bin\javac.exe'))) { continue }
        $version = Get-JdkVersion $candidate
        if ($version -ge 21) {
            return [pscustomobject]@{ Home = $candidate; Version = $version }
        }
    }
    return $null
}

Write-Host ""
Write-Host "  MiniCube - compilation" -ForegroundColor White
Write-Host "  ------------------------------" -ForegroundColor DarkGray

Write-Step "Recherche d'un JDK 21 ou superieur"
$jdk = Find-Jdk
if (-not $jdk) {
    Write-Host ""
    Write-Host "[ERREUR] Aucun JDK 21 ou superieur n'a ete trouve." -ForegroundColor Red
    Write-Host ""
    Write-Host "  Un JRE ne suffit pas : il faut un JDK (il contient javac)." -ForegroundColor Yellow
    Write-Host "  Installation :" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "      winget install EclipseAdoptium.Temurin.21.JDK"
    Write-Host ""
    Write-Host "  Fermez puis rouvrez PowerShell, et relancez .\build.ps1" -ForegroundColor Yellow
    Write-Host ""
    exit 1
}
$javac = Join-Path $jdk.Home 'bin\javac.exe'
$jar = Join-Path $jdk.Home 'bin\jar.exe'
Write-Ok "JDK $($jdk.Version) : $($jdk.Home)"

# ---------------------------------------------------------------------------
# 2. Telechargement des dependances
# ---------------------------------------------------------------------------

$dependencies = @(
    @{ Name = "gson-$GsonVersion.jar"
       Url  = "https://repo1.maven.org/maven2/com/google/code/gson/gson/$GsonVersion/gson-$GsonVersion.jar" },
    @{ Name = "javafx-base-$JavafxVersion-$Platform.jar"
       Url  = "https://repo1.maven.org/maven2/org/openjfx/javafx-base/$JavafxVersion/javafx-base-$JavafxVersion-$Platform.jar" },
    @{ Name = "javafx-graphics-$JavafxVersion-$Platform.jar"
       Url  = "https://repo1.maven.org/maven2/org/openjfx/javafx-graphics/$JavafxVersion/javafx-graphics-$JavafxVersion-$Platform.jar" },
    @{ Name = "javafx-controls-$JavafxVersion-$Platform.jar"
       Url  = "https://repo1.maven.org/maven2/org/openjfx/javafx-controls/$JavafxVersion/javafx-controls-$JavafxVersion-$Platform.jar" }
)

Write-Step "Dependances (JavaFX $JavafxVersion et Gson $GsonVersion)"
New-Item -ItemType Directory -Path 'lib' -Force | Out-Null
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

foreach ($dependency in $dependencies) {
    $target = Join-Path 'lib' $dependency.Name
    if (Test-Path -LiteralPath $target) {
        Write-Detail "$($dependency.Name) deja present"
        continue
    }
    Write-Detail "telechargement de $($dependency.Name) ..."
    $progressBackup = $ProgressPreference
    $ProgressPreference = 'SilentlyContinue'
    try {
        Invoke-WebRequest -Uri $dependency.Url -OutFile $target -UseBasicParsing
    } catch {
        Write-Host ""
        Write-Host "[ERREUR] Telechargement impossible : $($dependency.Name)" -ForegroundColor Red
        Write-Host "  $($_.Exception.Message)" -ForegroundColor Red
        Write-Host "  Verifiez votre connexion, puis relancez .\build.ps1" -ForegroundColor Yellow
        exit 1
    } finally {
        $ProgressPreference = $progressBackup
    }
}
$totalSize = (Get-ChildItem 'lib' -Filter '*.jar' | Measure-Object -Property Length -Sum).Sum
Write-Ok "$($dependencies.Count) bibliotheques pretes ($([math]::Round($totalSize / 1MB, 1)) Mo)"

# ---------------------------------------------------------------------------
# 3. Compilation
# ---------------------------------------------------------------------------

Write-Step "Compilation des sources"
Remove-Item 'build' -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path 'build\classes' -Force | Out-Null

$projectRoot = (Get-Location).Path
$sourceFiles = Get-ChildItem -Path 'src\main\java' -Filter '*.java' -Recurse |
    ForEach-Object { $_.FullName.Substring($projectRoot.Length + 1).Replace('\', '/') }

# Un fichier d'arguments evite la limite de longueur de ligne de commande de Windows.
# Les chemins sont relatifs et en barres obliques : ni espace ni echappement a gerer.
$argumentFile = 'build\sources.txt'
Set-Content -Path $argumentFile -Value $sourceFiles -Encoding ASCII
Write-Detail "$($sourceFiles.Count) fichiers source"

$classpath = ((Get-ChildItem 'lib' -Filter '*.jar' | ForEach-Object { "lib/$($_.Name)" }) -join ';')

& $javac -d 'build/classes' --release 21 -encoding UTF-8 -classpath $classpath "@$argumentFile"
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "[ERREUR] La compilation a echoue (voir les messages ci-dessus)." -ForegroundColor Red
    exit 1
}
$classCount = (Get-ChildItem 'build\classes' -Filter '*.class' -Recurse).Count
Write-Ok "$classCount classes compilees"

# ---------------------------------------------------------------------------
# 4. Ressources
# ---------------------------------------------------------------------------

Write-Step "Copie des ressources"
Copy-Item -Path 'src\main\resources\*' -Destination 'build\classes' -Recurse -Force
Write-Ok "feuilles de style, traductions et donnees embarquees copiees"

# ---------------------------------------------------------------------------
# 5. Assemblage du jar autonome
# ---------------------------------------------------------------------------

Write-Step "Assemblage de $OutputJar"
$stage = 'build\stage'
New-Item -ItemType Directory -Path $stage -Force | Out-Null

# Les dependances sont depliees dans le meme arbre que nos classes : le jar
# obtenu se lance avec un simple "java -jar", sans dossier lib a cote.
foreach ($dependency in $dependencies) {
    $absolute = Join-Path $projectRoot "lib\$($dependency.Name)"
    Push-Location $stage
    try {
        & $jar --extract --file $absolute
        if ($LASTEXITCODE -ne 0) { throw "extraction de $($dependency.Name) impossible" }
    } finally {
        Pop-Location
    }
}

# Un jar assemble a partir de plusieurs modules ne doit garder ni descripteur de
# module, ni signature, ni manifeste d'origine : le nouveau manifeste les remplace.
Get-ChildItem $stage -Filter 'module-info.class' -Recurse -ErrorAction SilentlyContinue |
    Remove-Item -Force -ErrorAction SilentlyContinue
$metaInf = Join-Path $stage 'META-INF'
if (Test-Path -LiteralPath $metaInf) {
    Get-ChildItem $metaInf -Include '*.SF', '*.DSA', '*.RSA', 'MANIFEST.MF' -Recurse -ErrorAction SilentlyContinue |
        Remove-Item -Force -ErrorAction SilentlyContinue
}

Copy-Item -Path 'build\classes\*' -Destination $stage -Recurse -Force

# Le jar precedent peut encore etre verrouille quelques instants : par un launcher
# tout juste ferme, ou par un antivirus en train de l'analyser. Trois tentatives
# espacees suffisent a franchir ce delai sans faire echouer la compilation.
Remove-Item $OutputJar -Force -ErrorAction SilentlyContinue
$attempt = 0
do {
    $attempt++
    & $jar --create --file $OutputJar --main-class $MainClass -C $stage .
    if ($LASTEXITCODE -eq 0) { break }
    if ($attempt -lt 3) {
        Write-Detail "fichier occupe, nouvelle tentative ($attempt sur 3)..."
        Start-Sleep -Milliseconds 900
        Remove-Item $OutputJar -Force -ErrorAction SilentlyContinue
    }
} while ($attempt -lt 3)

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "[ERREUR] Creation du jar impossible apres $attempt tentatives." -ForegroundColor Red
    Write-Host "  Fermez MiniCube s'il est encore ouvert, puis relancez .\build.bat" -ForegroundColor Yellow
    exit 1
}

$jarSize = [math]::Round((Get-Item $OutputJar).Length / 1MB, 1)
Write-Ok "$OutputJar cree ($jarSize Mo)"

# ---------------------------------------------------------------------------
# Termine
# ---------------------------------------------------------------------------

Write-Host ""
Write-Host "  Compilation terminee." -ForegroundColor Green
Write-Host ""
Write-Host "  Lancer le launcher :" -ForegroundColor White
Write-Host "      .\run.bat" -ForegroundColor Cyan
Write-Host "  ou :" -ForegroundColor White
Write-Host "      java -jar $OutputJar" -ForegroundColor Cyan
Write-Host ""
