# MiniCube HUD

Un panneau d'informations discret en jeu, aux couleurs de MiniCube : coordonnées,
direction, images par seconde, latence, heure du jeu et nom du serveur.

**Mod client uniquement.** Il ne touche ni au monde, ni aux entités, ni au réseau : il
lit ce que votre jeu sait déjà et le dessine. Il n'apporte aucun avantage en partie et
peut être utilisé sur un serveur sans rien demander à personne.

---

## Installation

| | |
|---|---|
| Minecraft | 26.2 |
| Chargeur | Fabric (loader 0.19.3 ou plus récent) |
| Requis | [Fabric API](https://modrinth.com/mod/fabric-api) 0.158.0+26.2 |
| Java | 25 ou plus |

1. Installez **Fabric pour 26.2** — depuis MiniCube, bouton **+** de la barre du bas,
   chargeur *Fabric*, version *26.2*.
2. Déposez `minicube-hud-1.0.0.jar` **et** le jar de Fabric API dans le dossier `mods`
   de votre profil.
3. Lancez le jeu.

---

## Utilisation

| Touche | Effet |
|---|---|
| **F6** | Affiche ou masque le panneau |
| **F7** | Déplace le panneau au coin suivant |

Les deux raccourcis sont modifiables dans *Options → Commandes → Divers*.

Le panneau se masque avec le reste de l'interface quand vous appuyez sur **F1**.

---

## Réglages

Tout se règle dans `config/minicube-hud.json`, créé au premier lancement. Le fichier est
écrit indenté, pour être modifié à la main sans lancer le jeu :

```json
{
  "enabled": true,
  "corner": "TOP_LEFT",
  "showCoordinates": true,
  "showDirection": true,
  "showFps": true,
  "showPing": true,
  "showTime": true,
  "showServer": true,
  "margin": 6,
  "textColor": -1512235,
  "accentColor": -6519482,
  "backgroundColor": -1608515560
}
```

- `corner` — `TOP_LEFT`, `TOP_RIGHT`, `BOTTOM_LEFT` ou `BOTTOM_RIGHT`.
- Les couleurs sont au format **ARGB** en décimal signé. Le premier octet est
  l'opacité : c'est lui qui rend le fond translucide.
- Un champ absent reprend sa valeur par défaut : ajouter un réglage dans une version
  ultérieure n'invalidera pas votre fichier.

Un fichier illisible n'empêche jamais de jouer — les valeurs par défaut reprennent la
main et l'incident est noté dans le journal.

---

## Compiler depuis les sources

```bash
cd mod
gradlew build
```

Le jar apparaît dans `build/libs/`. La première compilation télécharge Gradle, Minecraft
et ses bibliothèques : comptez quelques minutes et plusieurs centaines de mégaoctets.
Les suivantes prennent quelques secondes.

`gradlew runClient` lance un Minecraft de développement avec le mod déjà chargé, sans
toucher à votre installation habituelle.

---

## Sous le capot

Minecraft sépare désormais la **collecte** de ce qu'il faut dessiner et le dessin
lui-même. `MiniCubeHudElement` soumet donc des rectangles et des textes à un extracteur,
et le jeu les rend ensuite. C'est pourquoi cette classe ne fait rien de coûteux : elle
est appelée à chaque image.

Le mod s'enregistre avec `HudElementRegistry.addLast(...)`, ce qui le place au-dessus des
éléments du jeu mais sous les écrans, qui ne doivent jamais être recouverts.

---

## Licence

MIT.
