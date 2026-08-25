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

## 1.6.0 — 25 août 2026

### Accueil vocal

- **Le launcher vous salue à voix haute au démarrage.** « Bienvenue *votre pseudo* ! » est
  prononcé pendant que la fenêtre apparaît. Le pseudo est celui de votre compte MiniCube ;
  à défaut, celui du compte de jeu actif ; sans aucun des deux, l'accueil reste générique.
- **La voix vient de Windows, pas d'Internet.** Sous Windows 10 et 11, la synthèse utilise
  les voix *OneCore* (Julie, Paul, Hortense en français), nettement plus naturelles que les
  anciennes voix SAPI, avec repli automatique sur ces dernières si elles manquent. Sur macOS
  et Linux, `say` et `spd-say` prennent le relais. **Rien n'est envoyé à un service
  extérieur : votre pseudo ne quitte pas la machine.**
- **Prononcé une fois, rejoué ensuite.** La phrase est synthétisée puis conservée dans le
  cache : les démarrages suivants se contentent de relire le fichier, sans lancer le moindre
  processus. Comptez environ une seconde la première fois, rien du tout après.
- **Désactivable.** *Paramètres → Comportement → Accueil vocal au démarrage*. La
  réinitialisation des préférences vide aussi les phrases déjà enregistrées.
- La synthèse et la lecture se font sur un fil séparé : elles ne retardent jamais
  l'affichage de la fenêtre, et un échec reste silencieux — l'accueil est un agrément, pas
  une fonction dont le launcher dépend.

### Protection

- **Le pseudo ne figure jamais dans le script de synthèse.** Sous Windows, prononcer une
  phrase suppose d'exécuter du PowerShell : y insérer le pseudo reviendrait à exécuter du
  code choisi par l'utilisateur, et un pseudo tel que `"; Remove-Item …` suffirait. Le
  script est donc une constante, transmise encodée pour échapper à toute règle de citation,
  et la phrase voyage par une variable d'environnement que PowerShell lit comme une simple
  donnée. Quatre formes d'injection ont été essayées puis vérifiées sans effet.
- PowerShell est appelé par son chemin absolu dans `System32`, jamais par le `PATH` qu'un
  autre logiciel pourrait avoir détourné.
- Les caractères de contrôle sont retirés du pseudo et la phrase est plafonnée : rien
  d'aberrant ne part au moteur de synthèse.

---

## 1.5.0 — 25 août 2026

### Compte MiniCube

- **Page de compte servie par le launcher.** Un bouton dans *Paramètres* ouvre dans le
  navigateur une page de connexion aux couleurs de MiniCube : création du compte,
  connexion, profil, déconnexion, suppression. Le launcher héberge lui-même cette page ;
  il n'y a aucun site à louer, aucune inscription ailleurs, rien à configurer.
- **Ce que le compte contient.** Un pseudo, un rôle (Membre, VIP, Modérateur,
  Administrateur), une couleur, et les statistiques d'usage : nombre de parties lancées,
  temps de jeu cumulé, dernière version utilisée et les cinq dernières versions jouées.
  Les parties et le temps sont comptés automatiquement à chaque lancement.
- **Deux limites, dites franchement.** Un compte MiniCube n'autorise **pas** à jouer sur
  un serveur en `online-mode=true` : seul Microsoft peut le faire, et l'onglet de
  connexion Microsoft reste la seule voie pour jouer en ligne. Et tant que le compte est
  géré sur votre machine, le rôle est **déclaratif** : vous le choisissez vous-même.
- **Prêt pour un vrai serveur.** Les routes (`/api/state`, `/api/login`, …) forment un
  contrat qu'un serveur distant peut reprendre à l'identique. Passer d'une gestion locale
  à un site hébergé ne demanderait que de changer l'adresse.

### Protection du compte

- **Le mot de passe n'est jamais enregistré.** Seule son empreinte l'est, dérivée par
  PBKDF2-HMAC-SHA256 avec 210 000 itérations et un sel tiré au hasard pour chaque compte.
  La comparaison se fait en temps constant. Le fichier `profile.json` est restreint au
  seul propriétaire du compte Windows.
- **Le serveur n'écoute que sur 127.0.0.1**, sur un port attribué par le système. Aucune
  autre machine du réseau ne peut l'atteindre, même sur un réseau partagé.
- **Une page consultée ailleurs ne peut rien déclencher.** Les actions qui modifient le
  compte exigent une requête `POST` et une origine reconnue : une simple image pointant
  vers `/api/delete` ne supprime plus rien. Un pseudo inconnu et un mot de passe erroné
  renvoient le même message, pour ne pas révéler quels comptes existent.
- La page ne charge aucune ressource extérieure ; une politique de sécurité du contenu le
  formalise et les textes affichés le sont toujours par `textContent`, jamais par `innerHTML`.

### Corrections

- **Une adresse inconnue bloquait la page.** L'échange HTTP n'était pas refermé sur la
  réponse 404, ce qui figeait la connexion réutilisée par le navigateur : toute requête
  suivante restait sans réponse. Comme les navigateurs réclament `/favicon.ico` d'eux-mêmes,
  le cas se serait produit à chaque ouverture.
- Le serveur de la page est désormais arrêté à la fermeture du launcher.

---

## 1.4.0 — 25 août 2026

### Animation de démarrage

- **Écran d'accueil animé au lancement.** Le cube apparaît en grandissant avec un léger
  dépassement, le nom et la version montent en fondu, et une bille parcourt une barre
  pendant l'initialisation.

- **Il affiche les étapes réelles**, pas une jauge décorative : lecture de la
  configuration, vérification des dossiers, préparation de l'interface. Si l'une d'elles
  traîne, on sait laquelle. Une barre indéterminée plutôt qu'un pourcentage, parce que
  ces étapes n'ont pas de durée prévisible et qu'une jauge qui saute de 20 à 90 %
  n'informe personne.

- **La fenêtre principale apparaît en fondu**, dans la continuité de l'écran d'accueil.
  Afficher d'un coup une fenêtre de mille pixels après une disparition en fondu
  produisait une rupture.

- **La seule attente ajoutée évite un clignotement.** L'initialisation étant plus rapide
  que l'animation d'entrée, l'écran reste visible le temps qu'elle se termine — environ
  une seconde. Au-delà, il s'efface dès que l'interface est prête.

- Au premier démarrage, l'écran s'efface **avant** l'assistant : il est affiché au
  premier plan et serait resté par-dessus la fenêtre modale.

---

## 1.3.4 — 25 août 2026

- **Le README documente enfin la publication d'une version.** `version.bat`, ses
  garde-fous et la raison pour laquelle l'étiquette doit correspondre au code n'étaient
  décrits nulle part : ils n'existaient que dans une conversation.
- **Les sept états de l'onglet Mise à jour sont expliqués**, avec ce que chacun signifie
  réellement. « Aucune version publiée » et « Dépôt introuvable » ne veulent pas dire la
  même chose que « à jour ».
- Sommaire compact en tête de page.

---

## 1.3.3 — 25 août 2026

### L'onglet Mise à jour ne ment plus

L'onglet affichait **« MiniCube est à jour »** dans tous les cas où aucune mise à jour
n'était retournée — y compris quand la vérification avait échoué, quand le dépôt était
introuvable, ou quand une publication avait été refusée faute d'empreinte. L'utilisateur
croyait donc posséder la dernière version alors que personne n'avait pu le lui confirmer.

La vérification renvoie désormais un état parmi sept, chacun avec son message :

| État | Ce qui s'affiche |
|---|---|
| `AVAILABLE` | La version disponible, avec ses nouveautés |
| `UP_TO_DATE` | Vous avez bien la dernière version publiée |
| `NO_RELEASE` | Le dépôt ne contient encore aucune publication |
| `NOT_FOUND` | Le dépôt est introuvable, vérifiez son nom |
| `NOT_CONFIGURED` | Aucune source n'est renseignée |
| `REJECTED` | La publication existe mais a été refusée, et pourquoi |
| `ERROR` | La vérification n'a pas abouti, avec la cause |

Un `404` donne lieu à une seconde requête, uniquement dans ce cas, pour distinguer un
dépôt absent d'un dépôt sans publication. Le message vaut cette requête supplémentaire.

Le quota GitHub atteint (`403`) est également identifié et expliqué.

---

## 1.3.2 — 25 août 2026

### Publier une version sans se tromper

- **`version.bat` prépare une version d'un seul geste.** Le numéro vit dans
  `Constants.APP_VERSION` et dans `pom.xml`, et doit correspondre à l'étiquette Git. Le
  script met les trois d'accord, compile pour vérifier, puis crée le commit et
  l'étiquette. Il ne pousse rien : la commande d'envoi est affichée à la fin.

  Il refuse une version au format invalide, une version qui recule, une étiquette déjà
  existante, ou l'absence d'entrée correspondante dans `CHANGELOG.md` — ce texte est
  celui que verront vos joueurs dans l'onglet Mise à jour.

- **Le workflow refuse une étiquette qui ne correspond pas au code.** Le projet est
  compilé d'après `APP_VERSION` mais publié sous le nom de l'étiquette : en cas de
  divergence, le launcher aurait proposé indéfiniment une mise à jour installant la
  version déjà présente. Vos joueurs auraient tourné en rond sans comprendre. La
  publication échoue désormais avec un message explicite.

---

## 1.3.1 — 25 août 2026

- **Fiche d'installation Discord.** `discord-annonce.ps1` publie dans un salon une fiche
  expliquant le téléchargement, l'installation, le premier démarrage et où regarder en
  cas de problème. La version et le lien de téléchargement sont déduits du code.

  - **Le webhook est passé en paramètre, jamais écrit dans un fichier.** C'est un
    secret : quiconque le possède peut publier dans le salon. Le dépôt étant destiné à
    être public, un webhook en dur y serait exposé.
  - **Le texte vit dans `discord/fiche-installation.json`**, modifiable sans toucher au
    script. Ce fichier séparé évite aussi un piège d'encodage : PowerShell 5.1 lit un
    `.ps1` sans marque d'ordre des octets en ANSI, et massacrerait les accents.
  - **Les limites de Discord sont vérifiées avant l'envoi.** Discord répond 400 sans
    jamais dire quel champ dépasse ; le script le dit, lui.
  - Aperçu avec `-Apercu`, confirmation demandée avant publication, messages d'erreur
    explicites pour les cas 401, 404, 429 et 400.

---

## 1.3.0 — 25 août 2026

### Onglet Mise à jour

- **MiniCube se met à jour depuis vos publications GitHub.** Un nouvel onglet affiche la
  version installée, recherche une version plus récente, montre les nouveautés publiées
  et installe. Le dépôt se règle au format `proprietaire/nom` dans les Paramètres.

- **Le fichier récupéré s'adapte à l'installation.** Une installation complète télécharge
  l'installeur et le lance ; une version portable télécharge le jar et redémarre dessus.
  La distinction repose sur `jpackage.app-path`, renseigné par les exécutables produits
  par jpackage. Prendre l'un pour l'autre laisserait l'utilisateur avec un fichier
  inutilisable.

- **Une publication sans empreinte est refusée.** Le fichier va être exécuté : MiniCube
  exige un `.sha256` publié à côté et vérifie le téléchargement avant de le lancer.
  Vérifié sur une vraie publication tierce, correctement rejetée faute d'empreinte.

- **Le workflow publie les empreintes.** Chaque fichier est accompagné de son `.sha256` ;
  sans cela, le launcher refuserait ses propres mises à jour.

- **Plus de fenêtre surgissante au démarrage.** L'ancienne version proposait le
  téléchargement dans une boîte de dialogue au lancement, ce qui coupe quelqu'un qui veut
  simplement jouer. Une notification renvoie désormais vers l'onglet, où l'action est
  délibérée.

- Le service accepte SHA-256 comme SHA-1 pour les descripteurs auto-hébergés.

### Corrections

- **`Get-ChildItem -Include` sans `-Recurse` ne renvoie rien.** Le workflow aurait publié
  une release vide tout en signalant un succès. Corrigé en utilisant un chemin à joker.

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
