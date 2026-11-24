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
public class SchemaScreen extends CadreScreen {
    private static final int LARGEUR = 300;
    private static final int HAUTEUR_LIGNE = 22;

    private final List<Path> fichiers = new ArrayList<>();
    private Path dossier;
    private int defilement = 0;
    private String message = "";

    public SchemaScreen(Screen parent) {
        super("Mes schémas", parent);
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
        cadre(LARGEUR + 24, 1000);
        info = fichiers.size() + (fichiers.size() > 1 ? " fichiers" : " fichier");
        construireBoutons();
    }

    private int lignesVisibles() {
        return Math.max(1, (ph - 80) / HAUTEUR_LIGNE);
    }

    private void construireBoutons() {
        clearWidgets();
        int gauche = px + 12;
        int y = py + 24;
        int fin = Math.min(fichiers.size(), defilement + lignesVisibles());
        for (int i = defilement; i < fin; i++) {
            Path f = fichiers.get(i);
            String nom = dossier.relativize(f).toString();
            addRenderableWidget(Button.builder(Component.literal(nom), b -> importer(f))
                    .bounds(gauche, y, LARGEUR - 130, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Terrain"), b -> {
                String erreur = Hologramme.charger(f);
                if (erreur == null) minecraft.gui.setScreen(new HoloScreen(this)); else message = erreur;
            }).bounds(gauche + LARGEUR - 126, y, 56, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Partager"), b -> partager(f))
                    .bounds(gauche + LARGEUR - 66, y, 66, 20).build());
            y += HAUTEUR_LIGNE;
        }
        addRenderableWidget(Button.builder(Component.literal("Retour"), b -> onClose())
                .bounds(width / 2 - 105, py + ph - 28, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Trouver des schémas"),
                        ConfirmLinkScreen.confirmLink(this, URI.create("https://www.minecraft-schematics.com/")))
                .bounds(width / 2 + 5, py + ph - 28, 110, 20).build());
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
            Hologramme.dernierImporte = fichier;
            return null;
        } catch (IOException | RuntimeException e) {
            return "Lecture impossible : " + e.getMessage();
        }
    }

    /** Lit le schéma puis ouvre la fiche (nom + description) avant de le partager. */
    private void partager(Path fichier) {
        try {
            GaleriePayload.Entree e = preparer(fichier);
            if (e == null) { message = "Ce schéma ne contient aucun bloc."; return; }
            minecraft.gui.setScreen(new FicheScreen(this, "Partager dans la galerie", e.nom(), "", "Partager",
                    (nom, description) -> {
                        ClientPacketDistributor.sendToServer(new fr.maxence.chantier.PartagePayload(new GaleriePayload.Entree(
                                "", nom, "", description, e.largeur(), e.hauteur(), e.apercu(), e.lignes())));
                        message = "Envoi de « " + nom + " » dans la galerie commune…";
                    }));
        } catch (IOException | RuntimeException ex) {
            message = "Lecture impossible : " + ex.getMessage();
        }
    }

    /** Liste + vue 3D d'un schéma, prêtes à partager (null si vide). */
    static GaleriePayload.Entree preparer(Path fichier) throws IOException {
        {
            LecteurLitematic.Lecture lecture = LecteurLitematic.lire(fichier);
            if (lecture.blocs().isEmpty()) return null;
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
            return new GaleriePayload.Entree("", nom, "", "", lecture.largeur(), lecture.hauteur(),
                    fr.maxence.chantier.Images.compresser(rgb), lignes);
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
        int gauche = px + 12;
        String aide = "Importer : clic sur le nom · Terrain : hologramme · Partager : galerie";
        texte(g, font, font.plainSubstrByWidth(aide, LARGEUR), gauche, py + 10, DOUX);
        if (fichiers.isEmpty()) {
            texte(g, font, "Aucun schéma (.litematic, .schem, .nbt) trouvé.", gauche, py + 30, TEXTE);
            texte(g, font, "Glisse un fichier dans cette fenêtre pour l'ajouter.", gauche, py + 42, DOUX);
        }
        if (!message.isEmpty()) {
            texte(g, font, font.plainSubstrByWidth(message, LARGEUR), gauche, py + ph - 42, OR);
        }
    }
}
