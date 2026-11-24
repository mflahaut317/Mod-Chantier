package fr.maxence.chantier.client;

import fr.maxence.chantier.EtatPayload;
import fr.maxence.chantier.ImportPayload;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Galerie des schémas .litematic du dossier « schematics » du jeu (celui de Litematica / BlockCompanion).
 * Un clic importe le schéma comme liste du chantier.
 */
public class SchemaScreen extends Screen {
    private static final int LARGEUR = 300;
    private static final int HAUTEUR_LIGNE = 22;
    private static final int GRIS = 0xFFAAAAAA;
    private static final int OR = 0xFFFFD24A;
    private static final int BLANC = 0xFFFFFFFF;

    private final Screen parent;
    private final List<Path> fichiers = new ArrayList<>();
    private Path dossier;
    private int defilement = 0;
    private String message = "";

    public SchemaScreen(Screen parent) {
        super(Component.literal("Importer un schéma"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        dossier = FMLPaths.GAMEDIR.get().resolve("schematics");
        fichiers.clear();
        if (Files.isDirectory(dossier)) {
            try (Stream<Path> s = Files.walk(dossier, 3)) {
                s.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".litematic"))
                        .sorted()
                        .forEach(fichiers::add);
            } catch (IOException e) {
                message = "Impossible de lire le dossier schematics.";
            }
        }
        construireBoutons();
    }

    private int lignesVisibles() {
        return Math.max(1, (height - 80) / HAUTEUR_LIGNE);
    }

    private void construireBoutons() {
        clearWidgets();
        int gauche = (width - LARGEUR) / 2;
        int y = 36;
        int fin = Math.min(fichiers.size(), defilement + lignesVisibles());
        for (int i = defilement; i < fin; i++) {
            Path f = fichiers.get(i);
            String nom = dossier.relativize(f).toString();
            addRenderableWidget(Button.builder(Component.literal(nom), b -> importer(f))
                    .bounds(gauche, y, LARGEUR, 20).build());
            y += HAUTEUR_LIGNE;
        }
        addRenderableWidget(Button.builder(Component.literal("Retour"), b -> onClose())
                .bounds(width / 2 - 50, height - 28, 100, 20).build());
    }

    private void importer(Path fichier) {
        try {
            Map<Item, Integer> blocs = LecteurLitematic.lire(fichier);
            if (blocs.isEmpty()) { message = "Ce schéma ne contient aucun bloc."; return; }
            List<EtatPayload.Ligne> lignes = new ArrayList<>();
            for (Map.Entry<Item, Integer> e : blocs.entrySet()) {
                lignes.add(new EtatPayload.Ligne(BuiltInRegistries.ITEM.getKey(e.getKey()).toString(), e.getValue(), 0));
            }
            String nom = fichier.getFileName().toString().replaceAll("(?i)\\.litematic$", "");
            ClientPacketDistributor.sendToServer(new ImportPayload(nom, lignes));
            onClose();
        } catch (IOException | RuntimeException e) {
            message = "Lecture impossible : " + e.getMessage();
        }
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        int max = Math.max(0, fichiers.size() - lignesVisibles());
        int avant = defilement;
        defilement = Math.max(0, Math.min(max, defilement - (int) Math.signum(vertical) * 2));
        if (defilement != avant) construireBoutons();
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        String titre = "Importer un schéma (" + fichiers.size() + ")";
        g.text(font, titre, width / 2 - font.width(titre) / 2, 10, OR);
        String aide = "Dossier : .minecraft/schematics — clique sur un schéma pour en faire la liste du chantier";
        g.text(font, font.plainSubstrByWidth(aide, width - 20), width / 2 - Math.min(font.width(aide), width - 20) / 2, 22, GRIS);
        if (fichiers.isEmpty()) {
            String vide = "Aucun fichier .litematic trouvé.";
            g.text(font, vide, width / 2 - font.width(vide) / 2, height / 2, GRIS);
        }
        if (!message.isEmpty()) {
            g.text(font, font.plainSubstrByWidth(message, width - 20), 10, height - 44, BLANC);
        }
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
