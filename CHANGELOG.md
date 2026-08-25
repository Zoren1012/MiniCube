# Journal des versions — MiniCube

Le numéro suit trois niveaux :

- **majeur** — rupture de compatibilité (format de configuration ou de comptes modifié
  sans migration, changement du dossier de données) ;
- **mineur** — nouvelle fonctionnalité, correctif de sécurité notable, refonte visuelle,
  changement de comportement visible ;
- **correctif** — bug, ergonomie, retouche visuelle, documentation liée au code.

La version est définie dans `Constants.APP_VERSION` et reprise dans `pom.xml`. Ces deux
emplacements changent ensemble ; `package.ps1` lit le premier et le transmet aux outils.

---

## 1.2.2 — 25 août 2026

- **Publication automatique sur GitHub.** `.github/workflows/release.yml` fabrique
  l'installeur sur une machine Windows dès qu'une étiquette `v*` est poussée, et
  l'attache à une publication. Le dépôt ne contient donc que du code : le binaire vit
  dans les *Releases*, sans peser sur l'historique.
- **`.gitattributes` ajouté.** Sans lui, Git normalise les fins de ligne selon la
  machine : un `build.sh` récupéré sous Windows repartirait avec des CRLF, et bash
  refuserait de l'exécuter sous Linux. Les `.bat` gardent des CRLF, les `.sh` des LF.

---

## 1.2.1 — 25 août 2026

- **README entièrement réécrit.** L'ancien mélangeait deux publics — celui qui veut
  utiliser MiniCube et celui qui veut le modifier — et avait grossi par accumulation.
  Le nouveau part de l'usage : ce que fait le logiciel, comment l'installer, ce que
  chaque onglet apporte, avec des captures réelles. Les parties techniques viennent
  après.
- **Captures d'écran ajoutées** dans `docs/images/`, prises sur l'application réelle.
- **Différence entre compte Microsoft et hors-ligne explicitée** dans un tableau : c'est
  la source de confusion la plus fréquente, notamment pour les skins.
- La version n'est plus écrite en dur dans le README : elle ne vit plus que dans
  `Constants.APP_VERSION` et `pom.xml`.

---

## 1.2.0 — 25 août 2026

### Distribution

- **Exécutable natif et installeur Windows.** `package.bat` produit désormais deux
  livrables : `dist\MiniCube\MiniCube.exe`, application autonome avec son runtime Java
  intégré, et `dist\MiniCube-Setup-<version>.exe`, un installeur classique.
  **L'utilisateur final n'a plus besoin d'installer Java, ni de passer par un `.bat`.**

  - L'exécutable est produit par `jpackage`, fourni avec le JDK.
  - L'installeur est produit par Inno Setup s'il est présent ; sinon seul l'exécutable
    autonome est fabriqué, et le script le signale.
  - L'installation se fait **par utilisateur**, dans `%LOCALAPPDATA%\Programs\MiniCube` :
    aucune élévation de privilèges n'est demandée. Raccourci menu Démarrer, raccourci
    bureau optionnel, désinstalleur inclus.

- **Icône de l'application.** Le cube isométrique du logo, décliné en six tailles.
  Les tailles courantes sont stockées en bitmap et seule la 256 en PNG : les anciennes
  bibliothèques graphiques de Windows ne savent pas lire un PNG dans un `.ico` et
  échouent alors sur toutes les tailles.

- **Runtime taillé au plus juste.** Dix modules du JDK au lieu de la totalité, ce qui
  ramène l'application de plus de 250 Mo à 88 Mo, et l'installeur à 27 Mo.
  `jdk.crypto.ec` en fait partie : sans lui l'application démarrerait normalement, mais
  toute connexion à Microsoft ou à Mojang échouerait. Vérifié par un test qui interroge
  les trois services depuis un runtime identique.

- **La version n'est plus saisie qu'une fois.** `package.ps1` la lit dans
  `Constants.APP_VERSION` et la transmet à `jpackage` et à Inno Setup.

---

## 1.1.0 — 25 août 2026

Audit complet du projet : sécurité, performances, ergonomie. Le détail figure dans
[AUDIT.md](AUDIT.md).

### Sécurité

- **Le jeton de session ne fuit plus dans le journal.** La ligne de commande du jeu
  contient `--accessToken` ; le journal est précisément ce qu'on partage pour signaler
  un bug. Le masquage est appliqué au centre, dans `Log.log()`.
- **Mise à jour du launcher : HTTPS et SHA-1 désormais obligatoires.** Le jar téléchargé
  est exécuté ; la vérification d'empreinte était conditionnelle et le protocole libre.
- **La sauvegarde cloud ne peut plus imposer quel programme s'exécute.** `javaPath` et
  `extraJvmArgs` ne sont plus jamais restaurés depuis un service distant.
- **Contrôle du protocole des liens ouverts.** Seuls `http` et `https` sont acceptés ;
  `file:` et les protocoles applicatifs sont refusés.
- **Restriction réelle du fichier de comptes.** `File.setReadable` ne touche pas les ACL
  NTFS et renvoyait pourtant un succès ; remplacé par `AclFileAttributeView`.

### Performances

- **Vérification d'intégrité : 1 367 ms → 404 ms** par lancement (4 590 fichiers,
  425 Mo mesurés). Un cache retient taille, date et empreinte ; le bouton *Vérifier les
  fichiers* l'ignore volontairement.
- **Journal** : le flux reste ouvert au lieu d'être rouvert à chaque ligne.
- **Détection Java** : lecture du fichier `release` au lieu de lancer un processus par
  runtime candidat.
- **Texture du skin** : agrandissement par tableaux d'entiers, plus 262 144 allocations
  par changement.

### Fonctionnalités

- **Installation d'une version depuis le launcher.** Le téléchargement existait, aucune
  interface n'y donnait accès : sur une machine neuve, il n'y avait rien à lancer.
- **Gestion de plusieurs comptes.** La fenêtre de connexion liste les comptes
  enregistrés, permet d'en activer un ou de l'oublier.
- **Le skin importé peut enfin être appliqué.** Sur un compte hors-ligne, il est
  mémorisé et conservé d'une session à l'autre — aperçu 3D et vignette. Signalé par
  l'utilisateur.
- **Confirmation avant suppression** d'un mod ou d'un pack de shaders, bouton neutre
  présélectionné.

### Corrections

- **Journalisation fichier silencieusement désactivée.** `Files.createFile` échoue si le
  fichier existe, et la rotation n'archivait que les journaux non vides : un
  `launcher.log` de 0 octet coupait toute écriture pour les sessions suivantes.
- **Les premières lignes restaient en tampon.** Un fil démon les libère au bout de
  700 ms, indépendamment de toute écriture.
- **Carte d'aperçu du skin comprimée**, ses libellés tronqués (« R... » au lieu de
  « Recentrer »).
- **La vignette de la barre latérale** ignorait le skin importé localement.

---

## 1.0.1 — 24 août 2026

- Passage de version seul, sans changement de code.

---

## 1.0.0 — 24 août 2026

Première version.

- Lancement des versions installées, y compris Forge, Fabric, Quilt, NeoForge et
  OptiFine (fusion des descripteurs `inheritsFrom`, évaluation des règles
  conditionnelles, deux formats d'arguments).
- Authentification Microsoft en flux *device code*, et comptes hors-ligne.
- Huit onglets : Accueil, Skin, Serveurs, Graphismes, Shaders, Mods, Paramètres, Journal.
- Aperçu 3D du personnage, maillages construits à la main d'après la disposition
  officielle des textures.
- Interrogation des serveurs par le protocole *Server List Ping*, implémenté sans
  dépendance.
- Interface en verre translucide sur fond animé, thèmes clair et sombre, français et
  anglais.
- Compilation sans Maven : `build.ps1` télécharge les dépendances et appelle `javac`.
