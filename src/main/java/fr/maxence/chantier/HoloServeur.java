package fr.maxence.chantier;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Hologramme partagé d'un chantier : les blocs (compressés) sont gardés dans config/chantier-holo/<id>.bin,
 * la position dans chantier.json. Tous les membres connectés le reçoivent, et ceux qui arrivent ensuite aussi.
 */
public final class HoloServeur {
    private HoloServeur() {}

    private static final int TAILLE_MAX = 6_000_000;
    private static final int MORCEAU = 500_000;
    private static final Map<UUID, ByteArrayOutputStream> RECEPTION = new HashMap<>();

    private static Path fichier(Projet p) {
        return FMLPaths.CONFIGDIR.get().resolve("chantier-holo").resolve(p.donnees().id + ".bin");
    }

    /** Un morceau envoyé par un joueur ; au dernier, le hologramme devient celui du chantier. */
    static void recevoir(HoloEnvoiPayload paquet, IPayloadContext contexte) {
        if (!(contexte.player() instanceof ServerPlayer joueur)) return;
        Projet projet = Chantiers.de(joueur.getUUID());
        if (projet == null) {
            joueur.sendSystemMessage(Component.literal("§cCrée ou rejoins d'abord un chantier pour partager le hologramme."));
            return;
        }
        ByteArrayOutputStream tampon = paquet.morceau() == 0 ? new ByteArrayOutputStream() : RECEPTION.get(joueur.getUUID());
        if (tampon == null) return;
        RECEPTION.put(joueur.getUUID(), tampon);
        tampon.writeBytes(paquet.donnees());
        if (tampon.size() > TAILLE_MAX) {
            RECEPTION.remove(joueur.getUUID());
            joueur.sendSystemMessage(Component.literal("§cSchéma trop gros pour être partagé."));
            return;
        }
        if (paquet.morceau() < paquet.total() - 1) return;
        RECEPTION.remove(joueur.getUUID());
        try {
            Files.createDirectories(fichier(projet).getParent());
            Files.write(fichier(projet), tampon.toByteArray());
        } catch (IOException e) {
            joueur.sendSystemMessage(Component.literal("§cImpossible d'enregistrer le hologramme sur le serveur."));
            return;
        }
        Projet.Sauvegarde d = projet.donnees();
        d.holoNom = paquet.nom().isBlank() ? "Schéma" : paquet.nom();
        d.holoVersion++;
        d.holoX = paquet.x(); d.holoY = paquet.y(); d.holoZ = paquet.z();
        d.holoRotation = paquet.rotation();
        d.holoDimension = paquet.dimension();
        Chantiers.sauver();
        MinecraftServer serveur = joueur.level().getServer();
        for (ServerPlayer m : projet.membresEnLigne(serveur)) {
            envoyerA(m, projet);
            if (m != joueur) m.sendSystemMessage(Component.literal("§e" + joueur.getGameProfile().name()
                    + " a placé le hologramme « " + d.holoNom + " »."));
        }
        joueur.sendSystemMessage(Component.literal("§aHologramme partagé avec le groupe."));
    }

    /** Déconnexion : un envoi interrompu ne reste pas en mémoire. */
    public static void oublier(UUID joueur) {
        RECEPTION.remove(joueur);
    }

    /** valeur : x;y;z;rotation;dimension — déplacé ou tourné par un membre. */
    static void position(ServerPlayer joueur, Projet projet, String valeur) {
        Projet.Sauvegarde d = projet.donnees();
        if (d.holoNom == null) return;
        String[] v = valeur.split(";");
        if (v.length < 5) return;
        try {
            d.holoX = Integer.parseInt(v[0]); d.holoY = Integer.parseInt(v[1]); d.holoZ = Integer.parseInt(v[2]);
            d.holoRotation = Math.floorMod(Integer.parseInt(v[3]), 4);
        } catch (NumberFormatException e) {
            return;
        }
        d.holoDimension = v[4];
        Chantiers.sauver();
        HoloPayload p = new HoloPayload(d.holoVersion, d.holoNom, d.holoX, d.holoY, d.holoZ, d.holoRotation,
                d.holoDimension, 0, 0, new byte[0]);
        for (ServerPlayer m : projet.membresEnLigne(joueur.level().getServer())) {
            if (m != joueur && m.connection.hasChannel(HoloPayload.TYPE)) PacketDistributor.sendToPlayer(m, p);
        }
    }

    /** Retire le hologramme du chantier, pour tout le groupe. */
    static void retirer(Projet projet, MinecraftServer serveur) {
        Projet.Sauvegarde d = projet.donnees();
        if (d.holoNom == null) return;
        d.holoNom = null;
        Chantiers.sauver();
        try { Files.deleteIfExists(fichier(projet)); } catch (IOException ignored) { }
        for (ServerPlayer m : projet.membresEnLigne(serveur)) envoyerRetrait(m);
    }

    static void envoyerRetrait(ServerPlayer joueur) {
        if (joueur.connection.hasChannel(HoloPayload.TYPE)) {
            PacketDistributor.sendToPlayer(joueur, new HoloPayload(0, "", 0, 0, 0, 0, "", 0, -1, new byte[0]));
        }
    }

    /** Envoie le hologramme du chantier (s'il y en a un) à un membre, en morceaux. */
    static void envoyerA(ServerPlayer joueur, Projet projet) {
        Projet.Sauvegarde d = projet.donnees();
        if (d.holoNom == null || !joueur.connection.hasChannel(HoloPayload.TYPE)) return;
        byte[] tout;
        try {
            tout = Files.readAllBytes(fichier(projet));
        } catch (IOException e) {
            return;
        }
        int total = Math.max(1, (tout.length + MORCEAU - 1) / MORCEAU);
        for (int i = 0; i < total; i++) {
            byte[] morceau = Arrays.copyOfRange(tout, i * MORCEAU, Math.min(tout.length, (i + 1) * MORCEAU));
            PacketDistributor.sendToPlayer(joueur, new HoloPayload(d.holoVersion, d.holoNom, d.holoX, d.holoY, d.holoZ,
                    d.holoRotation, d.holoDimension == null ? "" : d.holoDimension, i, total, morceau));
        }
    }
}
