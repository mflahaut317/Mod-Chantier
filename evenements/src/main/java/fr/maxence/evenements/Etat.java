package fr.maxence.evenements;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/**
 * Ce qui est gardé sur le disque (dossier « evenements » du monde) :
 * - l'inventaire et la position de survie de chaque joueur parti en événement (rendus au retour, même après un redémarrage) ;
 * - le point d'apparition du monde d'événement et la liste des organisateurs.
 */
final class Etat {
    private Etat() {}

    static double spawnX = 0.5, spawnY = -60, spawnZ = 0.5;
    static float spawnYaw = 0;
    static final Set<String> ORGANISATEURS = new LinkedHashSet<>();

    private static Path dossier(MinecraftServer serveur) {
        return serveur.getWorldPath(LevelResource.ROOT).resolve("evenements");
    }

    private static Path fichierJoueur(MinecraftServer serveur, ServerPlayer joueur) {
        return dossier(serveur).resolve("joueurs").resolve(joueur.getUUID() + ".dat");
    }

    // ---------- Réglages ----------

    static void charger(MinecraftServer serveur) {
        Path f = dossier(serveur).resolve("reglages.properties");
        ORGANISATEURS.clear();
        if (!Files.exists(f)) return;
        Properties p = new Properties();
        try (var in = Files.newBufferedReader(f)) {
            p.load(in);
        } catch (IOException e) {
            Evenements.LOGGER.warn("Réglages des événements illisibles", e);
            return;
        }
        spawnX = Double.parseDouble(p.getProperty("spawn_x", "0.5"));
        spawnY = Double.parseDouble(p.getProperty("spawn_y", "-60"));
        spawnZ = Double.parseDouble(p.getProperty("spawn_z", "0.5"));
        spawnYaw = Float.parseFloat(p.getProperty("spawn_yaw", "0"));
        for (String n : p.getProperty("organisateurs", "").split(",")) {
            if (!n.isBlank()) ORGANISATEURS.add(n.trim().toLowerCase(Locale.ROOT));
        }
    }

    static void sauver(MinecraftServer serveur) {
        Properties p = new Properties();
        p.setProperty("spawn_x", Double.toString(spawnX));
        p.setProperty("spawn_y", Double.toString(spawnY));
        p.setProperty("spawn_z", Double.toString(spawnZ));
        p.setProperty("spawn_yaw", Float.toString(spawnYaw));
        p.setProperty("organisateurs", String.join(",", ORGANISATEURS));
        try {
            Files.createDirectories(dossier(serveur));
            try (var out = Files.newBufferedWriter(dossier(serveur).resolve("reglages.properties"))) {
                p.store(out, "Evenements : point d'apparition du monde d'evenement et organisateurs");
            }
        } catch (IOException e) {
            Evenements.LOGGER.warn("Impossible d'enregistrer les réglages des événements", e);
        }
    }

    static boolean estOrganisateur(ServerPlayer joueur) {
        return ORGANISATEURS.contains(joueur.getScoreboardName().toLowerCase(Locale.ROOT));
    }

    // ---------- Joueurs ----------

    /** Le joueur est-il parti en événement (affaires de survie mises de côté) ? */
    static boolean enEvenement(MinecraftServer serveur, ServerPlayer joueur) {
        return Files.exists(fichierJoueur(serveur, joueur));
    }

    static List<ServerPlayer> participants(MinecraftServer serveur) {
        List<ServerPlayer> liste = new ArrayList<>();
        for (ServerPlayer j : serveur.getPlayerList().getPlayers()) {
            if (j.level().dimension() == Evenements.ARENE && enEvenement(serveur, j)) liste.add(j);
        }
        return liste;
    }

    private static String nomDimension(ServerPlayer joueur) {
        var d = joueur.level().dimension();
        if (d == Level.NETHER) return "minecraft:the_nether";
        if (d == Level.END) return "minecraft:the_end";
        return "minecraft:overworld";
    }

    /** Met de côté l'inventaire et la position de survie, puis vide l'inventaire. */
    static boolean mettreDeCote(MinecraftServer serveur, ServerPlayer joueur) {
        RegistryOps<Tag> ops = serveur.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        Inventory inv = joueur.getInventory();
        ListTag objets = new ListTag();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            var encode = ItemStack.OPTIONAL_CODEC.encodeStart(ops, stack).result();
            if (encode.isEmpty()) {
                // Un objet impossible à enregistrer : on ne vide rien, le joueur garde ses affaires
                joueur.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§cImpossible de mettre ton inventaire de côté (objet « " + stack.getHoverName().getString() + " »). Range-le dans un coffre."));
                return false;
            }
            CompoundTag c = new CompoundTag();
            c.putInt("Slot", i);
            c.put("Item", encode.get());
            objets.add(c);
        }
        CompoundTag racine = new CompoundTag();
        racine.put("Inventaire", objets);
        racine.putString("Dimension", nomDimension(joueur));
        racine.putDouble("X", joueur.getX());
        racine.putDouble("Y", joueur.getY());
        racine.putDouble("Z", joueur.getZ());
        racine.putFloat("Yaw", joueur.getYRot());
        racine.putFloat("Pitch", joueur.getXRot());
        try {
            Path f = fichierJoueur(serveur, joueur);
            Files.createDirectories(f.getParent());
            NbtIo.writeCompressed(racine, f);
        } catch (IOException e) {
            Evenements.LOGGER.error("Sauvegarde de l'inventaire de {} impossible", joueur.getScoreboardName(), e);
            joueur.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cSauvegarde de ton inventaire impossible : tu restes ici."));
            return false;
        }
        inv.clearContent();
        return true;
    }

    /** Rend l'inventaire et renvoie le joueur là où il était. */
    static void rendre(MinecraftServer serveur, ServerPlayer joueur) {
        Path f = fichierJoueur(serveur, joueur);
        if (!Files.exists(f)) return;
        CompoundTag racine;
        try {
            racine = NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            Evenements.LOGGER.error("Lecture de l'inventaire de {} impossible", joueur.getScoreboardName(), e);
            joueur.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cTon inventaire n'a pas pu être relu : préviens Maxence, rien n'est perdu."));
            return;
        }
        RegistryOps<Tag> ops = serveur.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        Inventory inv = joueur.getInventory();
        inv.clearContent();
        for (Tag t : racine.getList("Inventaire").orElse(new ListTag())) {
            if (!(t instanceof CompoundTag c)) continue;
            Tag item = c.get("Item");
            if (item == null) continue;
            ItemStack stack = ItemStack.OPTIONAL_CODEC.parse(ops, item).result().orElse(ItemStack.EMPTY);
            int slot = c.getIntOr("Slot", -1);
            if (slot >= 0 && slot < inv.getContainerSize()) inv.setItem(slot, stack);
        }
        String nom = joueur.getScoreboardName();
        Outils.commande(serveur, String.format(Locale.ROOT, "execute in %s run tp %s %.3f %.3f %.3f %.1f %.1f",
                racine.getStringOr("Dimension", "minecraft:overworld"), nom,
                racine.getDoubleOr("X", 0), racine.getDoubleOr("Y", 100), racine.getDoubleOr("Z", 0),
                racine.getFloatOr("Yaw", 0), racine.getFloatOr("Pitch", 0)));
        try {
            Files.delete(f);
        } catch (IOException e) {
            Evenements.LOGGER.warn("Fichier d'événement de {} non supprimé", nom, e);
        }
    }
}
