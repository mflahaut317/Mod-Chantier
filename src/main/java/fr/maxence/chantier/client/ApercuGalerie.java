package fr.maxence.chantier.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Aperçus 3D de la galerie transformés en textures : la scène est calculée une seule fois,
 * puis chaque image affichée coûte autant qu'une icône. Libérées quand on ferme la galerie.
 */
final class ApercuGalerie {
    private record Texture(Identifier id, int largeur, int hauteur) {}

    private final Map<String, Texture> textures = new HashMap<>();
    private final List<Identifier> creees = new ArrayList<>();
    private int numero;

    /** Dessine la scène (clé = projet + rotation + taille) centrée dans la boîte. @return false si rien à dessiner */
    boolean dessiner(GuiGraphicsExtractor g, String cle, Scene scene, int x, int y, int l, int h) {
        if (scene == null) return false;
        int taille = Math.max(l, h) * 2; // deux fois plus de pixels que la place à l'écran : image nette
        Texture t = textures.computeIfAbsent(cle + "@" + taille, k -> creer(scene, taille));
        if (t == null) return false;
        float echelle = Math.min((l - 4) / (float) t.largeur(), (h - 4) / (float) t.hauteur());
        int dw = Math.max(1, Math.round(t.largeur() * echelle)), dh = Math.max(1, Math.round(t.hauteur() * echelle));
        g.blit(RenderPipelines.GUI_TEXTURED, t.id(), x + (l - dw) / 2, y + (h - dh) / 2, 0f, 0f, dw, dh,
                t.largeur(), t.hauteur(), t.largeur(), t.hauteur());
        return true;
    }

    private Texture creer(Scene scene, int taille) {
        Scene.Image image = scene.rendre(taille);
        if (image == null) return null;
        NativeImage ni = new NativeImage(image.largeur(), image.hauteur(), true);
        for (int y = 0; y < image.hauteur(); y++)
            for (int x = 0; x < image.largeur(); x++) ni.setPixel(x, y, image.pixels()[x + y * image.largeur()]);
        Identifier id = Identifier.fromNamespaceAndPath(fr.maxence.chantier.Chantier.MODID, "apercu/" + (numero++));
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "chantier apercu", ni));
        creees.add(id);
        return new Texture(id, image.largeur(), image.hauteur());
    }

    /** Libère toutes les textures créées. */
    void liberer() {
        for (Identifier id : creees) Minecraft.getInstance().getTextureManager().release(id);
        creees.clear();
        textures.clear();
    }
}
