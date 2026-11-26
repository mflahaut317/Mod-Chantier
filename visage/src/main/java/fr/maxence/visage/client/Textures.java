package fr.maxence.visage.client;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** Une texture par joueur dont la caméra est allumée. Mise à jour sur le fil principal du jeu. */
final class Textures {
    private Textures() {}

    /** Au-delà, la caméra est considérée éteinte (plus d'image reçue). */
    private static final long EXPIRATION = 3000;

    private static final class Etat {
        final Identifier id;
        final DynamicTexture texture;
        long derniere;
        Etat(Identifier id, DynamicTexture texture) { this.id = id; this.texture = texture; }
    }

    private static final Map<Integer, Etat> ETATS = new HashMap<>();

    /** Texture à afficher pour ce joueur, ou null si sa caméra est éteinte. */
    static Identifier active(int entite) {
        Etat e = ETATS.get(entite);
        return e != null && System.currentTimeMillis() - e.derniere < EXPIRATION ? e.id : null;
    }

    static void mettreAJour(int entite, int[] pixels) {
        Etat e = ETATS.get(entite);
        if (e == null) {
            Identifier id = Identifier.fromNamespaceAndPath(fr.maxence.visage.Visage.MODID, "camera/" + Integer.toUnsignedString(entite));
            DynamicTexture t = new DynamicTexture(() -> "visage " + entite, Camera.TAILLE, Camera.TAILLE, true);
            Minecraft.getInstance().getTextureManager().register(id, t);
            e = new Etat(id, t);
            ETATS.put(entite, e);
        }
        var image = e.texture.getPixels();
        if (image == null) return;
        for (int y = 0; y < Camera.TAILLE; y++)
            for (int x = 0; x < Camera.TAILLE; x++) image.setPixel(x, y, pixels[x + y * Camera.TAILLE]);
        e.texture.upload();
        e.derniere = System.currentTimeMillis();
    }

    static void eteindre(int entite) {
        Etat e = ETATS.get(entite);
        if (e != null) e.derniere = 0;
    }

    /** Décode une image JPEG reçue en pixels ARGB 96×96 (null si illisible). */
    static int[] decoder(byte[] jpeg) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(jpeg));
            if (img == null || img.getWidth() != Camera.TAILLE || img.getHeight() != Camera.TAILLE) return null;
            int[] pixels = new int[Camera.TAILLE * Camera.TAILLE];
            img.getRGB(0, 0, Camera.TAILLE, Camera.TAILLE, pixels, 0, Camera.TAILLE);
            for (int i = 0; i < pixels.length; i++) pixels[i] |= 0xFF000000;
            return pixels;
        } catch (Exception e) {
            return null;
        }
    }

    /** Libère les textures des joueurs dont on n'a plus rien reçu depuis 1 minute. */
    static void nettoyer() {
        long maintenant = System.currentTimeMillis();
        Iterator<Map.Entry<Integer, Etat>> it = ETATS.entrySet().iterator();
        while (it.hasNext()) {
            Etat e = it.next().getValue();
            if (maintenant - e.derniere > 60_000) {
                Minecraft.getInstance().getTextureManager().release(e.id);
                it.remove();
            }
        }
    }

    static void toutLiberer() {
        for (Etat e : ETATS.values()) Minecraft.getInstance().getTextureManager().release(e.id);
        ETATS.clear();
    }
}
