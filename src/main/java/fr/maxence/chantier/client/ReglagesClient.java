package fr.maxence.chantier.client;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.neoforged.fml.loading.FMLPaths;

/** Petits réglages du joueur (config/chantier-client.properties). */
final class ReglagesClient {
    private ReglagesClient() {}

    private static Properties valeurs;

    private static Path fichier() { return FMLPaths.CONFIGDIR.get().resolve("chantier-client.properties"); }

    private static Properties valeurs() {
        if (valeurs != null) return valeurs;
        valeurs = new Properties();
        if (Files.exists(fichier())) {
            try (Reader r = Files.newBufferedReader(fichier(), StandardCharsets.UTF_8)) {
                valeurs.load(r);
            } catch (IOException | RuntimeException ignored) { }
        }
        return valeurs;
    }

    static boolean tutoVu() { return Boolean.parseBoolean(valeurs().getProperty("tutoVu", "false")); }

    static void marquerTutoVu() {
        valeurs().setProperty("tutoVu", "true");
        try (Writer w = Files.newBufferedWriter(fichier(), StandardCharsets.UTF_8)) {
            valeurs.store(w, "Réglages du mod Chantier");
        } catch (IOException ignored) { }
    }
}
