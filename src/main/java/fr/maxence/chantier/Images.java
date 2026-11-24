package fr.maxence.chantier;

import java.io.ByteArrayOutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/** Compression des aperçus (RGB, 3 octets par pixel) pour les envoyer et les stocker sans gaspiller de place. */
public final class Images {
    private Images() {}

    /** Taille max d'un aperçu, en pixels de côté. */
    public static final int MAX = 128;

    public static byte[] compresser(byte[] rgb) {
        Deflater d = new Deflater(Deflater.BEST_COMPRESSION);
        d.setInput(rgb);
        d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(rgb.length / 2 + 64);
        byte[] tampon = new byte[8192];
        while (!d.finished()) out.write(tampon, 0, d.deflate(tampon));
        d.end();
        return out.toByteArray();
    }

    /** @return les octets RGB, ou null si les données sont invalides ou ne font pas exactement attendu octets */
    public static byte[] decompresser(byte[] donnees, int attendu) {
        if (attendu <= 0 || attendu > MAX * MAX * 3) return null;
        Inflater i = new Inflater();
        i.setInput(donnees);
        byte[] rgb = new byte[attendu];
        try {
            int lus = 0;
            while (lus < attendu && !i.finished()) {
                int n = i.inflate(rgb, lus, attendu - lus);
                if (n == 0 && (i.needsInput() || i.needsDictionary())) break;
                lus += n;
            }
            return lus == attendu ? rgb : null;
        } catch (DataFormatException e) {
            return null;
        } finally {
            i.end();
        }
    }
}
