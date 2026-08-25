#Requires -Version 5.1
<#
    Prepare une nouvelle version de MiniCube.

    Le numero vit a deux endroits et doit correspondre a l'etiquette Git : une
    divergence ferait proposer a vos joueurs une mise a jour qui installe la version
    qu'ils ont deja, indefiniment. Ce script fait les trois d'un coup.

    Utilisation :

        .\version.ps1 1.4.0

    Il ne pousse rien : la commande d'envoi est affichee a la fin, a vous de la lancer.
#>

param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string] $Version,

    # Cree la version meme si CHANGELOG.md ne mentionne pas ce numero.
    [switch] $SansJournal
)

$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

function Write-Step($text) { Write-Host ""; Write-Host "==> $text" -ForegroundColor Cyan }
function Write-Ok($text) { Write-Host "    $text" -ForegroundColor Green }
function Write-Detail($text) { Write-Host "    $text" -ForegroundColor DarkGray }

$constants = 'src\main\java\com\minicube\launcher\core\Constants.java'

# ---------------------------------------------------------------------------
# Controles
# ---------------------------------------------------------------------------

$Version = $Version.TrimStart('v', 'V')
if ($Version -notmatch '^\d+\.\d+\.\d+$') {
    Write-Host "[ERREUR] Format attendu : majeur.mineur.correctif, par exemple 1.4.0" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "  Preparation de MiniCube $Version" -ForegroundColor White
Write-Host "  --------------------------------" -ForegroundColor DarkGray

Write-Step "Controles"

$actuelle = (Select-String -Path $constants -Pattern 'APP_VERSION\s*=\s*"([^"]+)"').Matches[0].Groups[1].Value
Write-Detail "version actuelle : $actuelle"

$reprise = $false
if ($actuelle -eq $Version) {
    # Un passage precedent a pu modifier les fichiers puis echouer avant le commit.
    # Plutot que d'exiger une remise en etat manuelle, on reprend ou il s'est arrete.
    if (git tag --list "v$Version") {
        Write-Host "[ERREUR] La version $Version existe deja, etiquette comprise." -ForegroundColor Red
        exit 1
    }
    Write-Detail "les fichiers portent deja $Version : reprise d'un passage interrompu"
    $reprise = $true
}

# Comparaison numerique : passer de 1.4.0 a 1.3.0 est presque toujours une faute de
# frappe. Inutile lors d'une reprise, ou les deux numeros sont par definition egaux.
if (-not $reprise) {
$a = $actuelle.Split('.') | ForEach-Object { [int]$_ }
$n = $Version.Split('.') | ForEach-Object { [int]$_ }
$recule = $false
for ($i = 0; $i -lt 3; $i++) {
    if ($n[$i] -gt $a[$i]) { break }
    if ($n[$i] -lt $a[$i]) { $recule = $true; break }
}
if ($recule) {
    Write-Host "[ERREUR] $Version est anterieure a $actuelle." -ForegroundColor Red
    Write-Host "  Une version qui recule empecherait toute mise a jour ulterieure." -ForegroundColor Yellow
    exit 1
}

if (git tag --list "v$Version") {
    Write-Host "[ERREUR] L'etiquette v$Version existe deja." -ForegroundColor Red
    exit 1
}
}

if (-not $SansJournal) {
    $entree = Select-String -Path 'CHANGELOG.md' -Pattern "^## $([regex]::Escape($Version)) " -ErrorAction SilentlyContinue
    if (-not $entree) {
        Write-Host "[ERREUR] CHANGELOG.md ne contient pas d'entree pour $Version." -ForegroundColor Red
        Write-Host "  Ajoutez une section commencant par :  ## $Version - <date>" -ForegroundColor Yellow
        Write-Host "  C'est ce texte que verront vos joueurs dans l'onglet Mise a jour." -ForegroundColor Yellow
        Write-Host "  Pour passer outre : .\version.ps1 $Version -SansJournal" -ForegroundColor DarkGray
        exit 1
    }
    Write-Detail "entree trouvee dans CHANGELOG.md"
}
Write-Ok "controles passes"

# ---------------------------------------------------------------------------
# Mise a jour des fichiers
# ---------------------------------------------------------------------------

Write-Step "Mise a jour du numero"

$contenu = Get-Content -LiteralPath $constants -Raw
$contenu = $contenu -replace 'APP_VERSION\s*=\s*"[^"]+"', "APP_VERSION = `"$Version`""
# PowerShell 5.1 ajoute systematiquement une marque d'ordre des octets avec
# -Encoding UTF8, et javac refuse un fichier source qui commence par elle. On passe
# donc par .NET, seul moyen d'obtenir de l'UTF-8 sans marque.
$sansBom = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllText((Resolve-Path $constants).Path, $contenu, $sansBom)
Write-Detail "Constants.java"

# Seule la version du projet est touchee : celles des dependances portent le meme
# format et se trouvent plus bas dans le fichier.
$pom = Get-Content -LiteralPath 'pom.xml'
for ($i = 0; $i -lt $pom.Count; $i++) {
    if ($pom[$i] -match '^\s*<version>' -and $i -lt 20) {
        $pom[$i] = $pom[$i] -replace '<version>[^<]+</version>', "<version>$Version</version>"
        break
    }
}
# WriteAllLines terminerait chaque ligne par CRLF, alors que .gitattributes impose LF
# pour les fichiers texte : le diff porterait alors sur le fichier entier.
[System.IO.File]::WriteAllText((Resolve-Path 'pom.xml').Path,
    (($pom -join "`n") + "`n"), $sansBom)
Write-Detail "pom.xml"
Write-Ok "numero porte a $Version"

# ---------------------------------------------------------------------------
# Verification par la compilation
# ---------------------------------------------------------------------------

Write-Step "Compilation de controle"
& powershell -NoProfile -ExecutionPolicy Bypass -File "$PSScriptRoot\build.ps1" | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERREUR] La compilation echoue : la version n'est pas creee." -ForegroundColor Red
    Write-Host "  Les fichiers ont ete modifies, corrigez puis relancez." -ForegroundColor Yellow
    exit 1
}
Write-Ok "le projet compile"

# ---------------------------------------------------------------------------
# Enregistrement et etiquette
# ---------------------------------------------------------------------------

Write-Step "Enregistrement"

# Git ecrit ses avertissements sur la sortie d'erreur : avec ErrorActionPreference a
# Stop, un simple message sur les fins de ligne ferait echouer le script alors que la
# commande a reussi. Le code de retour est le seul indicateur fiable.
$strict = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try {
    git add -A
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERREUR] git add a echoue." -ForegroundColor Red
        exit 1
    }
    git commit -q -m "MiniCube $Version"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERREUR] Le commit a echoue." -ForegroundColor Red
        exit 1
    }
    Write-Detail "commit cree"

    git tag -a "v$Version" -m "MiniCube $Version"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERREUR] La creation de l'etiquette a echoue." -ForegroundColor Red
        exit 1
    }
    Write-Ok "etiquette v$Version creee"
} finally {
    $ErrorActionPreference = $strict
}

# ---------------------------------------------------------------------------
# Termine
# ---------------------------------------------------------------------------

Write-Host ""
Write-Host "  MiniCube $Version est prete." -ForegroundColor Green
Write-Host ""
Write-Host "  Rien n'a ete envoye. Pour publier :" -ForegroundColor White
Write-Host ""
Write-Host "      git push" -ForegroundColor Cyan
Write-Host "      git push origin v$Version" -ForegroundColor Cyan
Write-Host ""
Write-Host "  L'etiquette declenche la fabrication sur GitHub. Quelques minutes plus" -ForegroundColor DarkGray
Write-Host "  tard, la publication apparait et l'onglet Mise a jour la propose a vos" -ForegroundColor DarkGray
Write-Host "  joueurs." -ForegroundColor DarkGray
Write-Host ""
Write-Host "  Pour annuler avant d'avoir pousse :" -ForegroundColor DarkGray
Write-Host "      git tag -d v$Version" -ForegroundColor DarkGray
Write-Host "      git reset --soft HEAD~1" -ForegroundColor DarkGray
Write-Host ""
