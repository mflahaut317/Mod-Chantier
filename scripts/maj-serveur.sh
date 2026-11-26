#!/bin/bash
# Met à jour les mods (Chantier, Souvenirs, Tableau, Durabilité, Visage) sur le serveur et dans le pack des joueurs, en une commande :
#
#   ~/maj-serveur.sh
#
# Étapes : pack packwiz (git push) -> arrêt propre du serveur -> nettoyage -> redémarrage.
set -euo pipefail

PACK=~/survie-pack
SERVEUR=~/survie
DEPOT=mflahaut317/Mod-Chantier
BASE="https://github.com/$DEPOT/releases/download/dernier"
MODS=("chantier:Chantier" "souvenirs:Souvenirs" "tableau:Tableau" "durabilite:Durabilite" "visage:Visage")

# 0. Mot de passe sudo demandé tout de suite (sinon le serveur pourrait rester arrêté si sudo expire)
sudo -v || { echo "Mot de passe sudo refusé : rien n'a été fait."; exit 1; }

# 1. Dernières versions publiées
echo "Recherche des dernières versions…"
RELEASE=$(curl -fsSL "https://api.github.com/repos/$DEPOT/releases/tags/dernier")

# 2. Pack des joueurs (packwiz + GitHub)
cd "$PACK"
RESUME=""
for MOD in "${MODS[@]}"; do
    ID="${MOD%%:*}"; NOM="${MOD##*:}"
    VERSION=$(echo "$RELEASE" | grep -oE "\"$ID-[0-9]+\.[0-9]+\.[0-9]+\.jar\"" | tr -d '"' \
        | sed -E "s/$ID-(.*)\.jar/\1/" | sort -uV | tail -1)
    if [ -z "$VERSION" ]; then
        echo "$NOM : pas encore publié, ignoré."
        continue
    fi
    echo "$NOM : $VERSION"
    packwiz remove "$ID" >/dev/null 2>&1 || true
    packwiz url add "$NOM" "$BASE/$ID-$VERSION.jar"
    # Tableau : côté serveur seulement (les joueurs ne le téléchargent pas)
    if [ "$ID" = "tableau" ] && [ -f mods/tableau.pw.toml ]; then sed -i 's/^side = .*/side = "server"/' mods/tableau.pw.toml; fi
    if [ "$ID" = "durabilite" ] && [ -f mods/durabilite.pw.toml ]; then sed -i 's/^side = .*/side = "client"/' mods/durabilite.pw.toml; fi
    RESUME="$RESUME $NOM $VERSION"
done
packwiz refresh
git add -A
if git diff --cached --quiet; then
    echo "Le pack était déjà à jour."
else
    git commit -m "Mise à jour :$RESUME"
    git push
fi

# 2b. Attendre que GitHub serve le nouveau pack (la copie « raw » peut garder l'ancienne version jusqu'à 5 min)
LOCAL=$(sha256sum "$PACK/pack.toml" | cut -d' ' -f1)
echo "Attente de la publication du pack sur GitHub…"
for i in $(seq 1 40); do
    DISTANT=$(curl -fsSL "https://raw.githubusercontent.com/mflahaut317/survie-pack/main/pack.toml" | sha256sum | cut -d' ' -f1)
    [ "$DISTANT" = "$LOCAL" ] && break
    sleep 10
done
[ "$DISTANT" = "$LOCAL" ] || echo "Attention : GitHub sert peut-être encore l'ancien pack. Si un mod manque, relance ce script dans 5 minutes."

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

# 4. Nettoyage : le serveur retéléchargera les bonnes versions via packwiz au démarrage
for MOD in "${MODS[@]}"; do rm -f "$SERVEUR"/mods/"${MOD%%:*}"-*.jar; done
# packwiz.json = mémoire de l'installateur. Sans elle, il revérifie tous les fichiers et remet ceux qui manquent.
rm -f "$SERVEUR/packwiz.json"

# 5. Redémarrage
sudo systemctl daemon-reload
sudo systemctl restart minecraft
echo "C'est fait :$RESUME. Le serveur redémarre (environ 1 minute)."
echo "Côté joueurs : fermer Minecraft et relancer l'instance depuis Prism."
