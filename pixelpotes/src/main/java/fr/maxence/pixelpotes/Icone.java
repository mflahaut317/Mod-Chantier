package fr.maxence.pixelpotes;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

/** Remplace l'icône de la fenêtre du jeu (barre des tâches, Alt+Tab) par le logo Pixel Potes. */
final class Icone {
    private Icone() {}

    private static final int[] TAILLES = {16, 32, 48, 64, 128, 256};

    /** À appeler sur le fil de rendu (celui qui possède la fenêtre). */
    static void appliquer() {
        long fenetre = GLFW.glfwGetCurrentContext();
        if (fenetre == 0L) return;
        List<ByteBuffer> pixels = new ArrayList<>();
        try (MemoryStack pile = MemoryStack.stackPush()) {
            GLFWImage.Buffer images = GLFWImage.malloc(TAILLES.length, pile);
            int n = 0;
            for (int taille : TAILLES) {
                ByteBuffer image = charger("/assets/pixelpotes/icones/icone_" + taille + ".png", pile);
                if (image == null) continue;
                pixels.add(image);
                images.position(n++).width(taille).height(taille).pixels(image);
            }
            if (n == 0) return;
            images.position(0).limit(n);
            GLFW.glfwSetWindowIcon(fenetre, images);
        } catch (Throwable e) {
            // macOS refuse les icônes de fenêtre ; ailleurs, un échec laisse simplement l'icône Minecraft
        } finally {
            for (ByteBuffer p : pixels) STBImage.stbi_image_free(p);
        }
    }

    private static ByteBuffer charger(String chemin, MemoryStack pile) {
        try (InputStream in = Icone.class.getResourceAsStream(chemin)) {
            if (in == null) return null;
            byte[] octets = in.readAllBytes();
            ByteBuffer fichier = MemoryUtil.memAlloc(octets.length);
            try {
                fichier.put(octets).flip();
                IntBuffer l = pile.mallocInt(1), h = pile.mallocInt(1), c = pile.mallocInt(1);
                return STBImage.stbi_load_from_memory(fichier, l, h, c, 4);
            } finally {
                MemoryUtil.memFree(fichier);
            }
        } catch (Exception e) {
            return null;
        }
    }
}
