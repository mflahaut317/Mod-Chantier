package fr.maxence.chantier.client;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/**
 * Lit un fichier .litematic (Litematica / BlockCompanion) et compte les objets nécessaires.
 * Les blocs à deux parties (portes, lits, grandes fleurs) ne comptent qu'une fois, les dalles doubles deux fois.
 */
final class LecteurLitematic {
    private LecteurLitematic() {}

    /** @return item -> quantité, dans l'ordre décroissant */
    static Map<Item, Integer> lire(Path fichier) throws IOException {
        CompoundTag racine = NbtIo.readCompressed(fichier, NbtAccounter.unlimitedHeap());
        CompoundTag regions = racine.getCompoundOrEmpty("Regions");
        Map<Item, Integer> total = new HashMap<>();
        for (String nom : regions.keySet()) {
            lireRegion(regions.getCompoundOrEmpty(nom), total);
        }
        Map<Item, Integer> trie = new LinkedHashMap<>();
        total.entrySet().stream()
                .sorted(Map.Entry.<Item, Integer>comparingByValue().reversed())
                .forEach(e -> trie.put(e.getKey(), e.getValue()));
        return trie;
    }

    private static void lireRegion(CompoundTag region, Map<Item, Integer> total) {
        CompoundTag taille = region.getCompoundOrEmpty("Size");
        long volume = (long) Math.abs(taille.getIntOr("x", 0)) * Math.abs(taille.getIntOr("y", 0))
                * Math.abs(taille.getIntOr("z", 0));
        ListTag palette = region.getListOrEmpty("BlockStatePalette");
        long[] etats = region.getLongArray("BlockStates").orElse(new long[0]);
        if (volume == 0 || palette.isEmpty() || etats.length == 0) return;

        // Nombre de blocs par entrée de la palette (les valeurs peuvent chevaucher deux longs)
        int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
        long masque = (1L << bits) - 1;
        long[] compte = new long[palette.size()];
        for (long i = 0; i < volume; i++) {
            long debut = i * bits;
            int a = (int) (debut >> 6);
            int b = (int) (((i + 1) * bits - 1) >> 6);
            int decalage = (int) (debut & 63);
            if (b >= etats.length) break;
            long valeur = a == b
                    ? (etats[a] >>> decalage) & masque
                    : ((etats[a] >>> decalage) | (etats[b] << (64 - decalage))) & masque;
            if (valeur < compte.length) compte[(int) valeur]++;
        }

        for (int i = 0; i < palette.size(); i++) {
            if (compte[i] == 0) continue;
            CompoundTag entree = palette.getCompoundOrEmpty(i);
            String nom = entree.getStringOr("Name", "");
            CompoundTag props = entree.getCompoundOrEmpty("Properties");
            Item item = itemPour(nom, props);
            if (item == null) continue;
            long n = compte[i] * multiplicateur(props);
            total.merge(item, (int) Math.min(Integer.MAX_VALUE, n), Integer::sum);
        }
    }

    /** Objet à poser pour ce bloc, ou null s'il ne compte pas (air, moitié haute d'une porte…). */
    private static Item itemPour(String nom, CompoundTag props) {
        if (nom.endsWith("air")) return null;
        String moitie = props.getStringOr("half", "");
        String partie = props.getStringOr("part", "");
        if (moitie.equals("upper") || partie.equals("head")) return null; // portes, grandes fleurs, lits
        if (nom.equals("minecraft:water")) return props.getStringOr("level", "0").equals("0") ? Items.WATER_BUCKET : null;
        if (nom.equals("minecraft:lava")) return props.getStringOr("level", "0").equals("0") ? Items.LAVA_BUCKET : null;
        Identifier id = Identifier.tryParse(nom);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) return null;
        Block bloc = BuiltInRegistries.BLOCK.getValue(id);
        Item item = bloc.asItem();
        return item == Items.AIR ? null : item;
    }

    /** Dalle double = 2, bougies et cornichons de mer = leur nombre. */
    private static int multiplicateur(CompoundTag props) {
        if (props.getStringOr("type", "").equals("double")) return 2;
        for (String cle : new String[]{"candles", "pickles", "eggs", "flower_amount", "segment_amount"}) {
            String v = props.getStringOr(cle, "");
            if (!v.isEmpty()) {
                try { return Math.max(1, Integer.parseInt(v)); } catch (NumberFormatException ignored) { }
            }
        }
        return 1;
    }
}
