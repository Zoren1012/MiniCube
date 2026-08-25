#Requires -Version 5.1
<#
    Publie la fiche d'installation de MiniCube dans un salon Discord.

    LE WEBHOOK EST UN SECRET. Quiconque le possede peut publier dans votre salon.
    Il est passe en parametre et n'est jamais ecrit dans un fichier du projet : ne le
    collez nulle part dans le depot, il finirait pousse sur GitHub.

    Utilisation :

        # Voir la fiche sans rien envoyer
        .\discord-annonce.ps1 -Webhook "https://discord.com/api/webhooks/..." -Apercu

        # Publier, apres confirmation
        .\discord-annonce.ps1 -Webhook "https://discord.com/api/webhooks/..."

    Pour eviter que le webhook n'apparaisse dans l'historique de PowerShell, passez-le
    par une variable de session :

        $env:MINICUBE_WEBHOOK = "https://discord.com/api/webhooks/..."
        .\discord-annonce.ps1 -Webhook $env:MINICUBE_WEBHOOK

    Le contenu du message se modifie dans discord\fiche-installation.json, sans toucher
    a ce script.
#>

param(
    [Parameter(Mandatory = $true)]
    [string] $Webhook,

    # Depot GitHub d'ou proviennent les telechargements.
    [string] $Depot = 'Zoren1012/MiniCube',

    # Branche ou sont hebergees les captures affichees dans le message.
    [string] $Branche = 'main',

    # Affiche la fiche sans rien envoyer.
    [switch] $Apercu,

    # Publie sans demander confirmation.
    [switch] $Force,

    # Retire la capture, utile tant que le depot n'est pas encore pousse.
    [switch] $SansImage
)

$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

function Write-Step($text) { Write-Host ""; Write-Host "==> $text" -ForegroundColor Cyan }
function Write-Ok($text) { Write-Host "    $text" -ForegroundColor Green }
function Write-Detail($text) { Write-Host "    $text" -ForegroundColor DarkGray }

# ---------------------------------------------------------------------------
# Controles
# ---------------------------------------------------------------------------

if ($Webhook -notmatch '^https://(discord\.com|discordapp\.com)/api/webhooks/\d+/[\w-]+$') {
    Write-Host "[ERREUR] Ce n'est pas une adresse de webhook Discord valide." -ForegroundColor Red
    Write-Host "  Attendu : https://discord.com/api/webhooks/<identifiant>/<jeton>" -ForegroundColor Yellow
    Write-Host "  On l'obtient dans Discord : Parametres du salon > Integrations > Webhooks." -ForegroundColor Yellow
    exit 1
}

$modele = 'discord\fiche-installation.json'
if (-not (Test-Path -LiteralPath $modele)) {
    Write-Host "[ERREUR] Modele introuvable : $modele" -ForegroundColor Red
    exit 1
}

$constants = 'src\main\java\com\minicube\launcher\core\Constants.java'
$trouve = Select-String -Path $constants -Pattern 'APP_VERSION\s*=\s*"([^"]+)"'
if (-not $trouve) {
    Write-Host "[ERREUR] Version introuvable dans $constants" -ForegroundColor Red
    exit 1
}
$version = $trouve.Matches[0].Groups[1].Value

# ---------------------------------------------------------------------------
# Construction du message
# ---------------------------------------------------------------------------

Write-Step "Preparation de la fiche"

# Le modele est lu explicitement en UTF-8 : c'est la que vivent les accents, et
# PowerShell 5.1 supposerait sinon l'encodage ANSI de la machine.
$contenu = Get-Content -LiteralPath $modele -Raw -Encoding UTF8

$lien = "https://github.com/$Depot/releases/latest"
$image = if ($SansImage) { '' } else {
    "https://raw.githubusercontent.com/$Depot/$Branche/docs/images/home.png"
}

$contenu = $contenu.Replace('{{VERSION}}', $version)
$contenu = $contenu.Replace('{{DEPOT}}', $Depot)
$contenu = $contenu.Replace('{{LIEN}}', $lien)
$contenu = $contenu.Replace('{{IMAGE}}', $image)

try {
    $message = $contenu | ConvertFrom-Json
} catch {
    Write-Host "[ERREUR] Le modele n'est pas un JSON valide : $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# Les cles de documentation ne doivent pas partir chez Discord, qui rejetterait
# le message pour champ inconnu.
$message.PSObject.Properties.Remove('_comment')
if ($SansImage) {
    foreach ($embed in $message.embeds) { $embed.PSObject.Properties.Remove('image') }
}

$json = $message | ConvertTo-Json -Depth 12 -Compress:$false

Write-Ok "version $version, depot $Depot"
Write-Detail "telechargement : $lien"
if (-not $SansImage) { Write-Detail "capture       : $image" }

# ---------------------------------------------------------------------------
# Apercu
# ---------------------------------------------------------------------------

# ---------------------------------------------------------------------------
# Limites de Discord
# ---------------------------------------------------------------------------

# Discord repond 400 sans jamais dire quel champ pose probleme. Les limites sont
# donc verifiees ici, ou l'on sait exactement lequel a debarde.
Write-Step "Controle des limites Discord"
$problemes = @()

foreach ($embed in $message.embeds) {
    if ($embed.title.Length -gt 256) {
        $problemes += "titre : $($embed.title.Length) caracteres, maximum 256"
    }
    if ($embed.description.Length -gt 4096) {
        $problemes += "description : $($embed.description.Length) caracteres, maximum 4096"
    }
    if ($embed.footer -and $embed.footer.text.Length -gt 2048) {
        $problemes += "pied de page : $($embed.footer.text.Length) caracteres, maximum 2048"
    }
    if ($embed.fields.Count -gt 25) {
        $problemes += "$($embed.fields.Count) champs, maximum 25"
    }
    foreach ($field in $embed.fields) {
        if ($field.name.Length -gt 256) {
            $problemes += "champ '$($field.name)' : intitule de $($field.name.Length) caracteres, maximum 256"
        }
        if ($field.value.Length -gt 1024) {
            $problemes += "champ '$($field.name)' : contenu de $($field.value.Length) caracteres, maximum 1024"
        }
    }
    $total = $embed.title.Length + $embed.description.Length +
             ($embed.fields | ForEach-Object { $_.name.Length + $_.value.Length } |
              Measure-Object -Sum).Sum
    if ($embed.footer) { $total += $embed.footer.text.Length }
    if ($total -gt 6000) {
        $problemes += "message complet : $total caracteres, maximum 6000"
    }
    Write-Detail "$total caracteres au total, $($embed.fields.Count) champs"
}

if ($problemes.Count -gt 0) {
    Write-Host ""
    Write-Host "[ERREUR] Le message depasse ce que Discord accepte :" -ForegroundColor Red
    $problemes | ForEach-Object { Write-Host "  - $_" -ForegroundColor Yellow }
    Write-Host "  Raccourcissez les textes dans $modele" -ForegroundColor Yellow
    exit 1
}
Write-Ok "toutes les limites sont respectees"

Write-Step "Apercu du message"
foreach ($embed in $message.embeds) {
    Write-Host ""
    Write-Host "  $($embed.title)" -ForegroundColor White
    Write-Host "  $($embed.description -replace "`n", "`n  ")" -ForegroundColor Gray
    foreach ($field in $embed.fields) {
        Write-Host ""
        Write-Host "  $($field.name)" -ForegroundColor Cyan
        Write-Host "  $($field.value -replace "`n", "`n  ")" -ForegroundColor Gray
    }
    Write-Host ""
    Write-Host "  $($embed.footer.text)" -ForegroundColor DarkGray
}

if ($Apercu) {
    Write-Host ""
    Write-Host "  Apercu uniquement : rien n'a ete envoye." -ForegroundColor Yellow
    Write-Host "  Relancez sans -Apercu pour publier." -ForegroundColor Yellow
    Write-Host ""
    exit 0
}

# ---------------------------------------------------------------------------
# Confirmation
# ---------------------------------------------------------------------------

if (-not $Force) {
    Write-Host ""
    # Le salon vise n'est pas devinable depuis le webhook : mieux vaut le rappeler
    # que de publier au mauvais endroit.
    Write-Host "  Ce message va etre publie dans le salon associe au webhook." -ForegroundColor Yellow
    $reponse = Read-Host "  Publier maintenant ? (o/N)"
    if ($reponse -notmatch '^(o|oui|y|yes)$') {
        Write-Host "  Annule, rien n'a ete envoye." -ForegroundColor Yellow
        exit 0
    }
}

# ---------------------------------------------------------------------------
# Envoi
# ---------------------------------------------------------------------------

Write-Step "Publication"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

# Le corps est converti en octets UTF-8 : sans cela, PowerShell 5.1 encode selon la
# page de codes de la machine et les accents arrivent illisibles sur Discord.
$octets = [System.Text.Encoding]::UTF8.GetBytes($json)

try {
    $reponse = Invoke-WebRequest -Uri $Webhook -Method Post -Body $octets `
        -ContentType 'application/json; charset=utf-8' -UseBasicParsing
    Write-Ok "publie (HTTP $($reponse.StatusCode))"
} catch {
    $statut = $null
    if ($_.Exception.Response) { $statut = [int]$_.Exception.Response.StatusCode }

    Write-Host ""
    switch ($statut) {
        401 { Write-Host "[ERREUR] Webhook refuse : le jeton est invalide." -ForegroundColor Red }
        404 { Write-Host "[ERREUR] Webhook introuvable : il a ete supprime ou l'adresse est incorrecte." -ForegroundColor Red }
        429 { Write-Host "[ERREUR] Trop de messages envoyes. Patientez une minute." -ForegroundColor Red }
        400 {
            Write-Host "[ERREUR] Discord a refuse le message." -ForegroundColor Red
            Write-Host "  Causes frequentes : un champ depasse 1024 caracteres, la" -ForegroundColor Yellow
            Write-Host "  description depasse 4096, ou une adresse d'image est invalide." -ForegroundColor Yellow
        }
        default { Write-Host "[ERREUR] Envoi impossible : $($_.Exception.Message)" -ForegroundColor Red }
    }
    exit 1
}

Write-Host ""
Write-Host "  Fiche publiee." -ForegroundColor Green
Write-Host ""
Write-Host "  Pour la republier apres une nouvelle version, relancez la meme commande :" -ForegroundColor DarkGray
Write-Host "  la version est relue dans le code a chaque fois." -ForegroundColor DarkGray
Write-Host ""
