package fr.maxence.chantier.client;

import fr.maxence.chantier.EtatPayload;
import fr.maxence.chantier.GaleriePayload;
import java.net.URI;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
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
                s.filter(LecteurLitematic::estSchema)
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
                    .bounds(gauche, y, LARGEUR - 70, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Partager"), b -> { message = partager(f); })
                    .bounds(gauche + LARGEUR - 66, y, 66, 20).build());
            y += HAUTEUR_LIGNE;
        }
        addRenderableWidget(Button.builder(Component.literal("Galerie commune"),
                        b -> minecraft.gui.setScreen(new GalerieScreen(this)))
                .bounds(width / 2 - 155, height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Retour"), b -> onClose())
                .bounds(width / 2 - 50, height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Trouver des schémas"),
                        ConfirmLinkScreen.confirmLink(this, URI.create("https://www.minecraft-schematics.com/")))
                .bounds(width / 2 + 55, height - 28, 110, 20).build());
    }

    private void importer(Path fichier) {
        String erreur = envoyer(fichier);
        if (erreur == null) onClose(); else message = erreur;
    }

    /** Lit le schéma et envoie sa liste au serveur. @return null si tout va bien, sinon le message d'erreur */
    static String envoyer(Path fichier) {
        try {
            Map<Item, Integer> blocs = LecteurLitematic.lire(fichier).blocs();
            if (blocs.isEmpty()) return "Ce schéma ne contient aucun bloc.";
            List<EtatPayload.Ligne> lignes = new ArrayList<>();
            for (Map.Entry<Item, Integer> e : blocs.entrySet()) {
                lignes.add(new EtatPayload.Ligne(BuiltInRegistries.ITEM.getKey(e.getKey()).toString(), e.getValue(), 0));
            }
            String nom = fichier.getFileName().toString().replaceAll("(?i)\\.(litematic|schem|nbt)$", "");
            ClientPacketDistributor.sendToServer(new ImportPayload(nom, lignes));
            return null;
        } catch (IOException | RuntimeException e) {
            return "Lecture impossible : " + e.getMessage();
        }
    }

    /** Partage le schéma dans la galerie commune du serveur (liste + vue du dessus). */
    static String partager(Path fichier) {
        try {
            LecteurLitematic.Lecture lecture = LecteurLitematic.lire(fichier);
            if (lecture.blocs().isEmpty()) return "Ce schéma ne contient aucun bloc.";
            List<EtatPayload.Ligne> lignes = new ArrayList<>();
            for (Map.Entry<Item, Integer> e : lecture.blocs().entrySet()) {
                lignes.add(new EtatPayload.Ligne(BuiltInRegistries.ITEM.getKey(e.getKey()).toString(), e.getValue(), 0));
            }
            byte[] rgb = new byte[lecture.pixels().length * 3];
            for (int i = 0; i < lecture.pixels().length; i++) {
                int c = lecture.pixels()[i];
                rgb[i * 3] = (byte) (c >> 16);
                rgb[i * 3 + 1] = (byte) (c >> 8);
                rgb[i * 3 + 2] = (byte) c;
            }
            String nom = fichier.getFileName().toString().replaceAll("(?i)\\.(litematic|schem|nbt)$", "");
            ClientPacketDistributor.sendToServer(new fr.maxence.chantier.PartagePayload(new GaleriePayload.Entree(
                    "", nom, "", lecture.largeur(), lecture.hauteur(), rgb, lignes)));
            return "Envoi de « " + nom + " » dans la galerie commune…";
        } catch (IOException | RuntimeException e) {
            return "Lecture impossible : " + e.getMessage();
        }
    }

    /**
     * Fichiers glissés dans la fenêtre : le premier .litematic est copié dans « schematics » (pour la galerie)
     * puis importé. @return message pour le joueur
     */
    static String deposer(List<Path> fichiers) {
        for (Path f : fichiers) {
            if (!LecteurLitematic.estSchema(f)) continue;
            try {
                Path dossier = FMLPaths.GAMEDIR.get().resolve("schematics");
                Files.createDirectories(dossier);
                Path copie = dossier.resolve(f.getFileName().toString());
                if (!Files.exists(copie)) Files.copy(f, copie);
            } catch (IOException | RuntimeException ignored) {
                // Copie impossible : on importe quand même depuis l'original.
            }
            String erreur = envoyer(f);
            return erreur == null ? "Schéma « " + f.getFileName() + " » envoyé au chantier." : erreur;
        }
        return "Glisse un fichier .litematic, .schem ou .nbt.";
    }

    @Override
    public void onFilesDrop(List<Path> fichiers) {
        message = deposer(fichiers);
        rebuildWidgets();
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
        String aide = "Clique sur un schéma, ou glisse un .litematic / .schem / .nbt dans la fenêtre";
        g.text(font, font.plainSubstrByWidth(aide, width - 20), width / 2 - Math.min(font.width(aide), width - 20) / 2, 22, GRIS);
        if (fichiers.isEmpty()) {
            String vide = "Aucun schéma (.litematic, .schem, .nbt) trouvé.";
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
