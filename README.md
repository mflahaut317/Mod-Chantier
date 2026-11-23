# Chantier — suivi de build (NeoForge 26.3)

Suivi des matériaux d'un gros build entre potes : la liste vient d'une feuille Google Sheets, le mod compte ce qu'il y a dans les coffres choisis et dans l'inventaire des joueurs connectés, et affiche l'avancement à gauche de l'écran.

## Utilisation (tout se fait dans l'interface)

1. Ouvre l'inventaire (E) et clique sur le bouton **Chantier** à gauche (ou tape `/chantier`).
2. **Lien** : colle le lien de la feuille Google Sheets, puis **Charger**.
3. **Titre** : nom affiché en haut de la liste, puis **OK**.
4. **Choisir les coffres** (facultatif) : l'écran se ferme, fais un clic droit sur chaque coffre à compter (re-clic pour retirer, les coffres doubles comptent d'un coup). Rouvre l'écran et clique sur **Terminer la sélection**. **Vider** remet la sélection à zéro.
5. **Voir la liste complète** : toute la liste (molette pour défiler) et l'onglet **À récolter**, qui convertit ce qui manque en matières premières avec les recettes du jeu.

À 100 %, un message « Chantier terminé » s'affiche pour tout le monde et la liste disparaît, même si on sort ensuite les blocs pour construire. Charger ou relire une liste relance le chantier.

## La feuille Google Sheets

| Block Name     | Quantity        |
|----------------|-----------------|
| Glass          | 26 stacks + 17  |
| Oak Planks     | 19 stacks + 50  |
| stone_bricks   | 150             |

- Les colonnes sont détectées toutes seules (une liste exportée de Litematica marche telle quelle).
- Bloc : nom anglais du jeu (`Oak Planks`) ou ID (`stone_bricks`, `create:andesite_casing`).
- Quantité : `150`, `2 stacks`, `26 stacks + 17`, `2st` ou `1 shulker`.
- Partage : « Tous les utilisateurs disposant du lien » en lecture.

## Commandes (facultatives)

`/chantier` ouvre l'écran. `/chantier lien <lien>`, `/chantier actualiser`, `/chantier titre <texte>`, `/chantier masquer`, `/chantier afficher`, `/chantier info`, `/chantier arreter`.

## Installation et mises à jour

Le mod doit être sur le serveur **et** chez les joueurs (via packwiz). Sur le serveur : `~/maj-chantier.sh` (voir `scripts/maj-chantier.sh`).

Compiler à la main : `./gradlew build` (Java 25) → `build/libs/`.
