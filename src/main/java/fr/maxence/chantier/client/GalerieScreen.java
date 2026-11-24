package fr.maxence.chantier.client;

import fr.maxence.chantier.ActionPayload;
import fr.maxence.chantier.EtatPayload;
import fr.maxence.chantier.GaleriePayload;
import fr.maxence.chantier.GalerieRecue;
import java.net.URI;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Galerie commune du serveur : à gauche la liste des projets partagés, à droite l'aperçu (vue du dessus)
 * et les principaux matériaux. « Utiliser » en fait la liste de ton chantier.
 */
public class GalerieScreen extends Screen {
    private static final int LISTE = 150;
    private static final int HAUTEUR_LIGNE = 22;
    private static final int GRIS = 0xFFAAAAAA;
    private static final int OR = 0xFFFFD24A;
    private static final int BLANC = 0xFFFFFFFF;

    private final Screen parent;
    private GaleriePayload derniere = null;
    private boolean demandee = false;
    private int defilement = 0;
    private int choisi = -1;
    private boolean confirmerSuppression = false;

    public GalerieScreen(Screen parent) {
        super(Component.literal("Importer un schéma"));
        this.parent = parent;
    }

    private static void envoyer(String action, String valeur) {
        ClientPacketDistributor.sendToServer(new ActionPayload(action, valeur));
    }

    private List<GaleriePayload.Entree> entrees() {
        GaleriePayload g = GalerieRecue.get();
        return g == null ? List.of() : g.entrees();
    }

    private int lignesVisibles() { return Math.max(1, (height - 80) / HAUTEUR_LIGNE); }

    private int gauche() { return Math.max(6, width / 2 - 200); }

    @Override
    protected void init() {
        if (!demandee) { envoyer("galerie", ""); demandee = true; }
        derniere = GalerieRecue.get();
        List<GaleriePayload.Entree> liste = entrees();
        if (choisi >= liste.size()) choisi = liste.isEmpty() ? -1 : 0;
        if (choisi < 0 && !liste.isEmpty()) choisi = 0;

        int x = gauche(), y = 36;
        int fin = Math.min(liste.size(), defilement + lignesVisibles());
        for (int i = defilement; i < fin; i++) {
            int index = i;
            String nom = font.plainSubstrByWidth(liste.get(i).nom(), LISTE - 8);
            Button b = Button.builder(Component.literal(nom), btn -> { choisi = index; confirmerSuppression = false; rebuildWidgets(); })
                    .bounds(x, y, LISTE, 20).build();
            b.active = i != choisi;
            addRenderableWidget(b);
            y += HAUTEUR_LIGNE;
        }

        if (choisi >= 0) {
            GaleriePayload.Entree e = liste.get(choisi);
            int dx = x + LISTE + 10;
            addRenderableWidget(Button.builder(Component.literal("Utiliser pour mon chantier"), b -> {
                envoyer("galerie_utiliser", e.id());
                onClose();
            }).bounds(dx, height - 56, 150, 20).build());
            addRenderableWidget(Button.builder(Component.literal(confirmerSuppression ? "Sûr ?" : "Retirer"), b -> {
                if (confirmerSuppression) {
                    envoyer("galerie_supprimer", e.id());
                    confirmerSuppression = false;
                    choisi = -1;
                } else {
                    confirmerSuppression = true;
                }
                rebuildWidgets();
            }).bounds(dx + 155, height - 56, 70, 20).build());
        }

        addRenderableWidget(Button.builder(Component.literal("Mes schémas"),
                        b -> minecraft.gui.setScreen(new SchemaScreen(this)))
                .bounds(width / 2 - 160, height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Retour"), b -> onClose())
                .bounds(width / 2 - 55, height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Trouver des schémas"),
                        ConfirmLinkScreen.confirmLink(this, URI.create("https://www.minecraft-schematics.com/")))
                .bounds(width / 2 + 50, height - 28, 110, 20).build());
    }

    @Override
    public void tick() {
        super.tick();
        if (GalerieRecue.get() != derniere) rebuildWidgets(); // la galerie vient d'arriver ou a changé
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        int max = Math.max(0, entrees().size() - lignesVisibles());
        int avant = defilement;
        defilement = Math.max(0, Math.min(max, defilement - (int) Math.signum(vertical) * 2));
        if (defilement != avant) rebuildWidgets();
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        List<GaleriePayload.Entree> liste = entrees();
        String titre = "Galerie commune (" + liste.size() + " projets)";
        g.text(font, titre, width / 2 - font.width(titre) / 2, 10, OR);
        String aide = !message.isEmpty() ? message
                : "Glisse un schéma pour l'importer, ou une image (.png/.jpg) pour illustrer le projet choisi";
        g.text(font, aide, width / 2 - font.width(aide) / 2, 22, GRIS);

        if (GalerieRecue.get() == null) {
            g.text(font, "Chargement…", gauche(), 40, GRIS);
            return;
        }
        if (liste.isEmpty()) {
            g.text(font, "Aucun projet partagé pour l'instant.", gauche(), 40, GRIS);
            return;
        }
        if (choisi < 0 || choisi >= liste.size()) return;

        GaleriePayload.Entree e = liste.get(choisi);
        int x = gauche() + LISTE + 10, y = 36;
        g.text(font, e.nom(), x, y, BLANC);
        g.text(font, "par " + e.auteur() + " · " + e.lignes().size() + " matériaux", x, y + 11, GRIS);

        // Aperçu : vue du dessus, agrandie pour tenir dans un carré
        int cote = Math.min(140, Math.max(48, height - 170));
        int ay = y + 26;
        byte[] p = image(e);
        if (p != null) {
            double echelle = cote / (double) Math.max(e.largeur(), e.hauteur());
            int iw = (int) Math.round(e.largeur() * echelle), ih = (int) Math.round(e.hauteur() * echelle);
            g.fill(x - 1, ay - 1, x + iw + 1, ay + ih + 1, 0xFF202020);
            for (int pz = 0; pz < e.hauteur(); pz++) {
                int y0 = ay + (int) (pz * echelle), y1 = ay + (int) ((pz + 1) * echelle);
                if (y1 == y0) continue;
                for (int px = 0; px < e.largeur(); px++) {
                    int i = (px + pz * e.largeur()) * 3;
                    int rgb = ((p[i] & 255) << 16) | ((p[i + 1] & 255) << 8) | (p[i + 2] & 255);
                    if (rgb == 0) continue;
                    int x0 = x + (int) (px * echelle), x1 = x + (int) ((px + 1) * echelle);
                    if (x1 > x0) g.fill(x0, y0, x1, y1, 0xFF000000 | rgb);
                }
            }
        } else {
            g.text(font, "(pas d'aperçu)", x, ay, GRIS);
        }

        // Principaux matériaux, à droite de l'aperçu
        int mx = x + cote + 12, my = ay;
        int max = Math.min(e.lignes().size(), Math.max(1, (height - 60 - my) / 18));
        for (int i = 0; i < max; i++) {
            EtatPayload.Ligne l = e.lignes().get(i);
            Identifier id = Identifier.tryParse(l.item());
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) continue;
            g.item(new ItemStack(BuiltInRegistries.ITEM.getValue(id)), mx, my);
            g.text(font, ChantierClient.quantite(l.requis()), mx + 19, my + 4, BLANC);
            my += 18;
        }
    }

    /** Aperçus décompressés, gardés en mémoire tant que l'écran est ouvert. */
    private final java.util.Map<String, byte[]> images = new java.util.HashMap<>();
    private String message = "";

    private byte[] image(GaleriePayload.Entree e) {
        if (e.largeur() <= 0 || e.hauteur() <= 0) return null;
        String cle = e.id() + ":" + e.largeur() + "x" + e.hauteur() + ":" + e.apercu().length;
        return images.computeIfAbsent(cle, k -> fr.maxence.chantier.Images.decompresser(e.apercu(), e.largeur() * e.hauteur() * 3));
    }

    /** Schéma glissé : importé dans le chantier. Image glissée : devient l'aperçu du projet choisi. */
    @Override
    public void onFilesDrop(List<java.nio.file.Path> fichiers) {
        for (java.nio.file.Path f : fichiers) {
            String n = f.getFileName().toString().toLowerCase();
            if (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")) {
                List<GaleriePayload.Entree> liste = entrees();
                if (choisi < 0 || choisi >= liste.size()) { message = "Choisis d'abord un projet à gauche."; return; }
                message = envoyerImage(liste.get(choisi).id(), f);
                return;
            }
        }
        message = SchemaScreen.deposer(fichiers);
    }

    /** Lit l'image, la réduit (max 112 x 72 pour rester léger) et l'envoie au serveur. */
    private static String envoyerImage(String id, java.nio.file.Path fichier) {
        try {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(fichier.toFile());
            if (img == null) return "Format d'image non reconnu.";
            double pas = Math.max(1.0, Math.max(img.getWidth() / 112.0, img.getHeight() / 72.0));
            int w = Math.max(1, (int) (img.getWidth() / pas)), h = Math.max(1, (int) (img.getHeight() / pas));
            byte[] rgb = new byte[w * h * 3];
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                long r = 0, g = 0, b = 0; int n = 0;
                for (int yy = (int) (y * pas); yy < Math.min(img.getHeight(), (int) ((y + 1) * pas)); yy++)
                    for (int xx = (int) (x * pas); xx < Math.min(img.getWidth(), (int) ((x + 1) * pas)); xx++) {
                        int c = img.getRGB(xx, yy);
                        r += (c >> 16) & 255; g += (c >> 8) & 255; b += c & 255; n++;
                    }
                if (n == 0) n = 1;
                int i = (x + y * w) * 3;
                // 0,0,0 veut dire « vide » : le noir pur devient presque noir
                rgb[i] = (byte) Math.max(1, r / n); rgb[i + 1] = (byte) (g / n); rgb[i + 2] = (byte) (b / n);
            }
            ClientPacketDistributor.sendToServer(new fr.maxence.chantier.ImagePayload(id, w, h,
                    fr.maxence.chantier.Images.compresser(rgb)));
            return "Envoi de l'image…";
        } catch (java.io.IOException | RuntimeException e) {
            return "Image illisible : " + e.getMessage();
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
