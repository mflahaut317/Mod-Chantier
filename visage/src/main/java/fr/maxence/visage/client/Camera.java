package fr.maxence.visage.client;

import com.github.sarxos.webcam.Webcam;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * Lit la webcam dans un fil à part : 25 images/s, recadrées en carré et réduites à 96×96.
 * Chaque image est gardée en pixels (pour notre propre tête) et en JPEG (pour l'envoi).
 */
final class Camera {
    private Camera() {}

    static final int TAILLE = 96;
    private static final long INTERVALLE = 40; // ms (25 images/s)

    /** Une image prête : pixels ARGB (TAILLE×TAILLE) et JPEG. */
    record Image(int[] pixels, byte[] jpeg, long numero) { }

    private static volatile boolean voulue = false;
    private static volatile Image derniere = null;
    private static volatile String etat = "";
    private static Thread fil;
    private static long compteur = 0;

    static boolean allumee() { return voulue; }
    static Image derniere() { return derniere; }
    static String etat() { return etat; }
    static void effacerEtat() { etat = ""; }

    static synchronized void basculer() {
        if (voulue) {
            voulue = false;
            derniere = null;
            return;
        }
        voulue = true;
        etat = "Recherche de la caméra…";
        if (fil == null || !fil.isAlive()) {
            fil = new Thread(Camera::boucle, "Visage webcam");
            fil.setDaemon(true);
            fil.start();
        }
    }

    /** Noms des caméras trouvées (rempli en arrière-plan à l'ouverture des réglages). */
    private static volatile java.util.List<String> noms = java.util.List.of();
    private static volatile boolean redemarrer = false;

    static java.util.List<String> noms() { return noms; }

    /** Cherche les caméras branchées, sans bloquer le jeu. */
    static void lister() {
        Thread t = new Thread(() -> {
            try {
                java.util.List<String> liste = new java.util.ArrayList<>();
                for (Webcam w : Webcam.getWebcams(4000)) liste.add(w.getName());
                noms = liste;
            } catch (Throwable e) {
                VisageClient.LOGGER.warn("[Visage] Liste des caméras impossible", e);
            }
        }, "Visage liste");
        t.setDaemon(true);
        t.start();
    }

    /** Nouvelle caméra choisie : on la rouvre si la caméra est allumée. */
    static void changerDeCamera() {
        redemarrer = true;
    }

    private static Webcam choisir() throws Exception {
        String voulu = Reglages.camera;
        if (!voulu.isEmpty()) {
            for (Webcam w : Webcam.getWebcams(4000)) if (w.getName().equals(voulu)) return w;
        }
        return Webcam.getDefault(4000);
    }

    private static void boucle() {
        while (voulue) {
            redemarrer = false;
            Webcam webcam = null;
            try {
                webcam = choisir();
                if (webcam == null) {
                    etat = "Aucune caméra trouvée";
                    voulue = false;
                    return;
                }
                Dimension meilleure = null;
                for (Dimension d : webcam.getViewSizes()) {
                    // la plus petite taille d'au moins 240 px de haut, pour aller vite
                    if (d.height >= 240 && (meilleure == null || d.height < meilleure.height)) meilleure = d;
                }
                if (meilleure != null) webcam.setViewSize(meilleure);
                webcam.open();
                etat = "";
                ImageWriter jpeg = ecrivainJpeg();
                while (voulue && !redemarrer) {
                    long debut = System.currentTimeMillis();
                    BufferedImage brute = webcam.getImage();
                    if (brute != null) {
                        BufferedImage carre = recadrer(brute);
                        int[] pixels = new int[TAILLE * TAILLE];
                        carre.getRGB(0, 0, TAILLE, TAILLE, pixels, 0, TAILLE);
                        for (int i = 0; i < pixels.length; i++) pixels[i] |= 0xFF000000;
                        byte[] donnees = encoder(jpeg, carre);
                        derniere = new Image(pixels, donnees, ++compteur);
                        envoyer(donnees);
                    }
                    long reste = INTERVALLE - (System.currentTimeMillis() - debut);
                    if (reste > 0) Thread.sleep(reste);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable e) {
                // Pilote absent, caméra occupée par une autre appli, bibliothèque native refusée…
                etat = "Caméra indisponible : " + e.getClass().getSimpleName();
                voulue = false;
                VisageClient.LOGGER.error("[Visage] Erreur caméra", e);
            } finally {
                if (webcam != null) {
                    try { webcam.close(); } catch (Throwable ignored) { }
                }
                if (!voulue) derniere = null;
            }
        }
    }

    /** Envoi direct depuis ce fil (la connexion du jeu accepte les envois depuis n'importe quel fil). */
    private static void envoyer(byte[] donnees) {
        if (donnees.length == 0 || donnees.length > fr.maxence.visage.Visage.TAILLE_MAX || !voulue) return;
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.getConnection() == null || mc.player == null) return;
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new fr.maxence.visage.ImageEnvoi(donnees));
    }

    /** Garde le carré central (plus ou moins rapproché), réduit à TAILLE×TAILLE, puis tourné et retourné selon les réglages. */
    private static BufferedImage recadrer(BufferedImage source) {
        int cote = Math.round(Math.min(source.getWidth(), source.getHeight()) / Math.max(1f, Reglages.zoom));
        int x = (source.getWidth() - cote) / 2, y = (source.getHeight() - cote) / 2;
        BufferedImage sortie = new BufferedImage(TAILLE, TAILLE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sortie.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        java.awt.geom.AffineTransform t = new java.awt.geom.AffineTransform();
        t.translate(TAILLE / 2.0, TAILLE / 2.0);
        t.quadrantRotate(Reglages.rotation);
        if (Reglages.miroir) t.scale(-1, 1);
        t.translate(-TAILLE / 2.0, -TAILLE / 2.0);
        g.setTransform(t);
        g.drawImage(source, 0, 0, TAILLE, TAILLE, x, y, x + cote, y + cote, null);
        g.dispose();
        return sortie;
    }

    private static ImageWriter ecrivainJpeg() {
        Iterator<ImageWriter> it = ImageIO.getImageWritersByFormatName("jpeg");
        return it.hasNext() ? it.next() : null;
    }

    private static byte[] encoder(ImageWriter ecrivain, BufferedImage image) throws IOException {
        if (ecrivain == null) return new byte[0];
        ByteArrayOutputStream sortie = new ByteArrayOutputStream(6000);
        try (ImageOutputStream flux = ImageIO.createImageOutputStream(sortie)) {
            ecrivain.setOutput(flux);
            ImageWriteParam reglages = ecrivain.getDefaultWriteParam();
            reglages.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            reglages.setCompressionQuality(0.7f);
            ecrivain.write(null, new IIOImage(image, null, null), reglages);
        }
        return sortie.toByteArray();
    }
}
