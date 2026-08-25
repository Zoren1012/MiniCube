# Rapport d'audit et d'amélioration — MiniCube

**Date :** 24 août 2026 · **Version auditée :** 1.0.1 · **Périmètre :** intégralité du code

---

## Résumé

L'audit a porté sur les 77 fichiers source du projet, avec une attention particulière
aux points où une donnée extérieure entre dans le launcher : réseau, fichiers de
configuration, contenus distants.

| Catégorie | Détecté | Corrigé | Vérifié par un test |
|---|---|---|---|
| Failles de sécurité | 5 | 5 | 15 assertions automatisées |
| Problèmes de performance | 4 | 4 | 1 mesure chiffrée |
| Bugs découverts en vérifiant les correctifs | 2 | 2 | double exécution |
| Manques fonctionnels | 4 | 4 | 12 assertions + rendu des 8 onglets |
| Problèmes connus non corrigés | 4 | 0 | documentés ci-dessous |

Chaque correction a été compilée et exécutée. Aucune n'est théorique.

---

## 1. Failles de sécurité

### 1.1 — Le jeton de session fuyait dans le journal · **critique**

**Mécanisme.** `GameLaunchService` journalisait la ligne de commande complète du jeu :

```java
Log.debug("Commande : " + String.join(" ", command));
```

Cette ligne contient `--accessToken <jeton>`. Or les journaux sont précisément ce que
l'on demande à un utilisateur de partager quand il signale un problème. Un jeton publié
sur un forum ou un salon Discord donne l'accès complet au compte Minecraft, y compris
le changement de skin et l'accès aux serveurs, jusqu'à son expiration.

**Correction.** Le masquage est appliqué **au centre**, dans `Log.log()`, et non au
point d'appel :

```java
Entry entry = new Entry(LocalDateTime.now(), level, Safety.redact(message));
```

Ce choix est délibéré : un masquage placé sur chaque appel serait oublié tôt ou tard, et
la sortie du jeu elle-même — qui transite par le même journal — n'aurait pas été
couverte. Le nom de l'option est conservé, seule sa valeur est remplacée :

```
avant : --username Steve --accessToken eyJhbGciOiJIUzI1NiJ9.SECRET.signature --userType msa
après : --username Steve --accessToken [masque] --userType msa
```

Le coût est négligeable : une recherche de sous-chaîne écarte immédiatement les lignes
sans marqueur, ce qui est le cas de la quasi-totalité de la sortie du jeu.

### 1.2 — Exécution de code arbitraire par la mise à jour · **critique**

**Mécanisme.** `UpdateService` téléchargeait un jar et l'exécutait :

```java
new ProcessBuilder(java, "-jar", newJar).inheritIO().start();
```

La vérification d'empreinte était conditionnelle (`if (!info.sha1().isBlank())`) et
aucune contrainte ne pesait sur le protocole. Un descripteur en `http://`, ou servi
depuis un domaine expiré et racheté, faisait exécuter le code de son choix sur toutes
les machines équipées — sans interaction de l'utilisateur au-delà d'un clic sur
« installer ».

**Correction.** Deux exigences, désormais non négociables :

```java
Safety.requireSecureUrl(info.url(), "Mise a jour du launcher");
if (info.sha1().isBlank()) {
    throw new Safety.UnsafeInputException("Mise a jour refusee : ...");
}
```

Un fichier destiné à être exécuté n'est plus accepté sans transport chiffré **et**
empreinte. Les adresses `localhost` restent tolérées en clair, pour permettre de
développer son propre service de distribution sans certificat.

### 1.3 — Prise de contrôle par la sauvegarde cloud · **critique**

**Mécanisme.** `CloudSyncService.pull()` restaurait la configuration reçue du serveur.
`ConfigService.replace()` protégeait le dossier de jeu et les comptes, mais laissait
passer deux champs :

- `javaPath` — le programme exécuté au lancement ;
- `extraJvmArgs` — des arguments JVM, dont `-javaagent:`.

Un service de sauvegarde compromis prenait donc la main sur ce qui s'exécute.

**Correction.** Ces deux champs ne sont jamais restaurés : la valeur locale est
réimposée après désérialisation. La règle générale retenue est simple — **rien de ce qui
détermine un programme à exécuter ne peut venir du réseau.**

### 1.4 — Ouverture de liens sans contrôle de protocole · **moyenne**

**Mécanisme.** L'onglet Accueil ouvrait le lien fourni par une actualité :
`OsUtil.openUrl(item.link())`. Les actualités pouvant provenir d'une URL distante, un
lien `file:///C:/Windows/System32/...` ou un protocole applicatif enregistré
(`ms-msdt:`, à l'origine de la faille Follina) était remis tel quel au système.

**Correction.** `Safety.isWebLink` n'accepte que `http` et `https`. Tout autre protocole
est refusé et journalisé.

### 1.5 — Fichier de comptes lisible par les autres utilisateurs · **moyenne**

**Mécanisme.** `accounts.json` contient les jetons de rafraîchissement. La protection
reposait sur `File.setReadable(false, false)`, qui **ne touche pas les listes de
contrôle d'accès NTFS** et renvoie pourtant un succès : la protection était illusoire.

**Correction.** `Safety.restrictToOwner` applique une ACL réduite au propriétaire sous
Windows (`AclFileAttributeView`), et `rw-------` ailleurs. L'échec est journalisé plutôt
que silencieux — mieux vaut savoir qu'on n'est pas protégé.

### Vérification

Les cinq correctifs sont couverts par `SecurityCheck`, exécuté après chaque compilation :

```
=== MASQUAGE DES JETONS ===      5 assertions
=== LIENS AUTORISES ===          6 assertions
=== TELECHARGEMENTS EXECUTES === 4 assertions
TOUT EST CONFORME (15 verifications)
```

---

## 2. Optimisations

### 2.1 — Vérification d'intégrité : mesurée, pas supposée

**Problème.** Avec l'option « vérifier les fichiers avant de jouer » active — c'est la
valeur par défaut — chaque lancement recalculait l'empreinte SHA-1 de **toutes** les
ressources du jeu. Sur cette machine : 4 590 fichiers, 425 Mo.

**Correction.** `VerificationCache` retient pour chaque fichier sa taille, sa date de
modification et l'empreinte constatée. Tant que taille et date sont inchangées,
l'empreinte est réputée valable et la lecture est évitée.

**Mesure réelle**, sur les ressources effectivement installées :

| Étape | Durée |
|---|---|
| Vérification intégrale (comportement précédent) | **1 367 ms** |
| Premier passage, le cache se remplit | 1 735 ms |
| Lancements suivants, cache chaud | **404 ms** |

Soit environ **une seconde regagnée à chaque lancement**, et davantage sur un disque
mécanique où la lecture domine. Le gain de 3× est mesuré sur un SSD NVMe, cas le plus
défavorable pour ce type d'optimisation.

**Limite assumée et documentée.** Le cache suppose que personne ne remplace un fichier
par un autre de taille identique en restaurant sa date de modification. Le bouton
*Vérifier les fichiers* de l'onglet Paramètres ignore volontairement le cache
(`force = true`) et recalcule tout : le contrôle explicite ne repose sur aucune
hypothèse.

### 2.2 — Journal : le fichier était rouvert à chaque ligne

`writeToFile` faisait un `Files.writeString(..., APPEND)` par ligne, soit une ouverture
et une fermeture de fichier à chaque message. Sans importance pour les messages du
launcher, coûteux pour la sortie du jeu : Minecraft produit plusieurs milliers de lignes
au seul démarrage.

Le flux reste désormais ouvert, avec un tampon vidé toutes les 40 lignes et
**immédiatement** sur un avertissement ou une erreur, pour qu'un plantage ne laisse
jamais le diagnostic dans un tampon perdu.

**Deux défauts sont apparus en vérifiant cette optimisation — et c'est précisément
l'intérêt de la vérifier.**

*Premier défaut, introduit par le tampon.* Une condition de temps déclenchée à
l'écriture ne suffit pas : après les cinq lignes du démarrage, plus rien n'est
journalisé pendant que l'utilisateur regarde l'interface, et ces lignes restaient
invisibles dans le fichier — perdues si le launcher était tué. Un fil démon les libère
désormais au bout de 700 ms, indépendamment de toute écriture.

*Second défaut, latent depuis l'origine.* `init()` appelait `Files.createFile(logFile)`,
qui **lève une exception si le fichier existe déjà**. La rotation ne déplaçait le journal
précédent que s'il était non vide :

```java
if (Files.exists(logFile) && Files.size(logFile) > 0) { ... archive ... }
Files.createFile(logFile);   // echoue si un journal vide subsiste
```

Un `launcher.log` de 0 octet — ce que laisse un plantage, ou une session sans message —
faisait donc échouer l'ouverture et **désactivait silencieusement toute la journalisation
fichier** pour les sessions suivantes. Le bug était antérieur à cette optimisation ; le
tampon l'a simplement rendu atteignable. L'ouverture se fait maintenant en
`CREATE + TRUNCATE_EXISTING`, sans `createFile`.

Vérifié par une double exécution : lancement, arrêt forcé, relancement immédiat — le
journal est complet dans les deux cas.

### 2.3 — Détection Java : jusqu'à 20 processus lancés

`JavaRuntimeService.probe` exécutait `java -version` pour chaque candidat, avec un délai
d'attente de 10 secondes. Sur une machine équipée de plusieurs JDK, l'onglet Paramètres
se figeait le temps de la détection.

Chaque distribution Java dépose un fichier `release` contenant
`JAVA_VERSION="21.0.4"`. Il est désormais lu en premier ; l'exécutable n'est lancé que
s'il est absent, ce qui n'arrive que sur des installations anciennes ou incomplètes. Une
ouverture de fichier remplace la création d'un processus.

### 2.4 — Texture du skin : 262 144 allocations par changement

`SkinViewer3D.upscale` agrandissait la texture pixel par pixel, en créant un objet
`Color` par pixel source et en appelant `setColor` pour chacun des pixels agrandis.

La version actuelle lit et écrit par tableaux d'entiers (`PixelFormat.getIntArgbInstance`),
et duplique les lignes par `System.arraycopy`. Aucune allocation par pixel.

---

## 3. Fonctionnalités ajoutées

### 3.1 — Installation d'une version depuis le launcher

`GameFileService` savait déjà télécharger une version officielle
(`fetchAvailableVersions`, `installVersion`) mais **aucune interface n'y donnait accès** :
sur une machine neuve, le launcher n'avait rien à lancer.

Un bouton `+` dans la barre de lancement ouvre le catalogue Mojang, avec recherche,
filtre versions stables / de développement, et progression détaillée pendant
l'installation.

### 3.2 — Gestion de plusieurs comptes

`AccountService` gérait déjà une liste de comptes, mais la fenêtre de connexion ne
savait qu'en ajouter : avec deux comptes enregistrés, il était impossible de basculer de
l'un à l'autre. La fenêtre liste désormais les comptes enregistrés, avec un bouton pour
activer et un pour oublier.

### 3.3 — Confirmation avant les actions destructrices

Supprimer un mod ou un pack de shaders effaçait un fichier du disque sur un simple clic
d'icône. Une confirmation est désormais demandée, avec le bouton neutre présélectionné :
une validation au clavier ne peut plus supprimer par inadvertance.


### 3.4 — Le skin importé ne pouvait pas être appliqué · **signalé par l'utilisateur**

**Symptôme.** Sur un compte hors-ligne, importer un PNG mettait bien à jour l'aperçu 3D,
mais le bouton *Appliquer* restait grisé. Aucun clic ne produisait quoi que ce soit, et
au redémarrage suivant l'aperçu revenait au personnage par défaut : le skin importé était
perdu.

**Analyse.** Le comportement était « intentionnel » — on ne peut pas envoyer un skin aux
serveurs de Mojang sans compte authentifié — mais c'était une impasse. Un bouton désactivé
ne répond à aucun clic et n'explique rien ; la seule indication était une ligne grise en
bas de carte, facile à manquer.

Le vrai défaut était ailleurs : **rien ne retenait la texture importée.** `importSkin`
la copiait dans `~/.minicube/skins/`, puis plus personne ne s'en souvenait.

**Correction.** « Appliquer » a désormais un sens dans les deux cas :

| Compte | Effet |
|---|---|
| Microsoft | Envoi à Mojang **et** mémorisation locale, pour que l'aperçu reste juste pendant la propagation côté serveur |
| Hors-ligne | Mémorisation locale : aperçu 3D et vignette de la barre latérale, conservés d'une session à l'autre |

Le champ `localSkinPath` a été ajouté au compte et survit à l'enregistrement. Le bouton
reste actif, avec un message qui dit exactement ce qui s'est passé — y compris la limite :
les autres joueurs ne verront pas ce skin, puisqu'en multijoueur le serveur demande la
texture à Mojang à partir de l'UUID, et qu'un compte hors-ligne n'y possède aucun profil.

Si le fichier est déplacé ou supprimé, la référence est abandonnée proprement plutôt que
de provoquer une erreur à chaque affichage.

Deux défauts d'ergonomie ont été corrigés au passage : la carte d'aperçu se laissait
comprimer jusqu'à tronquer ses libellés (« R... » au lieu de « Recentrer »), et les
contrôles de capes restaient à juste titre désactivés hors-ligne — mais sans que ce soit
distingué du bouton *Appliquer*, qui lui pouvait fonctionner.

**Vérifié par 12 assertions** (`SkinCheck`) : validation des formats 64×64 et 64×32, refus
d'une image mal dimensionnée, application, persistance par sérialisation, et abandon de la
référence quand le fichier disparaît.
---

## 4. Problèmes détectés et **non** corrigés

Ces points sont réels et identifiés. Ils n'ont pas été traités faute de temps, ou parce
que la correction demande un arbitrage qui ne m'appartient pas.

### 4.1 — Le jeton transite par la ligne de commande du jeu · non corrigeable

Minecraft reçoit son jeton de session en argument (`--accessToken`). Sous Linux et
macOS, la ligne de commande d'un processus est lisible par tout utilisateur via `/proc`.
Le launcher officiel de Mojang procède de la même façon : il n'existe pas d'alternative
tant que le jeu n'accepte pas le jeton par un autre canal. Le masquage du §1.1 protège
les journaux, pas la table des processus.

### 4.2 — Onglet Serveurs : redessin complet à chaque réponse · moyen

`ServersController.redraw()` reconstruit toutes les cartes à chaque latence reçue, soit
n redessins pour n serveurs. Avec trois serveurs c'est invisible ; avec trente, le
scintillement serait net. **Correction recommandée :** conserver une référence par carte
et ne mettre à jour que les deux libellés concernés.

### 4.3 — Les jetons sont stockés en clair · moyen

L'ACL du §1.5 empêche un **autre utilisateur** de la machine de lire `accounts.json`,
mais pas un programme s'exécutant sous votre propre compte. Un chiffrement réel
supposerait DPAPI sous Windows, donc du code natif ou une dépendance JNA. C'est
l'approche qu'emploient les navigateurs ; elle mérite d'être envisagée si le launcher
est distribué largement.

### 4.4 — Aucune limite de taille sur les téléchargements · faible

`Http.download` écrit ce qu'il reçoit sans plafond. Un serveur malveillant pourrait
saturer le disque. Le risque est limité — les adresses sont configurées par
l'utilisateur — mais un plafond issu de l'en-tête `Content-Length` serait peu coûteux.

---

## 5. Recommandations pour les prochaines versions

Par ordre de valeur décroissante :

1. **Sauvegarde et restauration de la configuration.** Un fichier d'export
   (`config.json`, serveurs personnels, réglages graphiques) permettrait de retrouver
   son environnement après une réinstallation. Exclure `accounts.json` de l'export, ou
   avertir explicitement de ce qu'il contient.

2. **Onglet Diagnostic.** Rassembler ce que l'on demande systématiquement lors d'un
   signalement : version du launcher, runtime Java retenu, mémoire allouée, espace
   disque, résultat de la dernière vérification d'intégrité, et un bouton pour copier
   l'ensemble — journal masqué compris.

3. **Installation de Forge et Fabric.** Le launcher détecte et lance ces versions mais
   ne les installe pas. Fabric expose un service d'installation documenté et simple ;
   Forge demande davantage de travail.

4. **Corriger le redessin de l'onglet Serveurs** (§4.2).

5. **Chiffrement des jetons au repos** (§4.3), si la distribution dépasse un cercle
   restreint.

6. **Tests automatisés durables.** `SecurityCheck` et `PerfCheck` vivent aujourd'hui
   hors du projet. Les intégrer sous `src/test/java` avec JUnit les rendrait exécutables
   par n'importe qui, et éviterait qu'une régression sur le masquage des jetons passe
   inaperçue.

---

## 6. Décisions techniques notables

| Décision | Raison |
|---|---|
| Masquage des secrets au centre du journal, pas au point d'appel | Un masquage réparti finit toujours par être oublié quelque part |
| Refus d'une mise à jour sans SHA-1, même en https | Le chiffrement du transport ne dit rien de l'intégrité du serveur d'origine |
| `javaPath` et `extraJvmArgs` jamais restaurés depuis le cloud | Rien de ce qui détermine un programme à exécuter ne doit venir du réseau |
| Cache d'intégrité par taille + date, pas par empreinte seule | Un `stat` remplace la lecture complète du fichier |
| Le bouton *Vérifier les fichiers* ignore le cache | Un contrôle explicite ne doit reposer sur aucune hypothèse |
| Interpolateur à ressort écrit à la main | `Interpolator.SPLINE` refuse les points de contrôle hors [0,1] |
| La couche de notifications n'a aucun fond | Une région avec fond, même transparent, capte la souris sur toute sa surface |

---

*Rapport établi le 24 août 2026. Toutes les corrections décrites sont compilées et
exécutées ; les mesures proviennent de cette machine.*
