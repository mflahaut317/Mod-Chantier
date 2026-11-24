package fr.maxence.chantier;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Galerie commune : projets partagés par les joueurs (nom, auteur, liste de matériaux, vue du dessus).
 * Les fichiers de schéma restent chez les joueurs. Sauvegardé dans config/chantier-galerie.json.
 */
public final class GalerieCommune {
    private GalerieCommune() {}

    private static final int MAX_ENTREES = 300;
    private static final int APERCU_MAX = 64;
    private static final Gson GSON = new GsonBuilder().create();

    private static final class Projet {
        String id;
        String nom;
        String auteur;
        int largeur;
        int hauteur;
        String apercu; // base64
        Map<String, Integer> lignes = new LinkedHashMap<>();
    }

    private static final class Fichier {
        List<Projet> projets = new ArrayList<>();
    }

    private static final List<Projet> PROJETS = new ArrayList<>();

    /** Ajoute (ou remplace, même nom + même auteur) un projet partagé. @return message pour le joueur */
    static String partager(ServerPlayer joueur, GaleriePayload.Entree e) {
        if (e.largeur() < 0 || e.hauteur() < 0 || e.largeur() > APERCU_MAX || e.hauteur() > APERCU_MAX
                || e.apercu().length != e.largeur() * e.hauteur() * 3) {
            return "Aperçu invalide.";
        }
        Projet p = new Projet();
        p.id = UUID.randomUUID().toString();
        p.nom = e.nom().isBlank() ? "Sans nom" : e.nom().strip();
        if (p.nom.length() > 60) p.nom = p.nom.substring(0, 60);
        p.auteur = joueur.getGameProfile().name();
        p.largeur = e.largeur();
        p.hauteur = e.hauteur();
        p.apercu = Base64.getEncoder().encodeToString(e.apercu());
        for (EtatPayload.Ligne l : e.lignes()) {
            Identifier id = Identifier.tryParse(l.item());
            if (id != null && l.requis() > 0 && BuiltInRegistries.ITEM.containsKey(id)) p.lignes.merge(id.toString(), l.requis(), Integer::sum);
        }
        if (p.lignes.isEmpty()) return "Ce schéma ne contient aucun objet reconnu.";
        PROJETS.removeIf(x -> x.nom.equalsIgnoreCase(p.nom) && x.auteur.equals(p.auteur));
        if (PROJETS.size() >= MAX_ENTREES) return "La galerie est pleine (" + MAX_ENTREES + " projets).";
        PROJETS.add(0, p);
        sauver();
        return "« " + p.nom + " » partagé dans la galerie commune.";
    }

    static boolean supprimer(String id) {
        boolean retire = PROJETS.removeIf(p -> p.id.equals(id));
        if (retire) sauver();
        return retire;
    }

    /** Nom et liste d'un projet, pour l'utiliser comme liste de chantier (null si introuvable). */
    static Map.Entry<String, Map<Item, Integer>> liste(String id) {
        for (Projet p : PROJETS) {
            if (!p.id.equals(id)) continue;
            Map<Item, Integer> m = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> e : p.lignes.entrySet()) {
                Identifier i = Identifier.tryParse(e.getKey());
                if (i != null && BuiltInRegistries.ITEM.containsKey(i)) m.put(BuiltInRegistries.ITEM.getValue(i), e.getValue());
            }
            return Map.entry(p.nom, m);
        }
        return null;
    }

    static void envoyerA(ServerPlayer joueur) {
        if (!joueur.connection.hasChannel(GaleriePayload.TYPE)) return;
        List<GaleriePayload.Entree> entrees = new ArrayList<>();
        for (Projet p : PROJETS) {
            List<EtatPayload.Ligne> lignes = new ArrayList<>();
            for (Map.Entry<String, Integer> e : p.lignes.entrySet()) lignes.add(new EtatPayload.Ligne(e.getKey(), e.getValue(), 0));
            byte[] apercu;
            try { apercu = Base64.getDecoder().decode(p.apercu == null ? "" : p.apercu); } catch (IllegalArgumentException ex) { apercu = new byte[0]; }
            boolean ok = apercu.length == p.largeur * p.hauteur * 3;
            entrees.add(new GaleriePayload.Entree(p.id, p.nom, p.auteur, ok ? p.largeur : 0, ok ? p.hauteur : 0,
                    ok ? apercu : new byte[0], lignes));
        }
        PacketDistributor.sendToPlayer(joueur, new GaleriePayload(entrees));
    }

    static void charger() {
        PROJETS.clear();
        Path f = fichier();
        if (!Files.exists(f)) return;
        try {
            Fichier lu = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Fichier.class);
            if (lu != null && lu.projets != null) {
                for (Projet p : lu.projets) if (p != null && p.id != null && p.lignes != null) PROJETS.add(p);
            }
        } catch (IOException | RuntimeException e) {
            Chantier.LOGGER.error("[Chantier] Lecture de chantier-galerie.json impossible", e);
        }
    }

    private static void sauver() {
        Fichier f = new Fichier();
        f.projets.addAll(PROJETS);
        try {
            Files.writeString(fichier(), GSON.toJson(f), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Chantier.LOGGER.error("[Chantier] Écriture de chantier-galerie.json impossible", e);
        }
    }

    private static Path fichier() { return FMLPaths.CONFIGDIR.get().resolve("chantier-galerie.json"); }
}
