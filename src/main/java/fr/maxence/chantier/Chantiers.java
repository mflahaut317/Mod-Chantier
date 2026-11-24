package fr.maxence.chantier;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Tous les chantiers du serveur : un par groupe (ou par personne seule).
 * Un joueur est membre d'au plus un chantier. Sauvegardé dans config/chantier.json.
 */
public final class Chantiers {
    private Chantiers() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, Projet> PAR_ID = new LinkedHashMap<>();

    /** Format du fichier. */
    private static final class Fichier {
        List<Projet.Sauvegarde> chantiers = new ArrayList<>();
    }

    /** Le chantier dont ce joueur est membre, ou null. */
    public static Projet de(UUID joueur) {
        for (Projet p : PAR_ID.values()) if (p.estMembre(joueur)) return p;
        return null;
    }

    public static Projet creer(ServerPlayer createur, String titre) {
        Projet.Sauvegarde d = new Projet.Sauvegarde();
        d.titre = titre == null || titre.isBlank() ? "Chantier" : titre.trim();
        Projet p = new Projet(d);
        PAR_ID.put(d.id, p);
        p.ajouterMembre(createur); // sauvegarde
        return p;
    }

    /** Supprime le chantier pour tout le monde ; les membres connectés voient la liste disparaître. */
    public static void supprimer(Projet p, MinecraftServer serveur) {
        List<ServerPlayer> membres = p.membresEnLigne(serveur);
        HoloServeur.retirer(p, serveur);
        PAR_ID.remove(p.donnees().id);
        sauver();
        for (ServerPlayer j : membres) Projet.envoyerBrut(j, Projet.VIDE);
    }

    /** Toutes les 5 s : seuls les chantiers dont un membre est connecté sont recomptés (et renvoyés s'ils ont changé). */
    public static void tick(MinecraftServer serveur) {
        // Party liée : les nouveaux membres de la party rejoignent le chantier
        for (Projet p : new ArrayList<>(PAR_ID.values())) {
            for (String nom : Parties.synchroniser(p, serveur)) {
                for (ServerPlayer m : p.membresEnLigne(serveur)) {
                    m.sendSystemMessage(net.minecraft.network.chat.Component.literal("§e" + nom + " rejoint le chantier (party)."));
                    if (m.getGameProfile().name().equals(nom)) HoloServeur.envoyerA(m, p);
                }
                p.forcerEnvoi();
            }
        }
        for (Projet p : new ArrayList<>(PAR_ID.values())) {
            if (!p.estActif() || p.membresEnLigne(serveur).isEmpty()) continue;
            p.recompter(serveur);
            p.diffuser(serveur);
        }
    }

    /** Chaque seconde : particules sur les coffres pour les joueurs en mode sélection. */
    public static void tickSeconde(MinecraftServer serveur) {
        for (ServerPlayer j : serveur.getPlayerList().getPlayers()) {
            Projet p = de(j.getUUID());
            if (p != null) p.montrerCoffres(j);
        }
    }

    public static void onConnexion(ServerPlayer joueur) {
        Projet p = de(joueur.getUUID());
        if (p == null) {
            Projet.envoyerBrut(joueur, Projet.VIDE);
            return;
        }
        p.noterPseudo(joueur);
        p.envoyerA(joueur);
        HoloServeur.envoyerA(joueur, p);
    }

    /** Au démarrage : relit le fichier et retélécharge la feuille de chaque chantier. */
    public static void charger(MinecraftServer serveur) {
        PAR_ID.clear();
        GalerieCommune.charger();
        Path f = fichier();
        if (!Files.exists(f)) return;
        try {
            JsonObject racine = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!racine.has("chantiers")) {
                // Ancien format (un seul chantier sans membres) : on repart de zéro.
                Chantier.LOGGER.info("[Chantier] Ancien format de chantier.json ignoré : recrée le chantier en jeu.");
                return;
            }
            Fichier lu = GSON.fromJson(racine, Fichier.class);
            if (lu == null || lu.chantiers == null) return;
            for (Projet.Sauvegarde d : lu.chantiers) {
                if (d == null || d.id == null || d.membres == null || d.membres.isEmpty()) continue;
                PAR_ID.put(d.id, new Projet(d));
            }
        } catch (IOException | RuntimeException e) {
            Chantier.LOGGER.error("[Chantier] Lecture de chantier.json impossible", e);
        }
        for (Projet p : PAR_ID.values()) {
            if (p.aUnSchema()) p.restaurerSchema();
            else if (p.aUnLien()) p.telechargerListe(serveur, null);
        }
    }

    static void sauver() {
        Fichier f = new Fichier();
        for (Projet p : PAR_ID.values()) f.chantiers.add(p.donnees());
        try {
            Files.writeString(fichier(), GSON.toJson(f), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Chantier.LOGGER.error("[Chantier] Écriture de chantier.json impossible", e);
        }
    }

    private static Path fichier() { return FMLPaths.CONFIGDIR.get().resolve("chantier.json"); }
}
