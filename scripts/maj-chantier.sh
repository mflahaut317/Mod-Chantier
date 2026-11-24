#!/bin/bash
# Met à jour le mod Chantier sur le serveur et dans le pack des joueurs, en une commande.
#
#   ~/maj-chantier.sh          -> prend la dernière version publiée sur GitHub
#   ~/maj-chantier.sh 1.3.0    -> prend cette version précise
#
# Étapes : pack packwiz (git push) -> arrêt propre du serveur -> nettoyage -> redémarrage.
set -euo pipefail

PACK=~/survie-pack
SERVEUR=~/survie
DEPOT=mflahaut317/Mod-Chantier
BASE="https://github.com/$DEPOT/releases/download/dernier"

# 0. Mot de passe sudo demandé tout de suite (sinon le serveur pourrait rester arrêté si sudo expire)
sudo -v || { echo "Mot de passe sudo refusé : rien n'a été fait."; exit 1; }

# 1. Version à installer
if [ $# -ge 1 ]; then
    VERSION="$1"
else
    echo "Recherche de la dernière version…"
    VERSION=$(curl -fsSL "https://api.github.com/repos/$DEPOT/releases/tags/dernier" \
        | grep -oE 'chantier-[0-9]+\.[0-9]+\.[0-9]+\.jar' | sed -E 's/chantier-(.*)\.jar/\1/' \
        | sort -uV | tail -1)
fi
if [ -z "${VERSION:-}" ]; then
    echo "Impossible de trouver la version. Lance : ~/maj-chantier.sh 1.3.0"
    exit 1
fi
JAR="chantier-$VERSION.jar"
echo "Version : $VERSION"

# 2. Pack des joueurs (packwiz + GitHub)
cd "$PACK"
packwiz remove chantier >/dev/null 2>&1 || true
packwiz url add "Chantier" "$BASE/$JAR"
packwiz refresh
git add -A
if git diff --cached --quiet; then
    echo "Le pack était déjà à jour."
else
    git commit -m "Chantier $VERSION"
    git push
fi

# 3. Arrêt propre du serveur (sauvegarde du monde)
if screen -list | grep -q '\.mc\b'; then
    echo "Arrêt du serveur (sauvegarde du monde)…"
    screen -S mc -p 0 -X stuff "stop$(printf '\r')"
    for i in $(seq 1 90); do
        pgrep -f 'neoforge.*nogui' >/dev/null || break
        sleep 1
    done
    if pgrep -f 'neoforge.*nogui' >/dev/null; then
        echo "Le serveur ne s'est pas arrêté après 90 s. Rien n'a été redémarré : vérifie avec screen -r mc"
        exit 1
    fi
fi

# 4. Nettoyage : le serveur retéléchargera la bonne version via packwiz au démarrage
rm -f "$SERVEUR"/mods/chantier-*.jar

# 5. Redémarrage
sudo systemctl daemon-reload
sudo systemctl restart minecraft
echo "C'est fait : Chantier $VERSION. Le serveur redémarre (environ 1 minute)."
echo "Côté joueurs : fermer Minecraft et relancer l'instance depuis Prism."
