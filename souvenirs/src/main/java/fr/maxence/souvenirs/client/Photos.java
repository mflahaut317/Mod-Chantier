package fr.maxence.souvenirs.client;

import com.mojang.blaze3d.platform.NativeImage;
import fr.maxence.souvenirs.Paquets;
import fr.maxence.souvenirs.Recu;
import fr.maxence.souvenirs.Souvenirs;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Captures d'écran des souvenirs : envoi (réduites en JPEG) et affichage (textures, gardées en mémoire). */
final class Photos {
    private Photos() {}

    record Texture(Identifier id, int largeur, int hauteur) {}

    private static final Map<String, Texture> TEXTURES = new HashMap<>();
    private static final Set<String> DEMANDEES = new HashSet<>();

    /** Texture de la capture du souvenir (null si pas encore arrivée : elle est demandée au serveur). */
    static Texture texture(String id) {
        Texture t = TEXTURES.get(id);
        if (t != null) return t;
        byte[] jpeg = Recu.PHOTOS.get(id);
        if (jpeg == null) {
            if (DEMANDEES.add(id)) ClientPacketDistributor.sendToServer(new Paquets.Demande("image", id));
            return null;
        }
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(jpeg));
            if (img == null) return null;
            NativeImage ni = new NativeImage(img.getWidth(), img.getHeight(), true);
            for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) ni.setPixel(x, y, 0xFF000000 | img.getRGB(x, y));
            Identifier tid = Identifier.fromNamespaceAndPath(Souvenirs.MODID, "photo/" + id.replaceAll("[^a-z0-9-]", ""));
            Minecraft.getInstance().getTextureManager().register(tid, new DynamicTexture(() -> "souvenir " + id, ni));
            t = new Texture(tid, img.getWidth(), img.getHeight());
            TEXTURES.put(id, t);
            return t;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Oublie la capture d'un souvenir (elle vient d'être remplacée). */
    static void oublier(String id) {
        Texture t = TEXTURES.remove(id);
        if (t != null) Minecraft.getInstance().getTextureManager().release(t.id());
        Recu.PHOTOS.remove(id);
        DEMANDEES.remove(id);
    }

    /** Le dossier des captures d'écran du jeu (F2). */
    static Path dossier() { return FMLPaths.GAMEDIR.get().resolve("screenshots"); }

    /** Captures d'écran, de la plus récente à la plus ancienne. */
    static List<Path> captures() {
        if (!Files.isDirectory(dossier())) return List.of();
        try (Stream<Path> s = Files.list(dossier())) {
            return s.filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg");
                    })
                    .sorted((a, b) -> Long.compare(date(b), date(a)))
                    .limit(80).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    static long date(Path p) {
        try { return Files.getLastModifiedTime(p).toMillis(); } catch (IOException e) { return 0; }
    }

    /** Réduit l'image (960 x 540 au plus), la convertit en JPEG et l'envoie pour le souvenir id. @return message */
    static String envoyer(Path fichier, String id) {
        try {
            BufferedImage source = ImageIO.read(fichier.toFile());
            if (source == null) return "Format d'image non reconnu.";
            double echelle = Math.min(1.0, Math.min(960.0 / source.getWidth(), 540.0 / source.getHeight()));
            int w = Math.max(1, (int) (source.getWidth() * echelle)), h = Math.max(1, (int) (source.getHeight() * echelle));
            BufferedImage reduite = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = reduite.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(source, 0, 0, w, h, null);
            g.dispose();
            ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
            if (!ImageIO.write(reduite, "jpg", jpeg)) return "Conversion en JPEG impossible.";
            byte[] tout = jpeg.toByteArray();
            int morceau = 30_000, total = (tout.length + morceau - 1) / morceau;
            for (int i = 0; i < total; i++) {
                ClientPacketDistributor.sendToServer(new Paquets.PhotoEnvoi(id, i, total,
                        Arrays.copyOfRange(tout, i * morceau, Math.min(tout.length, (i + 1) * morceau))));
            }
            oublier(id);
            return "Envoi de la capture…";
        } catch (IOException | RuntimeException e) {
            return "Image illisible : " + e.getMessage();
        }
    }
}
