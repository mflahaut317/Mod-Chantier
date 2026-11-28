package fr.maxence.evenements;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Cache-cache : un chercheur (aveuglé et immobile pendant que les autres se cachent),
 * chaque caché touché rejoint les chercheurs. Les cachés gagnent s'il en reste à la fin du temps.
 */
final class CacheCache {
    private CacheCache() {}

    private enum Phase { AUCUNE, CACHE, CHERCHE }

    private static final String BARRE = "evenements:jeu";
    private static final String EQUIPE = "cachecache";

    private static Phase phase = Phase.AUCUNE;
    private static final Set<UUID> CHERCHEURS = new LinkedHashSet<>();
    private static final Set<UUID> CACHES = new LinkedHashSet<>();
    private static int ticksRestants;
    private static int dureeCherche;
    private static boolean brillent;

    static boolean enCours() {
        return phase != Phase.AUCUNE;
    }

    /** Lance une partie avec tous les joueurs présents dans le monde d'événement. Renvoie un message d'erreur, ou null. */
    static String lancer(MinecraftServer serveur, String nomChercheur, int secondesCache, int minutes) {
        if (enCours()) return "Une partie est déjà en cours (/cachecache stop pour l'arrêter).";
        List<ServerPlayer> joueurs = Etat.participants(serveur);
        if (joueurs.size() < 2) return "Il faut au moins 2 joueurs dans le monde d'événement.";
        ServerPlayer chercheur = null;
        if (nomChercheur != null) {
            for (ServerPlayer j : joueurs) if (j.getScoreboardName().equalsIgnoreCase(nomChercheur)) chercheur = j;
            if (chercheur == null) return nomChercheur + " n'est pas dans le monde d'événement.";
        } else {
            chercheur = joueurs.get(serveur.overworld().getRandom().nextInt(joueurs.size()));
        }
        CHERCHEURS.clear();
        CACHES.clear();
        CHERCHEURS.add(chercheur.getUUID());
        for (ServerPlayer j : joueurs) if (j != chercheur) CACHES.add(j.getUUID());

        dureeCherche = minutes * 60 * 20;
        ticksRestants = secondesCache * 20;
        brillent = false;
        phase = Phase.CACHE;

        // Tout le monde au point de départ, pseudos cachés (sinon on les voit à travers les murs)
        Outils.commande(serveur, "team remove " + EQUIPE);
        Outils.commande(serveur, "team add " + EQUIPE);
        Outils.commande(serveur, "team modify " + EQUIPE + " nametagVisibility never");
        Outils.commande(serveur, "team modify " + EQUIPE + " collisionRule never");
        for (ServerPlayer j : joueurs) {
            Evenements.versApparition(serveur, j);
            Outils.commande(serveur, "team join " + EQUIPE + " " + j.getScoreboardName());
            Outils.commande(serveur, "effect clear " + j.getScoreboardName() + " minecraft:glowing");
        }
        String nom = chercheur.getScoreboardName();
        Outils.commande(serveur, "effect give " + nom + " minecraft:blindness " + (secondesCache + 2) + " 0 true");
        Outils.commande(serveur, "effect give " + nom + " minecraft:slowness " + secondesCache + " 255 true");

        Outils.commande(serveur, "bossbar remove " + BARRE);
        Outils.commande(serveur, "bossbar add " + BARRE + " " + Outils.texte("Cache-cache"));
        Outils.commande(serveur, "bossbar set " + BARRE + " players @a[tag=" + Evenements.TAG + "]");
        Outils.commande(serveur, "bossbar set " + BARRE + " color yellow");
        Outils.commande(serveur, "bossbar set " + BARRE + " max " + Math.max(1, ticksRestants / 20));

        for (ServerPlayer j : joueurs) {
            if (j == chercheur) Outils.titre(j, "§cTu cherches !", "§7Compte jusqu'à " + secondesCache + "…");
            else Outils.titre(j, "§aCache-toi !", "§7" + nom + " arrive dans " + secondesCache + " s");
            Outils.son(serveur, j, "minecraft:block.note_block.pling");
        }
        Outils.message(joueurs, "§6§l[Cache-cache] §r§e" + nom + " §7cherche. §7" + CACHES.size()
                + " cachés, " + minutes + " min de recherche. Un coup de poing sur un caché = trouvé !");
        return null;
    }

    static void tick(MinecraftServer serveur) {
        if (!enCours()) return;
        ticksRestants--;
        if (ticksRestants % 20 == 0) majBarre(serveur);
        if (ticksRestants > 0) return;
        if (phase == Phase.CACHE) {
            phase = Phase.CHERCHE;
            ticksRestants = dureeCherche;
            Outils.commande(serveur, "bossbar set " + BARRE + " max " + Math.max(1, dureeCherche / 20));
            Outils.commande(serveur, "bossbar set " + BARRE + " color red");
            List<ServerPlayer> joueurs = Etat.participants(serveur);
            Outils.titre(joueurs, "§cC'est parti !", "§7Les chercheurs sont lâchés");
            for (ServerPlayer j : joueurs) Outils.son(serveur, j, "minecraft:entity.ender_dragon.growl");
        } else {
            terminer(serveur, false);
        }
    }

    private static void majBarre(MinecraftServer serveur) {
        int s = Math.max(0, ticksRestants / 20);
        String temps = String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
        String titre = phase == Phase.CACHE
                ? "Cachez-vous ! " + temps
                : "" + noms(serveur, CHERCHEURS) + "  •  Cachés restants : " + CACHES.size() + "  •  " + temps;
        Outils.commande(serveur, "bossbar set " + BARRE + " name " + Outils.texte(titre));
        Outils.commande(serveur, "bossbar set " + BARRE + " value " + s);
        Outils.commande(serveur, "bossbar set " + BARRE + " players @a[tag=" + Evenements.TAG + "]");
        // Dernière minute : les cachés brillent pour relancer la fin de partie
        if (phase == Phase.CHERCHE && !brillent && s <= 60 && dureeCherche > 60 * 20) {
            brillent = true;
            for (UUID id : CACHES) {
                ServerPlayer j = serveur.getPlayerList().getPlayer(id);
                if (j != null) Outils.commande(serveur, "effect give " + j.getScoreboardName() + " minecraft:glowing 70 0 true");
            }
            List<ServerPlayer> joueurs = Etat.participants(serveur);
            Outils.titre(joueurs, "§eDernière minute !", "§7Les cachés brillent maintenant");
        }
    }

    private static String noms(MinecraftServer serveur, Set<UUID> ids) {
        List<String> noms = new ArrayList<>();
        for (UUID id : ids) {
            ServerPlayer j = serveur.getPlayerList().getPlayer(id);
            if (j != null) noms.add(j.getScoreboardName());
        }
        return String.join(", ", noms);
    }

    /** Un joueur en frappe un autre. Renvoie vrai si c'était un coup de cache-cache (le coup est alors annulé). */
    static boolean touche(MinecraftServer serveur, ServerPlayer attaquant, ServerPlayer cible) {
        if (phase != Phase.CHERCHE) return enCours();
        if (!CHERCHEURS.contains(attaquant.getUUID()) || !CACHES.remove(cible.getUUID())) return true;
        CHERCHEURS.add(cible.getUUID());
        Outils.commande(serveur, "effect clear " + cible.getScoreboardName() + " minecraft:glowing");
        List<ServerPlayer> joueurs = Etat.participants(serveur);
        Outils.message(joueurs, "§6§l[Cache-cache] §r§e" + cible.getScoreboardName() + " §7a été trouvé par §e"
                + attaquant.getScoreboardName() + " §7! (" + CACHES.size() + " restant" + (CACHES.size() > 1 ? "s" : "") + ")");
        Outils.titre(cible, "§cTrouvé !", "§7Tu cherches avec eux maintenant");
        for (ServerPlayer j : joueurs) Outils.son(serveur, j, "minecraft:entity.experience_orb.pickup");
        majBarre(serveur);
        if (CACHES.isEmpty()) terminer(serveur, true);
        return true;
    }

    /** Un joueur part (déconnexion ou /event quitter) : on le retire de la partie. */
    static void retirer(MinecraftServer serveur, ServerPlayer joueur) {
        if (!enCours()) return;
        CACHES.remove(joueur.getUUID());
        CHERCHEURS.remove(joueur.getUUID());
        Outils.commande(serveur, "team leave " + joueur.getScoreboardName());
        Outils.commande(serveur, "bossbar set " + BARRE + " players @a[tag=" + Evenements.TAG + "]");
        if (CHERCHEURS.isEmpty() || CACHES.isEmpty()) terminer(serveur, CACHES.isEmpty());
    }

    static void terminer(MinecraftServer serveur, boolean chercheursGagnent) {
        if (!enCours()) return;
        List<ServerPlayer> joueurs = Etat.participants(serveur);
        if (chercheursGagnent) {
            Outils.titre(joueurs, "§cLes chercheurs gagnent !", "§7Tout le monde a été trouvé");
            Outils.message(joueurs, "§6§l[Cache-cache] §r§7Fin de partie : §ctous trouvés §7!");
        } else {
            String gagnants = noms(serveur, CACHES);
            Outils.titre(joueurs, "§aLes cachés gagnent !", "§7" + gagnants);
            Outils.message(joueurs, "§6§l[Cache-cache] §r§7Fin de partie : §a" + gagnants + " §7n'ont jamais été trouvés !");
        }
        for (ServerPlayer j : joueurs) {
            Outils.son(serveur, j, "minecraft:ui.toast.challenge_complete");
            Outils.commande(serveur, "effect clear " + j.getScoreboardName() + " minecraft:glowing");
            Outils.commande(serveur, "effect clear " + j.getScoreboardName() + " minecraft:blindness");
            Outils.commande(serveur, "effect clear " + j.getScoreboardName() + " minecraft:slowness");
        }
        arreter(serveur);
    }

    /** Arrête net (sans annonce de gagnant). */
    static void arreter(MinecraftServer serveur) {
        phase = Phase.AUCUNE;
        CHERCHEURS.clear();
        CACHES.clear();
        Outils.commande(serveur, "bossbar remove " + BARRE);
        Outils.commande(serveur, "team remove " + EQUIPE);
    }
}
