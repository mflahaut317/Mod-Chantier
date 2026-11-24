package fr.maxence.chantier;

import java.io.ByteArrayOutputStream;

/** Hologramme partagé reçu du serveur (côté joueur) : simple stockage, lu par le code client. */
public final class HoloRecu {
    private HoloRecu() {}

    /** Augmente à chaque nouvelle (position, données, retrait). */
    public static volatile int changement;
    public static volatile boolean retire;
    public static volatile int version = -1;
    public static volatile String nom = "";
    public static volatile int x, y, z, rotation;
    public static volatile String dimension = "";
    /** Blocs compressés complets de la version courante (null tant qu'ils ne sont pas tous arrivés). */
    public static volatile byte[] donnees;

    private static ByteArrayOutputStream tampon;
    private static int versionTampon = -1;

    public static synchronized void recevoir(HoloPayload p) {
        if (p.total() < 0) {
            retire = true;
            donnees = null;
            version = -1;
            changement++;
            return;
        }
        retire = false;
        nom = p.nom();
        x = p.x(); y = p.y(); z = p.z();
        rotation = p.rotation();
        dimension = p.dimension();
        if (p.total() == 0) { changement++; return; }
        if (p.morceau() == 0 || versionTampon != p.version()) {
            tampon = new ByteArrayOutputStream();
            versionTampon = p.version();
        }
        tampon.writeBytes(p.donnees());
        if (p.morceau() == p.total() - 1) {
            donnees = tampon.toByteArray();
            version = p.version();
            tampon = null;
            changement++;
        }
    }

    public static synchronized void vider() {
        retire = false;
        donnees = null;
        version = -1;
        tampon = null;
        changement++;
    }
}
