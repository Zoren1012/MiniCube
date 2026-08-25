# Guide d'installation — MiniCube

Ce document couvre l'installation des outils, la compilation, la configuration de la
connexion Microsoft et le dépannage.

---

## 1. Prérequis

### 1.1 JDK 21

Un **JDK** (kit de développement) est nécessaire, pas seulement un JRE.

**Windows**

```bat
winget install EclipseAdoptium.Temurin.21.JDK
```

Ou téléchargez l'installeur : <https://adoptium.net/temurin/releases/?version=21>
(choisissez *JDK*, architecture *x64*, format *.msi*). Cochez l'option
**« Set JAVA_HOME variable »** pendant l'installation.

**macOS**

```bash
brew install --cask temurin@21
```

**Linux (Debian, Ubuntu)**

```bash
sudo apt update && sudo apt install openjdk-21-jdk
```

**Linux (Fedora)**

```bash
sudo dnf install java-21-openjdk-devel
```

Vérification :

```bash
javac -version
```

La commande doit répondre `javac 21.x.x` ou une version supérieure. Si elle est
introuvable alors que `java` fonctionne, c'est qu'un JRE est installé mais pas un JDK.

### 1.2 Maven — facultatif

**Sous Windows, Maven n'est pas nécessaire.** Le script `build.bat` télécharge lui-même
les quatre bibliothèques et appelle `javac` : le JDK suffit.

Maven n'est d'ailleurs **pas distribué par winget** — `winget install Apache.Maven`
répond « Aucun package ne correspond aux critères sélectionnés ». Si vous y tenez malgré
tout, il faut l'installer à la main :

1. téléchargez l'archive *binary zip* sur <https://maven.apache.org/download.cgi> ;
2. décompressez-la, par exemple dans `C:\Tools\apache-maven-3.9.9` ;
3. ajoutez son sous-dossier `bin` à la variable `Path`.

Sur macOS et Linux, Maven s'installe normalement :

```bash
brew install maven          # macOS
sudo apt install maven      # Debian, Ubuntu
sudo dnf install maven      # Fedora
```

---

## 2. Compilation

Placez-vous à la racine du projet, le dossier contenant `pom.xml`.

### Windows — sans Maven (recommandé)

```bash
.\build.bat
```

Le préfixe `.\` est **obligatoire** sous PowerShell : par sécurité, il n'exécute jamais
un programme du dossier courant sans lui. Depuis `cmd.exe`, `build.bat` suffit ; un
double-clic dans l'explorateur fonctionne aussi.

Le script enchaîne quatre étapes :

1. **recherche d'un JDK 21** — dans `JAVA_HOME`, puis dans les emplacements habituels
   (Eclipse Adoptium, Microsoft, Oracle, Corretto, Zulu, BellSoft, `~/.jdks`). Il lit le
   fichier `release` de chaque JDK, ce qui évite de dépendre du `Path` : un JDK installé
   mais absent du `Path` est trouvé quand même ;
2. **téléchargement des dépendances** dans `lib/` — environ 9 Mo, une seule fois ;
3. **compilation** des sources dans `build/classes` ;
4. **assemblage** de `MiniCube.jar` à la racine, avec JavaFX déplié à l'intérieur :
   le jar est autonome, il n'a besoin d'aucun dossier `lib` à côté de lui.

Sortie attendue :

```
==> Recherche d'un JDK 21 ou superieur
    JDK 21 : C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot
==> Dependances (JavaFX 21.0.4 et Gson 2.11.0)
    4 bibliotheques pretes (9,1 Mo)
==> Compilation des sources
    71 fichiers source
    103 classes compilees
==> Assemblage de MiniCube.jar
    MiniCube.jar cree (9,5 Mo)
```

Pour repartir de zéro, supprimez `build/` (et `lib/` pour retélécharger les dépendances).

### Toute plateforme — avec Maven

```bash
mvn clean package
```

Le résultat est alors dans `target/MiniCube.jar`.

### Build pour une autre plateforme

Les binaires JavaFX sont propres à chaque système. Avec Maven :

```bash
mvn -Ppackage-win clean package
mvn -Ppackage-mac clean package
mvn -Ppackage-mac-aarch64 clean package
mvn -Ppackage-linux clean package
```

Sans Maven, changez la variable `$Platform` en tête de `build.ps1` (`win`, `mac`,
`mac-aarch64` ou `linux`), puis videz `lib/` avant de relancer.

---

## 3. Lancement

### Windows

```bash
.\run.bat
```

### Directement

```bash
java -jar MiniCube.jar
```

Si `java` pointe encore sur un vieux JRE, `run.bat` s'en charge : il retrouve le JDK 21
tout seul. Sinon, indiquez le chemin complet :

```bash
& "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\bin\java.exe" -jar MiniCube.jar
```

Un avertissement `Unsupported JavaFX configuration: classes were loaded from 'unnamed
module'` s'affiche au démarrage : il est **normal et sans conséquence**. Il signale que
JavaFX est chargé depuis le classpath plutôt que depuis le chemin de modules, ce qui est
exactement le fonctionnement voulu pour un jar autonome.

### En développement

```bash
mvn javafx:run
```

### Premier démarrage

1. L'assistant propose l'emplacement standard de `.minecraft` et le valide
   (`%APPDATA%\.minecraft` sous Windows).
2. Cliquez sur **Continuer**. Les réglages déjà présents dans `options.txt` sont repris.
3. Connectez-vous via la barre latérale (Microsoft ou hors-ligne).
4. Choisissez une version dans la barre du bas, puis **JOUER**.


## 4. Activer la connexion Microsoft

Le launcher utilise le flux **device code** : l'utilisateur saisit un code court sur une
page Microsoft, aucun mot de passe ne transite par l'application. Ce flux exige un
identifiant d'application Azure, gratuit et créé une seule fois.

### 4.1 Créer l'application Azure

1. Ouvrez <https://portal.azure.com> et connectez-vous.
2. Cherchez **App registrations** (Inscriptions d'applications).
3. **New registration** (Nouvelle inscription).
4. Renseignez :
   - **Name** : `MiniCube` (ou le nom de votre projet)
   - **Supported account types** : **Personal Microsoft accounts only**
   - **Redirect URI** : laissez vide
5. **Register**.
6. Copiez la valeur **Application (client) ID** — c'est votre `msClientId`.
7. Dans le menu de gauche, ouvrez **Authentication** :
   - descendez jusqu'à **Advanced settings**
   - **Allow public client flows** → **Yes**
   - **Save**

Aucun secret client n'est nécessaire : un launcher distribué ne peut pas garder un secret,
c'est précisément pourquoi le flux device code existe.

### 4.2 Renseigner l'identifiant

**Depuis l'interface** — onglet *Paramètres* → *Authentification Microsoft* → collez
l'identifiant → *Enregistrer*.

**Dans le fichier de configuration** — `~/.minicube/config.json` :

```json
{
  "msClientId": "12345678-90ab-cdef-1234-567890abcdef"
}
```

**Pour une distribution** — remplacez la constante par défaut dans
`src/main/java/com/minicube/launcher/core/Constants.java` :

```java
public static final String DEFAULT_MS_CLIENT_ID = "12345678-90ab-cdef-1234-567890abcdef";
```

### 4.3 Déroulement pour l'utilisateur

1. *Se connecter avec Microsoft* → le navigateur s'ouvre sur `microsoft.com/link`.
2. Le launcher affiche un code de huit caractères, copiable en un clic.
3. L'utilisateur saisit le code, valide, autorise l'application.
4. Le launcher détecte la validation, récupère le profil, le pseudo et le skin.

La session est ensuite renouvelée automatiquement à chaque démarrage.

---

## 5. Contenus distants

Les cinq points d'extension sont optionnels. Chacun se configure par une URL dans
`config.json` ; laissée vide, la donnée embarquée dans le jar est utilisée.

### 5.1 Actualités — `newsUrl`

```json
{
  "news": [
    {
      "title": "Ouverture de la saison 3",
      "content": "Le monde a été régénéré, les inventaires sont conservés.",
      "date": "12 mars 2026",
      "category": "Événement",
      "imageUrl": "",
      "link": "https://votre-site.fr/saison-3"
    }
  ]
}
```

### 5.2 Serveurs — `serversUrl`

```json
{
  "servers": [
    {
      "name": "Serveur principal",
      "address": "play.votre-serveur.fr",
      "port": 25565,
      "description": "Survie moddée",
      "requiredVersion": "1.20.4",
      "versionId": "",
      "iconUrl": "",
      "official": true
    }
  ]
}
```

Le serveur marqué `official` est celui dont l'état est affiché sur l'accueil.

### 5.3 Mods obligatoires — `modsManifestUrl`

```json
{
  "mods": [
    {
      "id": "fabric-api",
      "name": "Fabric API",
      "version": "0.97.0",
      "fileName": "fabric-api-0.97.0+1.20.4.jar",
      "url": "https://votre-site.fr/mods/fabric-api-0.97.0.jar",
      "sha1": "3f2a1b...",
      "size": 2216448,
      "required": true,
      "mcVersion": "1.20.4",
      "loader": "fabric"
    }
  ]
}
```

- `sha1` vide : seule la présence du fichier est vérifiée.
- `sha1` renseigné : un fichier différent est remplacé, et un téléchargement dont
  l'empreinte ne correspond pas est **rejeté puis supprimé**.
- `required: true` : le mod est installé avant chaque partie et ne peut être ni
  désactivé ni supprimé depuis l'interface.

### 5.4 Mise à jour du launcher — `updateUrl`

```json
{
  "version": "1.1.0",
  "url": "https://votre-site.fr/MiniCube-1.1.0.jar",
  "sha1": "9c8d7e...",
  "mandatory": false,
  "changelog": "Correction du lancement des versions NeoForge."
}
```

La mise à jour est **proposée**, jamais installée sans accord. Après téléchargement et
vérification, le launcher redémarre sur le nouveau jar.

### 5.5 Sauvegarde cloud — `cloudSyncUrl`

Votre service doit répondre à deux verbes sur la même adresse :

| Verbe | Attendu |
|---|---|
| `GET` | Renvoie le dernier document JSON enregistré |
| `PUT` | Enregistre le document JSON reçu |

Le champ `cloudSyncToken` est envoyé tel quel dans l'en-tête
`Authorization: Bearer <jeton>`.

Le dossier de jeu, le chemin Java, les comptes et le jeton lui-même **ne sont jamais
transmis** : seules les préférences d'usage sont synchronisées.

---

## 6. Dépannage


### « Le terme build.bat n'est pas reconnu »

PowerShell n'exécute jamais un programme du dossier courant sans préfixe explicite.
Écrivez `.\build.bat` (avec le point et la barre oblique inverse), ou passez par
`cmd.exe` où `build.bat` suffit.

### « ... build.ps1 ne peut pas être chargé, l'exécution de scripts est désactivée »

C'est la stratégie d'exécution PowerShell, `Restricted` par défaut sur Windows. Passez
par `.\build.bat` : les fichiers `.bat` n'y sont pas soumis et appellent PowerShell avec
le contournement nécessaire. Inutile de modifier vos réglages de sécurité.

### « Aucun package ne correspond aux critères sélectionnés » (winget, Maven)

Apache Maven n'est pas publié dans le catalogue winget. Il n'est de toute façon pas
nécessaire sous Windows — voir §1.2.

### « Aucun JDK 21 ou supérieur n'a été trouvé »

Le JDK n'est pas installé, ou bien il l'est mais dans un emplacement inhabituel.
Installez-le puis relancez :

```bash
winget install EclipseAdoptium.Temurin.21.JDK
```

Un JDK présent mais absent du `Path` est normalement détecté par `build.bat`, qui
inspecte directement les dossiers d'installation. Si le vôtre est ailleurs, définissez
`JAVA_HOME` sur sa racine.

### « JavaFX runtime components are missing »

Le jar généré démarre par `Bootstrap`, jamais par `MiniCubeLauncher` : c'est ce détour qui
permet à JavaFX de fonctionner depuis le classpath. Lancez bien
`java -jar MiniCube.jar`, sans `-cp` ni classe explicite.

### « Unsupported JavaFX configuration: classes were loaded from 'unnamed module' »

Simple avertissement, pas une erreur. Il est attendu avec un jar autonome et n'empêche
rien.

### « release version 21 not supported »

Le compilateur utilisé est trop ancien. Avec `build.bat`, cela n'arrive pas : le script
choisit lui-même un JDK 21. Avec Maven, vérifiez la ligne *Java version* de `mvn -v` et
corrigez `JAVA_HOME`.

### « Aucun identifiant d'application Azure n'est configuré »

Attendu tant que `msClientId` n'est pas renseigné (voir §4). Le mode hors-ligne reste
utilisable en attendant.

### « Ce compte Microsoft ne possède pas de profil Xbox »

Le compte n'a jamais été associé à Xbox Live. Connectez-vous une fois sur
<https://www.xbox.com> pour créer le profil, puis réessayez.

### « Ce compte Microsoft ne possède pas Minecraft Java Edition »

Le compte est valide mais sans licence Java Edition. Une licence Bedrock ou Game Pass PC
ne suffit pas toujours : lancez le jeu une fois avec le launcher officiel pour activer
le droit, puis réessayez.

### Le jeu se ferme immédiatement

1. Ouvrez l'onglet **Journal** : la sortie du jeu y est reproduite intégralement.
2. Causes les plus fréquentes :
   - **RAM trop élevée** — au-delà de la mémoire physique disponible, la JVM refuse de
     démarrer. Réduisez la valeur dans *Paramètres*.
   - **Mauvaise version de Java** — les versions 1.18 et suivantes exigent Java 17,
     les versions 1.20.5 et suivantes Java 21. La détection est automatique, mais un
     chemin Java forcé dans les paramètres la court-circuite.
   - **Mod incompatible** — désactivez les mods récemment ajoutés depuis l'onglet Mods.
3. Le journal complet est aussi sur disque :
   `~/.minicube/logs/launcher.log`.

### Les shaders ne s'appliquent pas

Le launcher écrit la sélection dans la configuration d'Iris et d'OptiFine, mais ne peut
pas les installer. Vérifiez que la version lancée contient bien **Iris** (avec Fabric)
ou **OptiFine**. Sur une version vanilla, aucun shader ne peut fonctionner.

### « Le dossier est en lecture seule »

Le dossier `.minecraft` choisi n'est pas accessible en écriture. Sous Windows, cela arrive
lorsqu'il est placé dans `C:\Program Files`. Choisissez un dossier dans votre répertoire
utilisateur.

### Le ping affiche « Délai dépassé » sur tous les serveurs

Un pare-feu bloque les connexions sortantes du launcher. Autorisez `java` / `javaw` dans
le pare-feu, ou testez depuis un autre réseau.

### Réinitialiser complètement

Fermez le launcher, puis supprimez le dossier de configuration :

```bat
rmdir /s /q "%USERPROFILE%\.minicube"
```

```bash
rm -rf ~/.minicube
```

Le dossier `.minecraft` n'est pas touché : vos mondes et vos mods sont conservés.

---

## 7. Distribution à vos utilisateurs

1. Renseignez `DEFAULT_MS_CLIENT_ID` et les URL par défaut dans `Constants.java`.
2. Adaptez `resources/config/servers.json` et `news.json`.
3. Compilez un jar par plateforme cible (voir §2).
4. Distribuez le jar accompagné du prérequis : **Java 21 ou supérieur installé**.

Pour éviter d'exiger Java chez l'utilisateur final, `jpackage` (fourni avec le JDK)
produit un exécutable natif embarquant un runtime :

```bash
jpackage --input . --name "MiniCube" --main-jar MiniCube.jar --main-class com.minicube.launcher.Bootstrap --type msi
```

Remplacez `--type msi` par `dmg` sous macOS ou `deb` sous Linux.
