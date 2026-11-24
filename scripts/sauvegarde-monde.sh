#!/bin/bash
# Sauvegarde quotidienne du monde de la survie vers le disque dur, en gardant les 7 derniers jours.
# Fonctionne serveur allumé : l'écriture du monde est suspendue le temps de la copie (save-off / save-on).
#
#   ~/sauvegarde-monde.sh            -> sauvegarde maintenant
#   (lancé automatiquement chaque nuit à 4 h par cron)
set -uo pipefail

SERVEUR=~/survie
DESTINATION=/mnt/sauvegardes/minecraft
GARDER_JOURS=7

# Nom du dossier du monde (level-name dans server.properties, « world » par défaut)
MONDE=$(grep -E '^level-name=' "$SERVEUR/server.properties" 2>/dev/null | cut -d= -f2- | tr -d '\r')
MONDE=${MONDE:-world}

if ! mountpoint -q /mnt/sauvegardes; then
    echo "$(date '+%F %T') ERREUR : le disque dur n'est pas monté sur /mnt/sauvegardes, rien n'a été fait."
    exit 1
fi
mkdir -p "$DESTINATION"

console() { screen -S mc -p 0 -X stuff "$1$(printf '\r')" 2>/dev/null; }
EN_MARCHE=0
screen -list | grep -q '\.mc\b' && EN_MARCHE=1

# 1. Le serveur écrit tout sur le disque, puis arrête d'écrire pendant la copie
if [ "$EN_MARCHE" = 1 ]; then
    console "save-off"
    console "save-all flush"
    sleep 15
fi

# 2. Copie compressée du monde + des réglages des mods (config, dont chantier.json)
FICHIER="$DESTINATION/survie-$(date '+%Y-%m-%d_%Hh%M').tar.gz"
tar -czf "$FICHIER" -C "$SERVEUR" "$MONDE" config 2>/dev/null
RESULTAT=$?

# 3. Le serveur reprend l'écriture (même si la copie a échoué)
[ "$EN_MARCHE" = 1 ] && console "save-on"

if [ $RESULTAT -gt 1 ] || [ ! -s "$FICHIER" ]; then
    echo "$(date '+%F %T') ERREUR : la sauvegarde a échoué ($FICHIER)."
    exit 1
fi

# 4. On ne garde que les 7 derniers jours
find "$DESTINATION" -name 'survie-*.tar.gz' -mtime +$GARDER_JOURS -delete

echo "$(date '+%F %T') OK : $FICHIER ($(du -h "$FICHIER" | cut -f1))"
