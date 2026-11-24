package fr.maxence.chantier;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Côté serveur : exécute les clics de l'écran Chantier et renvoie les réglages à jour. */
public final class Actions {
    private Actions() {}

    static void recevoir(ActionPayload action, IPayloadContext contexte) {
        if (!(contexte.player() instanceof ServerPlayer joueur)) return;
        MinecraftServer serveur = joueur.level().getServer();
        String valeur = action.valeur().trim();
        Projet projet = Chantiers.de(joueur.getUUID());

        // Actions possibles sans chantier
        switch (action.action()) {
            case "config" -> { repondre(joueur, null, false); return; }
            case "galerie" -> { GalerieCommune.envoyerA(joueur); return; }
            case "galerie_supprimer" -> {
                GalerieCommune.supprimer(valeur);
                GalerieCommune.envoyerA(joueur);
                return;
            }
            case "creer" -> {
                if (projet != null) { repondre(joueur, "Tu es déjà dans un chantier.", false); return; }
                Projet nouveau = Chantiers.creer(joueur, valeur);
                nouveau.envoyerA(joueur);
                repondre(joueur, "Chantier créé. Colle le lien de ta feuille et clique sur « Charger ».", false);
                return;
            }
            default -> { }
        }
        if (projet == null) { repondre(joueur, "Crée d'abord un chantier.", false); return; }

        switch (action.action()) {
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
            case "ajouter" -> {
                if (valeur.isEmpty()) { repondre(joueur, "Écris le pseudo du joueur à ajouter.", false); return; }
                ServerPlayer cible = serveur.getPlayerList().getPlayerByName(valeur);
                if (cible == null) { repondre(joueur, valeur + " est introuvable (il doit être connecté).", false); return; }
                Projet autre = Chantiers.de(cible.getUUID());
                if (autre == projet) { repondre(joueur, valeur + " est déjà dans ce chantier.", false); return; }
                if (autre != null) {
                    repondre(joueur, valeur + " est déjà dans un autre chantier : il doit d'abord le quitter.", false);
                    return;
                }
                projet.ajouterMembre(cible);
                projet.forcerEnvoi();
                projet.recompter(serveur);
                projet.diffuser(serveur);
                cible.sendSystemMessage(Component.literal("§e" + joueur.getGameProfile().name()
                        + " t'a ajouté au chantier « " + projet.titre() + " »."));
                repondre(joueur, valeur + " ajouté au chantier.", false);
            }
            case "quitter" -> {
                projet.retirerMembre(joueur.getUUID());
                Projet.envoyerBrut(joueur, Projet.VIDE);
                if (projet.sansMembre()) Chantiers.supprimer(projet, serveur);
                else {
                    projet.forcerEnvoi();
                    projet.recompter(serveur);
                    projet.diffuser(serveur);
                }
                repondre(joueur, "Tu as quitté le chantier.", false);
            }
            case "galerie_utiliser" -> {
                var projetGalerie = GalerieCommune.liste(valeur);
                if (projetGalerie == null) { repondre(joueur, "Ce projet n'est plus dans la galerie.", false); return; }
                appliquerListe(joueur, projet, projetGalerie.getKey(), projetGalerie.getValue());
            }
            case "arreter" -> {
                Chantiers.supprimer(projet, serveur);
                repondre(joueur, "Chantier supprimé.", false);
            }
            default -> { }
        }
    }

    /** Liste lue dans un schéma .litematic par le joueur. */
    static void importer(ImportPayload paquet, IPayloadContext contexte) {
        if (!(contexte.player() instanceof ServerPlayer joueur)) return;
        Projet projet = Chantiers.de(joueur.getUUID());
        if (projet == null) { repondre(joueur, "Crée d'abord un chantier.", false); return; }
        java.util.Map<net.minecraft.world.item.Item, Integer> liste = new java.util.LinkedHashMap<>();
        for (EtatPayload.Ligne l : paquet.lignes()) {
            net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(l.item());
            if (id == null || l.requis() <= 0
                    || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)) continue;
            liste.merge(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(id), l.requis(), Integer::sum);
        }
        if (liste.isEmpty()) { repondre(joueur, "Ce schéma ne contient aucun objet reconnu.", false); return; }
        String nom = paquet.nom().length() > 60 ? paquet.nom().substring(0, 60) : paquet.nom();
        appliquerListe(joueur, projet, nom, liste);
    }

    private static void appliquerListe(ServerPlayer joueur, Projet projet, String nom,
                                       java.util.Map<net.minecraft.world.item.Item, Integer> liste) {
        MinecraftServer serveur = joueur.level().getServer();
        String message = projet.importerSchema(nom, liste);
        projet.forcerEnvoi();
        projet.recompter(serveur);
        projet.diffuser(serveur);
        repondre(joueur, message, false);
    }

    /** Un joueur remplace l'aperçu d'un projet de la galerie par une image. */
    static void image(ImagePayload paquet, IPayloadContext contexte) {
        if (!(contexte.player() instanceof ServerPlayer joueur)) return;
        joueur.sendSystemMessage(Component.literal(GalerieCommune.definirImage(paquet)));
        GalerieCommune.envoyerA(joueur);
    }

    /** Un joueur partage un schéma dans la galerie commune. */
    static void partager(PartagePayload paquet, IPayloadContext contexte) {
        if (!(contexte.player() instanceof ServerPlayer joueur)) return;
        String message = GalerieCommune.partager(joueur, paquet.entree());
        joueur.sendSystemMessage(Component.literal(message));
        GalerieCommune.envoyerA(joueur);
    }

    /** En mode sélection, un clic droit sur un conteneur l'ajoute ou le retire (sans l'ouvrir). */
    static void onClicDroit(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer joueur)) return;
        Projet projet = Chantiers.de(joueur.getUUID());
        if (projet == null || !projet.enSelection(joueur.getUUID())) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) { event.setCanceled(true); return; }
        if (!(joueur.level() instanceof ServerLevel monde)) return;
        event.setCanceled(true);
        String message = projet.basculerCoffre(monde, event.getPos(), monde.getServer());
        joueur.sendSystemMessage(Component.literal(message));
    }

    /** Envoie les réglages à jour (et un message) au joueur, s'il a le mod. */
    static void repondre(ServerPlayer joueur, String message, boolean ouvrir) {
        if (!joueur.connection.hasChannel(ConfigPayload.TYPE)) return;
        Projet projet = Chantiers.de(joueur.getUUID());
        ConfigPayload config = projet != null
                ? projet.config(joueur.getUUID(), message, ouvrir)
                : new ConfigPayload("", "", 0, 0, false, 0, false, "", false, message == null ? "" : message, ouvrir);
        PacketDistributor.sendToPlayer(joueur, config);
    }
}
