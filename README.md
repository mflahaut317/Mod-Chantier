# Chantier — suivi de build (NeoForge 26.3)

Affiche en haut à gauche de l'écran la liste des matériaux d'un build, avec ce qu'il y a déjà dans les coffres d'une zone et le pourcentage d'avancement.

## La feuille Google Sheets

| Block Name     | Quantity        | Acquired |
|----------------|-----------------|----------|
| Glass          | 26 stacks + 17  | TRUE     |
| Oak Planks     | 19 stacks + 50  | FALSE    |
| stone_bricks   | 150             |          |

- Les colonnes sont détectées toutes seules : le mod prend la première cellule qui est un bloc et la première qui est une quantité. Les autres colonnes (image, « Acquired »…) sont ignorées.
- Bloc : le **nom anglais du jeu** (`Oak Planks`, `Water Bucket`) ou l'**ID** (`stone_bricks`, `create:andesite_casing`). Pour voir les ID : F3 + H en jeu, puis survoler l'objet.
- Quantité : `150`, `2 stacks`, `26 stacks + 17`, `2st` ou `1 shulker` (= 1728).
- Les lignes d'en-tête sont ignorées. Seul le premier onglet est lu (ou celui du `gid=` dans le lien).
- Partage : « Tous les utilisateurs disposant du lien » en lecture.

## Commandes

| Commande | Effet |
|---|---|
| `/chantier lien <lien>` | Enregistre le lien de la feuille et la lit |
| `/chantier zone <coin1> <coin2>` | Zone des coffres (ex. `/chantier zone ~ ~ ~ ~20 ~5 ~20`) |
| `/chantier actualiser` | Relit la feuille après modification |
| `/chantier titre <texte>` | Nom affiché en haut |
| `/chantier masquer` / `afficher` | Cache ou montre la liste pour soi |
| `/chantier info` | Résumé |
| `/chantier arreter` | Efface le chantier |

## Fonctionnement

- Le serveur recompte toutes les 5 s les coffres, tonneaux, shulkers posées de la zone, y compris le contenu des shulkers rangées dans les coffres.
- Chaque ligne est plafonnée à 100 % : le surplus ne gonfle pas l'avancement.
- Si la zone n'est pas chargée (personne à côté), le dernier comptage reste affiché.
- Zone max : 256 blocs de côté.
- Le mod doit être installé sur le serveur **et** chez les joueurs (packwiz). Un joueur sans le mod peut se connecter, il ne voit juste pas la liste.
- Réglages sauvegardés dans `config/chantier.json` du serveur.

## Compiler

`./gradlew build` (Java 25) → `build/libs/chantier-1.0.0.jar`
