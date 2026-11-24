package fr.maxence.chantier.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

/**
 * Version compacte des blocs d'un schéma pour l'aperçu 3D de la galerie :
 * une grille d'au plus COTE_MAX de côté (réduite si le schéma est plus grand), une palette de blocs,
 * un octet par case (0 = vide), le tout compressé.
 */
final class Volume {
    private Volume() {}

    private static final int TAILLE_MAX = 20_000;      // octets compressés (le paquet doit rester petit)
    private static final int[] COTES = {48, 32, 24, 16};

    record Grille(int sx, int sy, int sz, Block[] palette, byte[] cases) {
        int bloc(int x, int y, int z) {
            if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) return 0;
            return cases[x + z * sx + y * sx * sz] & 255;
        }
    }

    /** Encode le plan ; essaie des grilles de plus en plus petites jusqu'à tenir dans TAILLE_MAX. */
    static byte[] encoder(LecteurLitematic.Plan plan) {
        if (plan == null || plan.n == 0) return new byte[0];
        for (int cote : COTES) {
            byte[] donnees = encoder(plan, cote);
            if (donnees.length <= TAILLE_MAX) return donnees;
        }
        return new byte[0];
    }

    /** Tous les blocs, sans réduction (pour le hologramme partagé avec le groupe). */
    static byte[] encoderComplet(LecteurLitematic.Plan plan) {
        if (plan == null || plan.n == 0) return new byte[0];
        return encoder(plan, Math.max(plan.sx, Math.max(plan.sy, plan.sz)));
    }

    /** Remet une grille sous forme de plan (positions + blocs) pour l'hologramme. */
    static LecteurLitematic.Plan versPlan(Grille g) {
        LecteurLitematic.Plan p = new LecteurLitematic.Plan();
        for (int y = 0; y < g.sy(); y++) for (int z = 0; z < g.sz(); z++) for (int x = 0; x < g.sx(); x++) {
            int v = g.bloc(x, y, z);
            if (v != 0 && g.palette()[v] != null) p.ajouter(x, y, z, g.palette()[v]);
        }
        p.sx = g.sx(); p.sy = g.sy(); p.sz = g.sz();
        return p;
    }

    private static byte[] encoder(LecteurLitematic.Plan plan, int coteMax) {
        int grand = Math.max(plan.sx, Math.max(plan.sy, plan.sz));
        int pas = Math.max(1, (grand + coteMax - 1) / coteMax);
        int sx = (plan.sx + pas - 1) / pas, sy = (plan.sy + pas - 1) / pas, sz = (plan.sz + pas - 1) / pas;
        byte[] cases = new byte[sx * sy * sz];
        Map<Block, Integer> index = new HashMap<>();
        List<Block> palette = new ArrayList<>();
        for (int i = 0; i < plan.n; i++) {
            int x = plan.pos[i * 3] / pas, y = plan.pos[i * 3 + 1] / pas, z = plan.pos[i * 3 + 2] / pas;
            int c = x + z * sx + y * sx * sz;
            if (cases[c] != 0) continue; // une case réduite garde le premier bloc trouvé
            Integer n = index.get(plan.blocs[i]);
            if (n == null) {
                if (palette.size() >= 255) continue;
                palette.add(plan.blocs[i]);
                n = palette.size();
                index.put(plan.blocs[i], n);
            }
            cases[c] = (byte) (int) n;
        }
        try {
            ByteArrayOutputStream brut = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(brut);
            out.writeShort(sx); out.writeShort(sy); out.writeShort(sz);
            out.writeShort(palette.size());
            for (Block b : palette) out.writeUTF(BuiltInRegistries.BLOCK.getKey(b).toString());
            out.write(cases);
            out.flush();
            return compresser(brut.toByteArray());
        } catch (IOException e) {
            return new byte[0];
        }
    }

    /** null si les données sont vides ou illisibles. */
    static Grille decoder(byte[] donnees) {
        if (donnees == null || donnees.length == 0) return null;
        try {
            byte[] brut = decompresser(donnees);
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(brut));
            int sx = in.readUnsignedShort(), sy = in.readUnsignedShort(), sz = in.readUnsignedShort();
            int n = in.readUnsignedShort();
            if ((long) sx * sy * sz > 30_000_000L || n > 255) return null;
            Block[] palette = new Block[n + 1];
            for (int i = 1; i <= n; i++) {
                Identifier id = Identifier.tryParse(in.readUTF());
                palette[i] = id != null && BuiltInRegistries.BLOCK.containsKey(id) ? BuiltInRegistries.BLOCK.getValue(id) : null;
            }
            byte[] cases = new byte[sx * sy * sz];
            in.readFully(cases);
            return new Grille(sx, sy, sz, palette, cases);
        } catch (IOException | DataFormatException | RuntimeException e) {
            return null;
        }
    }

    private static byte[] compresser(byte[] brut) {
        Deflater d = new Deflater(Deflater.BEST_COMPRESSION);
        d.setInput(brut);
        d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] tampon = new byte[8192];
        while (!d.finished()) out.write(tampon, 0, d.deflate(tampon));
        d.end();
        return out.toByteArray();
    }

    private static byte[] decompresser(byte[] donnees) throws DataFormatException {
        Inflater i = new Inflater();
        i.setInput(donnees);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] tampon = new byte[8192];
        while (!i.finished()) {
            int n = i.inflate(tampon);
            if (n == 0 && (i.needsInput() || i.needsDictionary())) break;
            out.write(tampon, 0, n);
            if (out.size() > 32_000_000) break;
        }
        i.end();
        return out.toByteArray();
    }
}
