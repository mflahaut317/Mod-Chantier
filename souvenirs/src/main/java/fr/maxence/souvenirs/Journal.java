package fr.maxence.souvenirs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Le journal du serveur : config/souvenirs/journal.json, et les captures dans config/souvenirs/photos/<id>.jpg.
 * Tout le monde peut tout faire : ajouter, illustrer, supprimer.
 */
public final class Journal {
    private Journal() {}

    private static final int MAX_ENTREES = 3000;
    private static final int TAILLE_PHOTO_MAX = 2_500_000;
    private static final int MORCEAU = 500_000;
    private static final Gson GSON = new GsonBuilder().create();

    private static final class Fichier { List<Entree> entrees = new ArrayList<>(); }

    private static final List<Entree> ENTREES = new ArrayList<>();
    private static final Map<UUID, ByteArrayOutputStream> RECEPTION = new HashMap<>();

    private static Path dossier() { return FMLPaths.CONFIGDIR.get().resolve("souvenirs"); }

    private static Path photo(String id) { return dossier().resolve("photos").resolve(id.replaceAll("[^a-z0-9-]", "") + ".jpg"); }

    // ---------- Ajout ----------

    /** Texte Minecraft -> JSON (gardé tel quel : il sera traduit dans la langue de chaque joueur). */
    static String json(MinecraftServer serveur, Component c) {
        try {
            return ComponentSerialization.CODEC.encodeStart(serveur.registryAccess().createSerializationContext(JsonOps.INSTANCE), c)
                    .getOrThrow().toString();
        } catch (RuntimeException e) {
            return ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, Component.literal(c.getString())).getOrThrow().toString();
        }
    }

    /** Ajoute un souvenir, le sauvegarde et l'annonce dans le chat à tout le serveur. */
    public static void ajouter(MinecraftServer serveur, String joueur, String type, String icone,
                               Component titre, Component texte, String lieu) {
        Entree e = new Entree(UUID.randomUUID().toString().substring(0, 13), System.currentTimeMillis(), joueur, type, icone,
                json(serveur, titre), json(serveur, texte), lieu, false);
        ENTREES.add(e);
        while (ENTREES.size() > MAX_ENTREES) ENTREES.remove(0);
        sauver();
        Component annonce = Component.literal("§6✎ Journal : §r").append(titre).append(Component.literal(" "))
                .append(Component.literal("§a[ouvrir]").withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/journal"))));
        for (ServerPlayer p : serveur.getPlayerList().getPlayers()) p.sendSystemMessage(annonce);
    }

    /** Appelé par d'autres mods (Chantier) sans en dépendre : voir SouvenirsApi. */
    static void ajouterDepuisApi(String joueur, String type, String icone, String titre, String texte) {
        MinecraftServer serveur = ServerLifecycleHooks.getCurrentServer();
        if (serveur == null) return;
        serveur.execute(() -> ajouter(serveur, joueur, type, icone, Component.literal(titre), Component.literal(texte), ""));
    }

    // ---------- Réseau ----------

    static void envoyerListe(ServerPlayer joueur, boolean ouvrir) {
        if (!joueur.connection.hasChannel(Paquets.Liste.TYPE)) {
            if (ouvrir) joueur.sendSystemMessage(Component.literal("§cInstalle le mod Souvenirs pour lire le journal."));
            return;
        }
        List<Entree> liste = ENTREES.size() > 2000 ? new ArrayList<>(ENTREES.subList(ENTREES.size() - 2000, ENTREES.size())) : new ArrayList<>(ENTREES);
        PacketDistributor.sendToPlayer(joueur, new Paquets.Liste(liste, ouvrir));
    }

    static void demande(Paquets.Demande d, IPayloadContext contexte) {
        if (!(contexte.player() instanceof ServerPlayer joueur)) return;
        switch (d.action()) {
            case "liste" -> envoyerListe(joueur, false);
            case "image" -> envoyerPhoto(joueur, d.valeur());
            case "supprimer" -> {
                ENTREES.removeIf(e -> e.id().equals(d.valeur()));
                try { Files.deleteIfExists(photo(d.valeur())); } catch (IOException ignored) { }
                sauver();
                envoyerListe(joueur, false);
            }
            default -> { }
        }
    }

    static void creer(Paquets.Creer c, IPayloadContext contexte) {
        if (!(contexte.player() instanceof ServerPlayer joueur)) return;
        String titre = c.titre().strip();
        if (titre.isEmpty()) return;
        if (titre.length() > 80) titre = titre.substring(0, 80);
        String texte = c.texte().strip();
        if (texte.length() > 500) texte = texte.substring(0, 500);
        ajouter(joueur.level().getServer(), joueur.getGameProfile().name(), "perso", c.icone(),
                Component.literal(titre), Component.literal(texte), lieu(joueur));
        envoyerListe(joueur, false);
    }

    /** Un morceau de capture envoyé par un joueur ; au dernier, la photo est enregistrée. */
    static void photoRecue(Paquets.PhotoEnvoi p, IPayloadContext contexte) {
        if (!(contexte.player() instanceof ServerPlayer joueur)) return;
        ByteArrayOutputStream tampon = p.numero() == 0 ? new ByteArrayOutputStream() : RECEPTION.get(joueur.getUUID());
        if (tampon == null) return;
        RECEPTION.put(joueur.getUUID(), tampon);
        tampon.writeBytes(p.donnees());
        if (tampon.size() > TAILLE_PHOTO_MAX) { RECEPTION.remove(joueur.getUUID()); return; }
        if (p.numero() < p.total() - 1) return;
        RECEPTION.remove(joueur.getUUID());
        for (int i = 0; i < ENTREES.size(); i++) {
            Entree e = ENTREES.get(i);
            if (!e.id().equals(p.id())) continue;
            try {
                Files.createDirectories(photo(e.id()).getParent());
                Files.write(photo(e.id()), tampon.toByteArray());
            } catch (IOException ex) {
                joueur.sendSystemMessage(Component.literal("§cImpossible d'enregistrer la capture."));
                return;
            }
            ENTREES.set(i, new Entree(e.id(), e.date(), e.joueur(), e.type(), e.icone(), e.titre(), e.texte(), e.lieu(), true));
            sauver();
            joueur.sendSystemMessage(Component.literal("§aCapture ajoutée au souvenir."));
            envoyerListe(joueur, false);
            return;
        }
    }

    static void oublier(UUID joueur) { RECEPTION.remove(joueur); }

    private static void envoyerPhoto(ServerPlayer joueur, String id) {
        byte[] tout;
        try { tout = Files.readAllBytes(photo(id)); } catch (IOException e) { return; }
        int total = Math.max(1, (tout.length + MORCEAU - 1) / MORCEAU);
        for (int i = 0; i < total; i++) {
            PacketDistributor.sendToPlayer(joueur, new Paquets.Photo(id, i, total,
                    Arrays.copyOfRange(tout, i * MORCEAU, Math.min(tout.length, (i + 1) * MORCEAU))));
        }
    }

    /** « Nether · 120 64 -30 » */
    static String lieu(ServerPlayer p) {
        String dim = p.level().dimension().identifier().getPath();
        String nom = switch (dim) {
            case "overworld" -> "Monde normal";
            case "the_nether" -> "Nether";
            case "the_end" -> "End";
            default -> dim;
        };
        return nom + " · " + p.getBlockX() + " " + p.getBlockY() + " " + p.getBlockZ();
    }

    // ---------- Fichier ----------

    static void charger() {
        ENTREES.clear();
        Path f = dossier().resolve("journal.json");
        if (!Files.exists(f)) return;
        try {
            Fichier lu = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Fichier.class);
            if (lu != null && lu.entrees != null) for (Entree e : lu.entrees) if (e != null && e.id() != null) ENTREES.add(e);
        } catch (IOException | RuntimeException e) {
            Souvenirs.LOGGER.error("[Souvenirs] Lecture du journal impossible", e);
        }
    }

    private static void sauver() {
        Fichier f = new Fichier();
        f.entrees.addAll(ENTREES);
        try {
            Files.createDirectories(dossier());
            Files.writeString(dossier().resolve("journal.json"), GSON.toJson(f), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Souvenirs.LOGGER.error("[Souvenirs] Écriture du journal impossible", e);
        }
    }

    /** Pour lire un JSON de texte (utilisé côté joueur). */
    static boolean estJson(String s) {
        try { JsonParser.parseString(s); return true; } catch (RuntimeException e) { return false; }
    }
}
