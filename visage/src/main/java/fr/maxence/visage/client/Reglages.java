package fr.maxence.visage.client;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.neoforged.fml.loading.FMLPaths;

/** Réglages de la caméra, gardés dans config/visage.properties. Appliqués avant l'envoi : tout le monde voit le même résultat. */
final class Reglages {
    private Reglages() {}

    /** Nom de la caméra choisie ("" = celle par défaut). */
    static volatile String camera = "";
    /** Quarts de tour dans le sens des aiguilles d'une montre (0 à 3). */
    static volatile int rotation = 0;
    static volatile boolean miroir = false;
    /** 1 = image entière, 1,5 ou 2 = rapproché sur le centre. */
    static volatile float zoom = 1f;

    private static Path fichier() {
        return FMLPaths.CONFIGDIR.get().resolve("visage.properties");
    }

    static void charger() {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(fichier(), StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (Exception e) {
            return;
        }
        camera = p.getProperty("camera", "");
        try { rotation = Math.floorMod(Integer.parseInt(p.getProperty("rotation", "0")), 4); } catch (NumberFormatException ignored) { }
        miroir = Boolean.parseBoolean(p.getProperty("miroir", "false"));
        try { zoom = Math.max(1f, Math.min(3f, Float.parseFloat(p.getProperty("zoom", "1")))); } catch (NumberFormatException ignored) { }
    }

    static void sauver() {
        Properties p = new Properties();
        p.setProperty("camera", camera);
        p.setProperty("rotation", Integer.toString(rotation));
        p.setProperty("miroir", Boolean.toString(miroir));
        p.setProperty("zoom", Float.toString(zoom));
        try (Writer w = Files.newBufferedWriter(fichier(), StandardCharsets.UTF_8)) {
            p.store(w, "Visage : réglages de la caméra");
        } catch (Exception ignored) { }
    }
}
