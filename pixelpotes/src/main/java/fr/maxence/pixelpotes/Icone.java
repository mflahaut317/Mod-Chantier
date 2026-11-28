package fr.maxence.pixelpotes;

import org.lwjgl.sdl.SDLPixels;
import org.lwjgl.sdl.SDLSurface;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDL_Surface;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

/** Remplace l'icône de la fenêtre du jeu (barre des tâches, Alt+Tab) par le logo Pixel Potes. */
public final class Icone {
    private Icone() {}

    private static final int[] TAILLES = {16, 32, 48, 64, 128, 256};

    /** À appeler sur le fil de rendu (celui qui possède la fenêtre). Depuis la 26.3, la fenêtre est gérée par SDL3. */
    public static void appliquer() {
        long fenetre = SDLVideo.SDL_GL_GetCurrentWindow();
        if (fenetre == 0L) return;
        List<ByteBuffer> pixels = new ArrayList<>();
        List<SDL_Surface> surfaces = new ArrayList<>();
        try (MemoryStack pile = MemoryStack.stackPush()) {
            for (int taille : TAILLES) {
                ByteBuffer image = charger("/assets/pixelpotes/icones/icone_" + taille + ".png", pile);
                if (image == null) continue;
                pixels.add(image);
                SDL_Surface s = SDLSurface.SDL_CreateSurfaceFrom(taille, taille, SDLPixels.SDL_PIXELFORMAT_RGBA32, image, taille * 4);
                if (s != null) surfaces.add(s);
            }
            if (surfaces.isEmpty()) return;
            // la plus grande en principal, les autres en variantes (barre des tâches, Alt+Tab, titre)
            SDL_Surface principale = surfaces.get(surfaces.size() - 1);
            for (SDL_Surface s : surfaces) if (s != principale) SDLSurface.SDL_AddSurfaceAlternateImage(principale, s);
            SDLVideo.SDL_SetWindowIcon(fenetre, principale);
        } catch (Throwable e) {
            // un échec laisse simplement l'icône Minecraft
        } finally {
            for (SDL_Surface s : surfaces) SDLSurface.SDL_DestroySurface(s);
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
