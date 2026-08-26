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

## 1.15.0 — 26 août 2026

### Un onglet Discord

- **L'invitation du serveur est intégrée au launcher** : `https://discord.gg/fxEnUhmUHj`,
  affichée en clair, avec un bouton pour l'ouvrir et un autre pour la copier.
- **Elle n'est pas un réglage.** C'est une constante du programme, et l'onglet ne propose
  aucun champ de saisie. La rendre modifiable permettrait de rediriger les joueurs
  ailleurs en éditant un fichier, ce qui n'a aucun usage légitime.
- Le lien sort par le même filtre que tous les liens du launcher : seule une adresse web
  reconnue est transmise au système, jamais un `file:` ou un protocole applicatif.

### Un onglet Boutique et soutien, annoncé comme non terminé

- La page dit **« En cours de développement »** au lieu de rester vide : une page vide se
  lit comme une panne, une page annoncée se lit comme une attente.
- Ce qui est prévu y est listé — cosmétiques, rôles d'affichage, soutien à l'hébergement —
  avec la seule promesse qui compte : **rien ne donnera d'avantage en jeu**.

### Une animation par thème

- L'animation de cette page **est dessinée dans le langage du thème actif**. En style
  Minecraft, cinq blocs carrés vert-herbe et terre sautent d'un mouvement linéaire, sans
  aucun lissage — comme tout ce qui bouge dans le jeu. Dans les thèmes verre, cinq orbes
  rondes en dégradé respirent avec un amorti aux deux extrémités.
- Elle **s'arrête quand la fenêtre est réduite** et quand la page n'est pas affichée. Une
  animation décorative qui continue de tourner derrière une fenêtre iconifiée ne coûte
  rien de visible et consomme quand même du processeur.

### Vérifications

- Cinq contrôles s'ajoutent à `SecurityCheck` : l'invitation est en `https`, vise bien
  `discord.gg`, passe le filtre des liens, la constante est `final`, et **aucun champ de
  `LauncherSettings` ne peut la remplacer**. Cette dernière vérification est le garde-fou
  de la promesse « non modifiable » : elle échouera le jour où quelqu'un ajoutera le
  réglage.
- `IdleCheck` compte désormais les images de cette animation en plus de celles du fond :
  **279 images fenêtre ouverte, 0 fenêtre réduite**. La mesure a servi : la mise en pause
  n'était accrochée qu'à la fenêtre déjà attachée, or la page est construite avant que la
  fenêtre existe — l'animation ne s'arrêtait donc jamais. La scène est maintenant
  surveillée elle aussi. Suite complète : **154 vérifications**.

---

## 1.14.0 — 26 août 2026

### L'historique des versions dans l'onglet Mise à jour

- **Une carte « Historique des versions »** liste les quinze dernières publications avec
  ce qui a changé dans chacune, leur date et un lien vers la publication.
- Elle est chargée **à l'ouverture de l'onglet**, indépendamment de la vérification de
  mise à jour : savoir ce qui a changé vaut d'être lu même quand on est déjà à jour, ne
  serait-ce que pour retrouver quand telle chose est arrivée.
- Votre version y est signalée, et celles plus récentes aussi : sans ce repérage, une
  liste de numéros ne dit pas où l'on se situe.

### Les notes de publication disaient enfin quelque chose

- **Le vrai problème n'était pas l'affichage.** Chaque publication portait le même texte
  générique — « Installeur Windows, aucun prérequis… » — écrit en dur dans le workflow.
  L'onglet affichait donc consciencieusement une phrase identique d'une version à l'autre.
- Le workflow **extrait désormais du CHANGELOG la section de la version publiée** et
  l'utilise comme notes. Si la section manque, un avertissement est émis dans la
  fabrication plutôt que de publier en silence un texte creux.
- Le balisage Markdown est **nettoyé pour l'affichage** : le launcher montre du texte
  brut, où les `###` et les `**` resteraient visibles. Seul le balisage est retiré, pas
  un mot du contenu.

> Les publications déjà en ligne gardent leur texte générique : il est figé sur GitHub.
> Pour le remplacer après coup :
> `gh release edit v1.12.0 --notes-file notes.md`

---

## 1.13.1 — 26 août 2026

### Le menu : deux défauts corrigés

- **La signature se posait sur la version du jeu.** Minecraft écrit déjà « Minecraft
  1.21.11/Fabric » dans le coin inférieur gauche du menu ; la signature s'affichait
  par-dessus. Elle est remontée dans le coin supérieur gauche, qui est libre.
- **Le bouton « Rejoindre » ne s'affichait jamais.** Il attendait une adresse de serveur
  dans le fichier de configuration du mod — que personne n'allait éditer pour découvrir
  une fonction dont il ignorait l'existence.

  Le mod lit désormais la **liste de serveurs du launcher** (`custom-servers.json`) au
  premier lancement et en retient le premier : le bouton apparaît tout seul. Lecture
  seule, sur un fichier du même utilisateur ; rien n'est écrit dans les données de
  MiniCube, et l'absence du launcher n'est pas une erreur — le mod fonctionne alors sans
  ce bouton.

Mod en version **1.1.0**.

---

## 1.13.0 — 26 août 2026

### Le mod devient obligatoire

- **MiniCube HUD est désormais installé d'office et non désactivable.** Le launcher lit le
  manifeste des mods requis à l'adresse de la **dernière publication** du dépôt — celle-ci
  suit automatiquement la version la plus récente, sans rien à reconfigurer.
- Le manifeste est **généré à la fabrication**, avec l'empreinte SHA-1 réelle du jar qui
  vient d'être construit. Une empreinte figée dans le dépôt serait fausse dès la
  compilation suivante : Gradle ne produit pas deux jars identiques à l'octet près.
- Le jar du mod et son manifeste sont joints à chaque publication GitHub, à côté de
  l'installeur.
- L'onglet *Mods* refusait déjà de désactiver ou de supprimer un mod requis, côté interface
  comme côté service. Rien n'a eu à changer là.

> **Un mod client ne peut pas être imposé par un serveur.** Rien ne permet de vérifier de
> façon fiable ce qui tourne chez un joueur : quiconque lance le jeu autrement que par
> MiniCube pourra s'en passer. Ce que le launcher garantit, c'est que ses utilisateurs
> l'auront toujours, à jour et intact.

### Le menu du jeu aux couleurs de la communauté

- **Une signature** en bas à gauche du menu principal : « MiniCube » et le nom de votre
  communauté.
- **Un bouton « Rejoindre »** sous ceux du jeu, qui connecte directement à votre serveur.
  Il passe par le même chemin que le menu multijoueur : l'écran de connexion s'affiche,
  avec ses messages d'erreur habituels si le serveur est injoignable.
- Placé **sous** les boutons du jeu, pas au milieu de la pile : insérer un bouton entre
  eux les décalerait tous, et un joueur habitué cliquerait à côté.
- Rien n'est retiré du menu, et les deux ajouts se coupent dans la configuration.

---

## 1.12.0 — 26 août 2026

### Onglet Style

- Nouvel onglet **Style**, qui rassemble tout ce qui touche à l'apparence. Le thème se
  choisit sur un **aperçu miniature** plutôt que dans une liste déroulante : trois
  ambiances très différentes cohabitent, et un nom seul ne dit pas laquelle on prend.
- **Couleur d'accent personnalisable.** Une seule couleur suffit : survol, appui, voile et
  lueur en sont dérivés. Elle se repose sur la racine de la fenêtre, donc sans toucher aux
  feuilles de style.
- L'image de fond a rejoint cet onglet. Le thème et l'image ont été **retirés des
  Paramètres** : les régler à deux endroits finissait par produire deux vérités.

### Thème Minecraft

- Un troisième thème reprend le langage visuel du launcher officiel et des menus du jeu :
  **surfaces opaques et sombres, aucun angle arrondi, bordures en biseau** — claire en
  haut à gauche, sombre en bas à droite — et le vert du bouton JOUER officiel.
- C'est l'opposé exact du thème Liquid Glass, et le fichier de style le traite comme tel :
  il remplace aussi les **formes**, pas seulement les couleurs. Les interrupteurs
  redeviennent des cases à cocher avec une croix verte : un rail arrondi n'existe nulle
  part dans Minecraft.
- Le fond animé **s'éteint** sous ce thème : des halos dérivant derrière des panneaux
  opaques ne se verraient pas, et coûteraient pour rien.
- **La police du jeu n'est pas embarquée** — elle n'est pas redistribuable. MiniCube
  l'utilise si vous l'avez installée, sinon il retient une police à chasse fixe du système
  qui en donne l'esprit.

### Corrections

- **JavaFX ne parcourt pas une liste de polices** comme le fait un navigateur : il retient
  la première et retombe sur la police par défaut si elle manque. Nommer « Minecraft » en
  tête d'une liste ne servait donc à rien. Le choix se fait désormais à l'exécution, parmi
  les familles réellement installées.
- **Une image de fond rendait le contenu illisible.** Le voile posé sous le contenu était
  calibré pour les halos, pas pour une photographie. Il se densifie maintenant tant qu'une
  image est en place : l'image reste une ambiance au lieu de concurrencer les textes.
- L'étiquette de l'aperçu « Verre clair » était écrite dans la couleur du thème qu'elle
  décrit, donc invisible sur une carte sombre.

---

## 1.11.0 — 26 août 2026

### Performances : ce que les mesures ont montré

Avant d'optimiser, j'ai mesuré. Le launcher atteignait **déjà** les cibles demandées :
136 images/s, démarrage en 1,3 s, 37 Mo de mémoire, 0,2 % de processeur au repos, et des
onglets qui s'ouvrent en 0 à 25 ms. Deux constats méritent d'être connus :

- **La cadence suit l'écran.** Le rendu passe par Direct3D avec synchronisation
  verticale : sur un écran 144 Hz, MiniCube affiche 144 images par seconde. **Forcer
  `javafx.animation.pulse=120` a été essayé et écarté** : sur cette machine, cela
  dégradait le temps par image (7,94 ms contre 6,80) et *plafonnerait* un écran 144 Hz.
- **Détecter la fréquence de l'écran coûte 424 ms**, soit un tiers du temps de démarrage.
  Écarté également.

### Ce qui a réellement été gagné

- **Le fond animé s'arrête quand la fenêtre est réduite.** Le cas n'est pas théorique :
  par défaut le launcher reste ouvert pendant la partie, et trois dégradés continuaient
  d'être recalculés à la cadence de l'écran avec le processeur graphique dont le jeu a
  besoin. Vérifié : 346 images animées fenêtre visible, **0 une fois réduite**.
- **Les compteurs système ne sont plus lus sur le fil de l'interface.** Sur Windows, le
  premier appel à `getProcessCpuLoad()` coûte **571 ms**, et chacun des suivants une
  vingtaine de millisecondes — de quoi perdre une image par seconde. Le relevé se fait
  désormais à l'écart, l'interface ne lit qu'une valeur déjà calculée. L'ouverture de
  l'onglet Performances est passée de **600 ms à 16 ms**, et le processeur au repos de
  9,3 % à 0,4 %.
- Les mesures s'arrêtent dès qu'on quitte l'onglet : rien ne tourne pour une page que
  personne ne regarde.

### Tableau de bord des performances

- Nouvel onglet **Performances** : cadence du launcher, processeur, mémoire du launcher,
  mémoire système, version de Java et **temps de chargement de Minecraft**.
- Ce dernier chiffre n'est pas le temps de lancement du processus, qui ne dirait rien :
  c'est le délai entre l'appui sur *Jouer* et le moment où le jeu signale qu'il a atteint
  son menu principal, dans sa propre sortie. C'est ce que le joueur ressent.
- L'onglet affiche aussi **l'état de vos serveurs**, interrogés à son ouverture.

### Optimisation automatique

- Un bouton analyse la machine et la configuration, et **propose la correction avec le
  constat** — un diagnostic qui nomme un problème sans dire comment le résoudre ne sert
  à rien. Chaque proposition s'applique d'un clic, ou toutes d'un coup.
- Sont examinés : la mémoire allouée (trop, ou trop peu), le Java exigé par la version
  sélectionnée, la distance de rendu au regard de la mémoire, l'espace disque, les
  shaders activés sans Iris ni OptiFine pour les lire, et **la carte graphique**.
- Ce dernier point vaut le détour : sur un portable équipé des deux, Windows lance
  souvent Java sur la puce intégrée. Le jeu rame sans raison apparente et personne ne
  pense à vérifier. MiniCube le détecte et dit quoi faire.

### Profils de jeu

- **Chaque profil a sa version, son dossier de jeu — donc ses mods, ses shaders, ses
  mondes — sa mémoire et ses réglages graphiques.** Un pack moddé lourd et une partie
  vanilla légère cohabitent sans se marcher dessus.
- Sélecteur de profil dans la barre du bas, gestion complète derrière l'engrenage :
  création à partir d'un modèle (Vanilla, Fabric, Forge, Moddé, Personnalisé), renommage,
  suppression.
- À la création, le profil reçoit **son propre dossier** ou partage le `.minecraft`
  principal. Le profil créé au premier démarrage partage l'existant : personne ne doit
  perdre ses mondes parce que les profils sont apparus.
- **Supprimer un profil n'efface jamais ses fichiers.** Le profil disparaît de la liste,
  les mondes restent sur le disque, et le message le dit explicitement.

---

## 1.10.0 — 26 août 2026

### Refonte visuelle

- **Le fond ne salit plus le contenu.** Il portait cinq halos de couleurs opposées —
  violet, bleu, turquoise, rose — qui se recouvraient en donnant des teintes boueuses, et
  traversaient les cartes en pleine intensité. Il n'en reste que **trois, tous dans la
  même famille de violets et de bleus**, et un voile sombre est posé sous le contenu :
  les halos restent visibles sur les bords, mais cessent de passer derrière les textes.
- **Les cartes se détachent enfin.** Elles superposent désormais le voile de verre et un
  reflet très court qui éclaircit leur bord supérieur — ce qui fait lire une surface qui
  capte la lumière plutôt qu'un rectangle gris. Bordure, ombre et espacements revus.
- **Les cases à cocher sont devenues des interrupteurs.** Sur une page qui en aligne huit,
  un rail qui s'allume se lit d'un coup d'œil, là où un petit carré demandait de chercher
  la coche.
- **Un filet sépare deux réglages consécutifs.** Sans lui, une carte de huit lignes
  formait un pavé uniforme où l'œil ne savait plus quelle explication se rapportait à quel
  interrupteur.
- **Hiérarchie typographique reprise** : titres de page nettement plus grands, valeurs de
  statistiques doublées de taille, légendes en petites capitales.
- **L'onglet actif se voit.** Le repère qui glisse derrière lui est passé du blanc à la
  teinte d'accent, avec sa lueur.
- La barre du bas se détache du contenu qui défile derrière, la bannière d'accueil porte
  une vraie teinte d'accent, et le rappel du serveur principal est présenté en pastille au
  lieu de flotter en texte nu.

### Corrections

- **`-fx-letter-spacing` n'existe pas en JavaFX** et était silencieusement ignoré. Les
  légendes qui en dépendaient sont passées en capitales, ce qui fonctionne réellement.
- Le filet de séparation tire sa couleur du thème : écrit en blanc, il aurait été
  invisible sur le thème clair.

---

## 1.9.0 — 26 août 2026

### Installation des chargeurs de mods

- **MiniCube installe désormais Fabric, Quilt, NeoForge et Forge.** Jusqu'ici il savait
  *reconnaître* une version moddée, mais pas la poser : il fallait aller chercher
  l'installeur officiel soi-même. C'était l'étape où l'on perd les joueurs ; elle
  disparaît.
- La fenêtre d'installation propose un **choix de chargeur**. Le catalogue affiché suit
  ce choix — chaque chargeur ne couvre qu'une partie des versions du jeu, et montrer les
  autres ne mènerait qu'à des échecs. La version du chargeur la plus récente est
  proposée d'office.
- La version vanilla dont dépend le chargeur est **installée automatiquement** si elle
  manque, et la version fraîchement posée est **sélectionnée au retour** : plus qu'à
  cliquer sur Jouer.

### Deux méthodes, selon le chargeur

- **Fabric et Quilt** publient leur descriptif de version en JSON. L'installation se
  réduit à l'écrire : aucun programme n'est téléchargé ni exécuté. Comptez une seconde.
- **Forge et NeoForge** n'offrent rien de tel — leur installeur est un programme qu'il
  faut lancer. MiniCube le télécharge depuis leur dépôt Maven officiel en HTTPS,
  **contrôle son empreinte SHA-1 publiée à côté de lui**, puis l'exécute en mode
  silencieux et l'efface. C'est le modèle de confiance de Maven et Gradle : il protège
  d'un fichier corrompu, non d'un dépôt officiel qui serait lui-même compromis. La
  différence avec une empreinte figée dans le code est réelle, mais aucune autre voie
  n'existe pour ces deux chargeurs.

### Corrections

- **NeoForge : correspondance de versions corrigée.** Minecraft ayant abandonné le `1.`
  initial, NeoForge est passé de trois à quatre nombres. MiniCube lisait encore l'ancienne
  règle et aurait proposé des versions fantômes du type « 1.26.2 ». Les deux conventions
  sont désormais comprises, et le résultat est **recoupé avec le catalogue Mojang** : une
  version que Mojang ne publie pas n'est plus proposée, même si la règle changeait encore.
- **Fabric répondait par une erreur 400** pour une version de jeu qu'il ne connaît pas,
  au lieu d'une liste vide. Le launcher affichait cette erreur brute ; il dit maintenant
  simplement qu'aucune version n'existe.
- **Les listes n'étaient pas habillées par le thème.** Le catalogue de versions s'affichait
  en blanc au milieu d'un dialogue sombre — un défaut présent depuis l'origine, et devenu
  visible avec cette fenêtre.
- **L'onglet Mise à jour affichait « null »** quand le réseau échouait : plusieurs
  exceptions réseau n'ont aucun message. Les pannes courantes sont désormais nommées —
  serveur injoignable, connexion qui n'aboutit pas.

---

## 1.8.0 — 25 août 2026

### Retrait de l'accueil vocal

- **L'accueil vocal est supprimé**, ainsi que la voix neuronale qui l'accompagnait. Le
  launcher ne parle plus au démarrage. Décision assumée : entendre son pseudo prononcé à
  chaque lancement lasse vite, et cela ne valait ni les quatre-vingts mégaoctets de moteur
  neuronal, ni la dépendance à un binaire tiers à distribuer à toute une communauté.
- Sont retirés du code : `VoiceService`, `NeuralVoiceService`, la carte *Voix neuronale*
  et la case *Accueil vocal au démarrage* des paramètres, ainsi que les traductions
  associées.
- **Rien à faire de votre côté.** Les réglages `voiceGreetingEnabled`, `neuralVoiceEnabled`
  et `neuralVoiceId` que contient peut-être votre `config.json` sont simplement ignorés, et
  disparaîtront du fichier au prochain enregistrement. Si vous aviez installé la voix
  neuronale, le dossier `.minicube/voice-neural/` peut être effacé : il ne sert plus.
- Les versions **1.6.0** et **1.7.0** restent étiquetées dans Git : la fonction est
  récupérable telle quelle si l'envie revenait.

---

## 1.7.0 — 25 août 2026

### Voix neuronale

- **L'accueil peut désormais être prononcé par une vraie voix neuronale.** MiniCube
  embarque [Piper](https://github.com/rhasspy/piper), un moteur de synthèse neuronale qui
  tourne entièrement sur votre machine. La différence avec la voix de Windows s'entend
  immédiatement : 22 kHz au lieu de 16, et une intonation qui n'a plus rien de robotique.
- **Trois voix françaises** au choix — Siwis et UPMC (féminines), Tom (masculine) —
  depuis *Paramètres → Voix neuronale*.
- **Rien n'est téléchargé sans votre accord.** La carte annonce le poids exact (82 Mo pour
  Siwis, moteur compris) et attend le bouton. Tant que rien n'est installé, le launcher
  garde la voix de Windows : il n'est jamais muet.
- **Une fois installée, elle est hors ligne pour de bon.** Votre pseudo ne quitte toujours
  pas la machine, et aucun service extérieur n'est appelé — contrairement à ce qu'imposerait
  une API vocale en ligne, qui aurait demandé une clé payante et l'envoi de chaque pseudo
  à un tiers.
- La voix se fait entendre juste après l'installation : cela vaut confirmation, et prépare
  le cache. La toute première synthèse charge le réseau de neurones et prend quelques
  secondes ; les suivantes sont instantanées.
- Le bouton **Écouter** essaie une voix avant de la retenir, et **Désinstaller** rend les
  quatre-vingts mégaoctets.

### Protection

- **Chaque fichier téléchargé est comparé à une empreinte SHA-256 inscrite dans le code.**
  C'est la garantie qui compte, puisqu'il s'agit d'un programme qui sera exécuté : si
  l'hébergeur servait un autre contenu, l'installation échouerait au lieu de le lancer.
  Les adresses sont figées et en HTTPS, jamais lues sur le réseau.
- **La phrase à prononcer passe par l'entrée standard du moteur**, jamais par la ligne de
  commande. Le pseudo n'a donc aucune façon d'être interprété comme autre chose que du
  texte : vérifié avec une phrase hostile, qui a bien été prononcée sans rien déclencher.
- Le téléchargement est vérifié puis déplacé : un fichier incomplet ou refusé est effacé
  et ne peut pas être utilisé.

### Détails

- La voix entre dans l'empreinte du cache : changer de voix produit un nouveau fichier au
  lieu de rejouer l'ancienne.
- Le moteur neuronal n'existe que pour Windows 64 bits ; ailleurs, la carte le dit et la
  voix du système prend le relais.

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
