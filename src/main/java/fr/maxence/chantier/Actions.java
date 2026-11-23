package fr.maxence.chantier;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Côté serveur : exécute les clics de l'écran Chantier et renvoie les réglages à jour. */
public final class Actions {
    private Actions() {}

    static void recevoir(ActionPayload action, IPayloadContext contexte) {
        if (!(contexte.player() instanceof ServerPlayer joueur)) return;
        MinecraftServer serveur = joueur.level().getServer();
        Projet projet = Projet.get();
        String valeur = action.valeur().trim();

        switch (action.action()) {
            case "config" -> repondre(joueur, null, false);
            case "titre" -> {
                if (valeur.isEmpty()) { repondre(joueur, "Écris un titre.", false); return; }
                projet.definirTitre(valeur);
                projet.diffuser(serveur);
                repondre(joueur, "Titre enregistré.", false);
            }
            case "lien" -> {
                if (valeur.isEmpty()) { repondre(joueur, "Colle le lien de la feuille Google Sheets.", false); return; }
                projet.definirLien(valeur);
                repondre(joueur, "Lecture de la feuille…", false);
                projet.telechargerListe(serveur, message -> repondre(joueur, message, false));
            }
            case "actualiser" -> {
                repondre(joueur, "Lecture de la feuille…", false);
                projet.telechargerListe(serveur, message -> repondre(joueur, message, false));
            }
            case "coin1", "coin2" -> {
                int numero = action.action().equals("coin1") ? 1 : 2;
                String message = projet.definirCoin(numero, joueur.level().dimension(), joueur.blockPosition(), serveur);
                repondre(joueur, message, false);
            }
            case "masquer", "afficher" -> {
                projet.masquer(joueur.getUUID(), action.action().equals("masquer"));
                projet.envoyerA(joueur);
                repondre(joueur, action.action().equals("masquer") ? "Liste masquée." : "Liste affichée.", false);
            }
            case "selection_on" -> {
                projet.basculerSelection(joueur.getUUID(), true);
                joueur.sendSystemMessage(Component.literal(
                        "§eMode sélection : clic droit sur les coffres pour les ajouter ou les retirer. "
                                + "Rouvre l'écran Chantier et clique sur « Terminer » quand c'est fini."));
                repondre(joueur, "Mode sélection actif.", false);
            }
            case "selection_off" -> {
                projet.basculerSelection(joueur.getUUID(), false);
                repondre(joueur, "Sélection terminée : " + projet.nombreCoffres() + " emplacements de coffre.", false);
            }
            case "vider_coffres" -> {
                projet.viderCoffres();
                projet.recompter(serveur);
                projet.diffuser(serveur);
                repondre(joueur, "Sélection vidée.", false);
            }
            case "arreter" -> {
                projet.arreter();
                projet.diffuser(serveur);
                repondre(joueur, "Chantier effacé.", false);
            }
            default -> { }
        }
    }

    /** En mode sélection, un clic droit sur un conteneur l'ajoute ou le retire (sans l'ouvrir). */
    static void onClicDroit(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer joueur)) return;
        if (!Projet.get().enSelection(joueur.getUUID())) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) { event.setCanceled(true); return; }
        if (!(joueur.level() instanceof ServerLevel monde)) return;
        event.setCanceled(true);
        String message = Projet.get().basculerCoffre(monde, event.getPos(), monde.getServer());
        joueur.sendSystemMessage(Component.literal(message));
    }

    /** Envoie les réglages à jour (et un message) au joueur, s'il a le mod. */
    static void repondre(ServerPlayer joueur, String message, boolean ouvrir) {
        if (!joueur.connection.hasChannel(ConfigPayload.TYPE)) return;
        PacketDistributor.sendToPlayer(joueur, Projet.get().config(joueur.getUUID(), message, ouvrir));
    }
}
