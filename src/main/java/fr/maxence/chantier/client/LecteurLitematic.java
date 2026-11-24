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
import net.minecraft.world.level.block.Blocks;

/**
 * Lit un schéma (.litematic, .schem, .nbt), compte les objets nécessaires et dessine une vue du dessus.
 * Les blocs à deux parties (portes, lits, grandes fleurs) ne comptent qu'une fois, les dalles doubles deux fois.
 */
final class LecteurLitematic {
    private LecteurLitematic() {}

    /** Taille max de l'aperçu (en pixels de côté). */
    static final int APERCU_MAX = 48;

    /** Résultat : liste triée + vue du dessus (rgb, 0 = vide). */
    record Lecture(Map<Item, Integer> blocs, int largeur, int hauteur, int[] pixels) {}

    /** Extensions lues : Litematica / BlockCompanion, WorldEdit (Sponge), structures / Create. */
    static boolean estSchema(Path fichier) {
        String n = fichier.getFileName().toString().toLowerCase();
        return n.endsWith(".litematic") || n.endsWith(".schem") || n.endsWith(".nbt");
    }

    /** Vue du dessus : bloc le plus haut de chaque colonne, coloré comme sur une carte. */
    private static final class Apercu {
        final int sx, sz;
        final int[] hauteurs, couleurs;
        int maxY = 1;

        Apercu(int sx, int sz) {
            this.sx = Math.max(1, sx);
            this.sz = Math.max(1, sz);
            hauteurs = new int[this.sx * this.sz];
            couleurs = new int[this.sx * this.sz];
            java.util.Arrays.fill(hauteurs, -1);
        }

        void bloc(int x, int y, int z, int rgb) {
            if (rgb == 0 || x < 0 || z < 0 || x >= sx || z >= sz) return;
            int i = x + z * sx;
            if (y > hauteurs[i]) { hauteurs[i] = y; couleurs[i] = rgb; }
            if (y > maxY) maxY = y;
        }

        /** Réduit à APERCU_MAX de côté et assombrit selon la hauteur (relief). */
        Lecture vers(Map<Item, Integer> blocs) {
            int pas = Math.max(1, (int) Math.ceil(Math.max(sx, sz) / (double) APERCU_MAX));
            int w = (sx + pas - 1) / pas, h = (sz + pas - 1) / pas;
            int[] px = new int[w * h];
            for (int cz = 0; cz < h; cz++) {
                for (int cx = 0; cx < w; cx++) {
                    int meilleur = -1, couleur = 0;
                    for (int z = cz * pas; z < Math.min(sz, (cz + 1) * pas); z++) {
                        for (int x = cx * pas; x < Math.min(sx, (cx + 1) * pas); x++) {
                            int i = x + z * sx;
                            if (hauteurs[i] > meilleur) { meilleur = hauteurs[i]; couleur = couleurs[i]; }
                        }
                    }
                    if (meilleur < 0) continue;
                    double f = 0.6 + 0.4 * meilleur / (double) maxY;
                    int r = (int) (((couleur >> 16) & 255) * f), g = (int) (((couleur >> 8) & 255) * f), b = (int) ((couleur & 255) * f);
                    px[cx + cz * w] = Math.max(1, (r << 16) | (g << 8) | b);
                }
            }
            return new Lecture(blocs, w, h, px);
        }
    }

    /** Lit le fichier : liste triée par quantité + aperçu. */
    static Lecture lire(Path fichier) throws IOException {
        CompoundTag racine = NbtIo.readCompressed(fichier, NbtAccounter.unlimitedHeap());
        Map<Item, Integer> total = new HashMap<>();
        Apercu[] apercu = new Apercu[1];
        String nomFichier = fichier.getFileName().toString().toLowerCase();
        if (nomFichier.endsWith(".schem")) {
            lireSponge(racine, total, apercu);
        } else if (nomFichier.endsWith(".nbt")) {
            lireStructure(racine, total, apercu);
        } else {
            CompoundTag regions = racine.getCompoundOrEmpty("Regions");
            long plusGrand = 0;
            for (String nom : regions.keySet()) {
                plusGrand = Math.max(plusGrand, lireRegion(regions.getCompoundOrEmpty(nom), total, apercu, plusGrand));
            }
        }
        Map<Item, Integer> trie = new LinkedHashMap<>();
        total.entrySet().stream()
                .sorted(Map.Entry.<Item, Integer>comparingByValue().reversed())
                .forEach(e -> trie.put(e.getKey(), e.getValue()));
        return apercu[0] == null ? new Lecture(trie, 0, 0, new int[0]) : apercu[0].vers(trie);
    }

    /** Couleur « carte » d'un bloc (0 = transparent ou inconnu). */
    private static int couleur(String nom) {
        if (nom.endsWith("air")) return 0;
        Identifier id = Identifier.tryParse(nom);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) return 0;
        Block bloc = BuiltInRegistries.BLOCK.getValue(id);
        if (bloc == Blocks.AIR) return 0;
        return bloc.defaultMapColor().col;
    }

    /** @return le volume de la région (l'aperçu est fait sur la plus grande) */
    private static long lireRegion(CompoundTag region, Map<Item, Integer> total, Apercu[] apercu, long plusGrand) {
        CompoundTag taille = region.getCompoundOrEmpty("Size");
        int sx = Math.abs(taille.getIntOr("x", 0)), sy = Math.abs(taille.getIntOr("y", 0)), sz = Math.abs(taille.getIntOr("z", 0));
        long volume = (long) sx * sy * sz;
        ListTag palette = region.getListOrEmpty("BlockStatePalette");
        long[] etats = region.getLongArray("BlockStates").orElse(new long[0]);
        if (volume == 0 || palette.isEmpty() || etats.length == 0) return 0;

        int[] couleurs = new int[palette.size()];
        for (int i = 0; i < palette.size(); i++) couleurs[i] = couleur(palette.getCompoundOrEmpty(i).getStringOr("Name", ""));
        Apercu dessin = null;
        if (volume > plusGrand) {
            dessin = new Apercu(sx, sz);
            apercu[0] = dessin;
        }

        // Nombre de blocs par entrée de la palette (les valeurs peuvent chevaucher deux longs)
        int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
        long masque = (1L << bits) - 1;
        long[] compte = new long[palette.size()];
        long couche = (long) sx * sz;
        for (long i = 0; i < volume; i++) {
            long debut = i * bits;
            int a = (int) (debut >> 6);
            int b = (int) (((i + 1) * bits - 1) >> 6);
            int decalage = (int) (debut & 63);
            if (b >= etats.length) break;
            long valeur = a == b
                    ? (etats[a] >>> decalage) & masque
                    : ((etats[a] >>> decalage) | (etats[b] << (64 - decalage))) & masque;
            if (valeur >= compte.length) continue;
            compte[(int) valeur]++;
            if (dessin != null) {
                dessin.bloc((int) (i % sx), (int) (i / couche), (int) ((i / sx) % sz), couleurs[(int) valeur]);
            }
        }

        for (int i = 0; i < palette.size(); i++) {
            if (compte[i] == 0) continue;
            CompoundTag entree = palette.getCompoundOrEmpty(i);
            CompoundTag props = entree.getCompoundOrEmpty("Properties");
            Item item = itemPour(entree.getStringOr("Name", ""), props);
            if (item == null) continue;
            long n = compte[i] * multiplicateur(props);
            total.merge(item, (int) Math.min(Integer.MAX_VALUE, n), Integer::sum);
        }
        return volume;
    }

    /**
     * Format .schem de WorldEdit (Sponge v2 et v3) : une palette « minecraft:oak_stairs[facing=east] » -> index,
     * et les blocs encodés en varints (ordre x, puis z, puis y).
     */
    private static void lireSponge(CompoundTag racine, Map<Item, Integer> total, Apercu[] apercu) {
        CompoundTag schema = racine.contains("Schematic") ? racine.getCompoundOrEmpty("Schematic") : racine;
        CompoundTag palette;
        byte[] donnees;
        if (schema.contains("Blocks")) { // v3
            CompoundTag blocs = schema.getCompoundOrEmpty("Blocks");
            palette = blocs.getCompoundOrEmpty("Palette");
            donnees = blocs.getByteArray("Data").orElse(new byte[0]);
        } else { // v2
            palette = schema.getCompoundOrEmpty("Palette");
            donnees = schema.getByteArray("BlockData").orElse(new byte[0]);
        }
        if (palette.isEmpty() || donnees.length == 0) return;
        int largeur = schema.getShortOr("Width", (short) 0) & 0xFFFF;
        int longueur = schema.getShortOr("Length", (short) 0) & 0xFFFF;

        Map<Integer, String> parIndex = new HashMap<>();
        for (String etat : palette.keySet()) parIndex.put(palette.getIntOr(etat, -1), etat);
        Map<Integer, Integer> couleurs = new HashMap<>();
        for (Map.Entry<Integer, String> e : parIndex.entrySet()) {
            String nom = e.getValue();
            int crochet = nom.indexOf('[');
            couleurs.put(e.getKey(), couleur(crochet >= 0 ? nom.substring(0, crochet) : nom));
        }
        Apercu dessin = largeur > 0 && longueur > 0 ? new Apercu(largeur, longueur) : null;
        apercu[0] = dessin;

        Map<Integer, Long> compte = new HashMap<>();
        int i = 0;
        long position = 0;
        while (i < donnees.length) {
            int valeur = 0, decalage = 0;
            byte b;
            do {
                b = donnees[i++];
                valeur |= (b & 0x7F) << decalage;
                decalage += 7;
            } while ((b & 0x80) != 0 && i < donnees.length && decalage < 35);
            compte.merge(valeur, 1L, Long::sum);
            if (dessin != null) {
                long couche = (long) largeur * longueur;
                dessin.bloc((int) (position % largeur), (int) (position / couche), (int) ((position / largeur) % longueur),
                        couleurs.getOrDefault(valeur, 0));
            }
            position++;
        }

        for (Map.Entry<Integer, Long> e : compte.entrySet()) {
            String etat = parIndex.get(e.getKey());
            if (etat == null) continue;
            // "minecraft:oak_slab[type=double,waterlogged=false]" -> nom + propriétés
            String nom = etat;
            CompoundTag props = new CompoundTag();
            int crochet = etat.indexOf('[');
            if (crochet >= 0 && etat.endsWith("]")) {
                nom = etat.substring(0, crochet);
                for (String paire : etat.substring(crochet + 1, etat.length() - 1).split(",")) {
                    int egal = paire.indexOf('=');
                    if (egal > 0) props.putString(paire.substring(0, egal).trim(), paire.substring(egal + 1).trim());
                }
            }
            Item item = itemPour(nom, props);
            if (item == null) continue;
            long n = e.getValue() * multiplicateur(props);
            total.merge(item, (int) Math.min(Integer.MAX_VALUE, n), Integer::sum);
        }
    }

    /** Format .nbt (bloc de structure, Create) : « palette » de blocs et liste « blocks » avec « state » et « pos ». */
    private static void lireStructure(CompoundTag racine, Map<Item, Integer> total, Apercu[] apercu) {
        ListTag palette = racine.getListOrEmpty("palette");
        if (palette.isEmpty()) {
            ListTag palettes = racine.getListOrEmpty("palettes"); // variantes : on prend la première
            if (!palettes.isEmpty()) palette = palettes.getListOrEmpty(0);
        }
        ListTag blocs = racine.getListOrEmpty("blocks");
        if (palette.isEmpty() || blocs.isEmpty()) return;
        ListTag taille = racine.getListOrEmpty("size");
        Apercu dessin = taille.size() == 3 ? new Apercu(taille.getIntOr(0, 0), taille.getIntOr(2, 0)) : null;
        apercu[0] = dessin;
        int[] couleurs = new int[palette.size()];
        for (int i = 0; i < palette.size(); i++) couleurs[i] = couleur(palette.getCompoundOrEmpty(i).getStringOr("Name", ""));

        long[] compte = new long[palette.size()];
        for (int i = 0; i < blocs.size(); i++) {
            CompoundTag bloc = blocs.getCompoundOrEmpty(i);
            int etat = bloc.getIntOr("state", -1);
            if (etat < 0 || etat >= compte.length) continue;
            compte[etat]++;
            ListTag pos = bloc.getListOrEmpty("pos");
            if (dessin != null && pos.size() == 3) {
                dessin.bloc(pos.getIntOr(0, 0), pos.getIntOr(1, 0), pos.getIntOr(2, 0), couleurs[etat]);
            }
        }
        for (int i = 0; i < palette.size(); i++) {
            if (compte[i] == 0) continue;
            CompoundTag entree = palette.getCompoundOrEmpty(i);
            CompoundTag props = entree.getCompoundOrEmpty("Properties");
            Item item = itemPour(entree.getStringOr("Name", ""), props);
            if (item == null) continue;
            total.merge(item, (int) Math.min(Integer.MAX_VALUE, compte[i] * multiplicateur(props)), Integer::sum);
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
