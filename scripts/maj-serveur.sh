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
MODS=("chantier:Chantier" "souvenirs:Souvenirs" "tableau:Tableau" "durabilite:Durabilite" "visage:Visage" "pixelpotes:PixelPotes" "evenements:Evenements")

# -1. Le script se met à jour tout seul (nouveaux mods ajoutés à la liste, corrections…)
SCRIPT_URL="https://raw.githubusercontent.com/mflahaut317/Mod-Chantier/main/scripts/maj-serveur.sh"
if [ -z "${MAJ_SCRIPT_A_JOUR:-}" ]; then
    NOUVEAU=$(mktemp)
    if curl -fsSL "$SCRIPT_URL" -o "$NOUVEAU" && [ -s "$NOUVEAU" ] && ! cmp -s "$NOUVEAU" "$0"; then
        cp "$NOUVEAU" "$0" && chmod +x "$0"
        rm -f "$NOUVEAU"
        echo "Script mis à jour, je le relance…"
        MAJ_SCRIPT_A_JOUR=1 exec "$0" "$@"
    fi
    rm -f "$NOUVEAU"
fi

# 0. Mot de passe sudo demandé tout de suite (sinon le serveur pourrait rester arrêté si sudo expire)
sudo -v || { echo "Mot de passe sudo refusé : rien n'a été fait."; exit 1; }

# 1. Dernières versions publiées
echo "Recherche des dernières versions…"
RELEASE=$(curl -fsSL "https://api.github.com/repos/$DEPOT/releases/tags/dernier")

# 2. Pack des joueurs (packwiz + GitHub) : seuls les mods qui ont une nouvelle version sont touchés
cd "$PACK"
RESUME=""
CHANGES=0
for MOD in "${MODS[@]}"; do
    ID="${MOD%%:*}"; NOM="${MOD##*:}"
    VERSION=$(echo "$RELEASE" | grep -oE "\"$ID-[0-9]+\.[0-9]+\.[0-9]+\.jar\"" | tr -d '"' \
        | sed -E "s/$ID-(.*)\.jar/\1/" | sort -uV | tail -1)
    if [ -z "$VERSION" ]; then
        echo "$NOM : pas encore publié, ignoré."
        continue
    fi
    ACTUELLE=""
    [ -f "mods/$ID.pw.toml" ] && ACTUELLE=$(grep -oE "$ID-[0-9.]+[0-9]\.jar" "mods/$ID.pw.toml" | head -1 | sed -E "s/$ID-(.*)\.jar/\1/")
    if [ "$ACTUELLE" = "$VERSION" ]; then
        echo "$NOM : $VERSION (déjà à jour)"
        continue
    fi
    if [ -z "$ACTUELLE" ]; then
        echo "$NOM : nouveau mod, $VERSION"
        RESUME="$RESUME $NOM $VERSION (nouveau)"
    else
        echo "$NOM : $ACTUELLE -> $VERSION"
        RESUME="$RESUME $NOM $ACTUELLE -> $VERSION"
    fi
    CHANGES=$((CHANGES + 1))
    packwiz remove "$ID" >/dev/null 2>&1 || true
    packwiz url add "$NOM" "$BASE/$ID-$VERSION.jar"
    # Tableau : côté serveur seulement (les joueurs ne le téléchargent pas)
    if [ "$ID" = "tableau" ] && [ -f "mods/$ID.pw.toml" ]; then sed -i 's/^side = .*/side = "server"/' "mods/$ID.pw.toml"; fi
    # Durabilité et Pixel Potes : côté joueurs seulement
    case "$ID" in durabilite|pixelpotes)
        [ -f "mods/$ID.pw.toml" ] && sed -i 's/^side = .*/side = "client"/' "mods/$ID.pw.toml";;
    esac
done

# Rien de neuf : on ne touche à rien (sauf demande explicite : ~/maj-serveur.sh --forcer)
if [ "$CHANGES" = 0 ] && [ "${1:-}" != "--forcer" ]; then
    echo "Tout est déjà à jour : le serveur n'est pas redémarré."
    exit 0
fi

packwiz refresh
git add -A
if git diff --cached --quiet; then
    echo "Le pack était déjà à jour."
else
    git commit -m "Mise à jour :$RESUME"
    git push
fi

# 2b. Le serveur lit le pack à l'adresse de ce commit précis : disponible tout de suite sur GitHub
#     (l'adresse « main » peut garder l'ancienne version en cache jusqu'à 5 minutes)
SHA=$(git -C "$PACK" rev-parse HEAD)
sed -i -E "s#raw\.githubusercontent\.com/mflahaut317/survie-pack/[^/]+/pack\.toml#raw.githubusercontent.com/mflahaut317/survie-pack/$SHA/pack.toml#" "$SERVEUR/start.sh"
grep -q "$SHA" "$SERVEUR/start.sh" || echo "Attention : adresse du pack introuvable dans start.sh (le serveur lira la version « main »)."

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
echo "C'est fait :${RESUME:- rien de neuf (redémarrage forcé)}. Le serveur redémarre (environ 1 minute)."
echo "Côté joueurs : fermer Minecraft et relancer l'instance depuis Prism."
