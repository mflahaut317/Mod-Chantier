package fr.maxence.chantier.client;

import fr.maxence.chantier.ActionPayload;
import fr.maxence.chantier.EtatPayload;
import fr.maxence.chantier.GaleriePayload;
import fr.maxence.chantier.GalerieRecue;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Galerie commune du serveur, comme une page web :
 * - le catalogue montre une carte par projet (image, intitulé, auteur, description) ;
 * - un clic sur une carte ouvre la fiche : grande image, description et tout ce qu'il faut pour le construire.
 */
public class GalerieScreen extends Screen {
    private static final int CARTE_L = 220;
    private static final int CARTE_H = 80;
    private static final int ECART = 6;
    private static final int LIGNE_MAT = 18;
    private static final int GRIS = 0xFFAAAAAA;
    private static final int SOMBRE = 0xFF777777;
    private static final int TEXTE = 0xFFDDDDDD;
    private static final int OR = 0xFFFFD24A;
    private static final int BLANC = 0xFFFFFFFF;

    private final Screen parent;
    private GaleriePayload derniere = null;
    private boolean demandee = false;
    private int defilement = 0;        // catalogue : en rangées
    private int defilementMat = 0;     // fiche : en lignes de matériaux
    private String detailId = null;    // null = catalogue
    private boolean confirmerSuppression = false;
    private String message = "";

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

    private GaleriePayload.Entree detail() {
        if (detailId == null) return null;
        for (GaleriePayload.Entree e : entrees()) if (e.id().equals(detailId)) return e;
        return null;
    }

    private void ouvrir(String id) {
        detailId = id;
        defilementMat = 0;
        confirmerSuppression = false;
        message = "";
        rebuildWidgets();
    }

    // ---------- Catalogue : grille de cartes ----------

    private int colonnes() { return Math.max(1, Math.min(3, (width - 12 + ECART) / (CARTE_L + ECART))); }

    private int rangeesVisibles() { return Math.max(1, (height - 36 - 34) / (CARTE_H + ECART)); }

    private int grilleGauche() {
        int c = colonnes();
        return (width - (c * CARTE_L + (c - 1) * ECART)) / 2;
    }

    /** Index de la carte sous la souris, ou -1. */
    private int carteSous(double mx, double my) {
        List<GaleriePayload.Entree> liste = entrees();
        int c = colonnes();
        int premier = defilement * c, fin = Math.min(liste.size(), premier + rangeesVisibles() * c);
        for (int i = premier; i < fin; i++) {
            int k = i - premier;
            int x = grilleGauche() + (k % c) * (CARTE_L + ECART);
            int y = 36 + (k / c) * (CARTE_H + ECART);
            if (mx >= x && mx < x + CARTE_L && my >= y && my < y + CARTE_H) return i;
        }
        return -1;
    }

    // ---------- Fiche ----------

    private int ficheGauche() { return Math.max(8, width / 2 - 230); }

    private int ficheLargeur() { return Math.min(460, width - 16); }

    private List<String> lignesDescription(GaleriePayload.Entree e) {
        String d = e.description().isBlank() ? "Pas de description." : e.description();
        return couper(font, d, ficheLargeur(), 3);
    }

    private int ficheHaut(GaleriePayload.Entree e) { return 36 + lignesDescription(e).size() * 10 + 6; }

    private int coteApercu(GaleriePayload.Entree e) { return Math.max(48, Math.min(150, height - ficheHaut(e) - 40)); }

    private int matVisibles(GaleriePayload.Entree e) { return Math.max(1, (height - 40 - (ficheHaut(e) + 12)) / LIGNE_MAT); }

    @Override
    protected void init() {
        if (!demandee) { envoyer("galerie", ""); demandee = true; }
        derniere = GalerieRecue.get();
        GaleriePayload.Entree e = detail();
        if (detailId != null && e == null && derniere != null) detailId = null; // projet retiré entre-temps

        if (e == null) {
            // Un bouton « Voir » par carte (le clic sur la carte entière marche aussi quand le jeu le transmet)
            List<GaleriePayload.Entree> liste = entrees();
            int c = colonnes();
            int premier = defilement * c, fin = Math.min(liste.size(), premier + rangeesVisibles() * c);
            for (int i = premier; i < fin; i++) {
                String id = liste.get(i).id();
                int k = i - premier;
                int x = grilleGauche() + (k % c) * (CARTE_L + ECART);
                int y = 36 + (k / c) * (CARTE_H + ECART);
                addRenderableWidget(Button.builder(Component.literal("Voir la fiche"), b -> ouvrir(id))
                        .bounds(x + CARTE_L - 84, y + CARTE_H - 22, 80, 18).build());
            }
            addRenderableWidget(Button.builder(Component.literal("Mes schémas"),
                            b -> minecraft.gui.setScreen(new SchemaScreen(this)))
                    .bounds(width / 2 - 160, height - 28, 100, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Retour"), b -> onClose())
                    .bounds(width / 2 - 55, height - 28, 100, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Trouver des schémas"),
                            ConfirmLinkScreen.confirmLink(this, URI.create("https://www.minecraft-schematics.com/")))
                    .bounds(width / 2 + 50, height - 28, 110, 20).build());
            return;
        }

        int x = width / 2 - 188, y = height - 28;
        addRenderableWidget(Button.builder(Component.literal("Utiliser pour mon chantier"), b -> {
            envoyer("galerie_utiliser", e.id());
            onClose();
        }).bounds(x, y, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Modifier"), b ->
                minecraft.gui.setScreen(new FicheScreen(this, "Modifier la fiche", e.nom(), e.description(), "Enregistrer",
                        (nom, description) -> envoyer("galerie_modifier", e.id() + "\n" + nom + "\n" + description))))
                .bounds(x + 155, y, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal(confirmerSuppression ? "Sûr ?" : "Retirer"), b -> {
            if (confirmerSuppression) {
                envoyer("galerie_supprimer", e.id());
                detailId = null;
            }
            confirmerSuppression = !confirmerSuppression;
            rebuildWidgets();
        }).bounds(x + 230, y, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Galerie"), b -> { detailId = null; rebuildWidgets(); })
                .bounds(x + 305, y, 70, 20).build());
    }

    @Override
    public void tick() {
        super.tick();
        if (GalerieRecue.get() != derniere) rebuildWidgets(); // la galerie vient d'arriver ou a changé
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClic) {
        if (super.mouseClicked(event, doubleClic)) return true;
        if (detailId == null && event.button() == 0) {
            int i = carteSous(event.x(), event.y());
            if (i >= 0) { ouvrir(entrees().get(i).id()); return true; }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        int pas = -(int) Math.signum(vertical);
        GaleriePayload.Entree e = detail();
        if (e == null) {
            int rangees = (entrees().size() + colonnes() - 1) / colonnes();
            int avant = defilement;
            defilement = Math.max(0, Math.min(Math.max(0, rangees - rangeesVisibles()), defilement + pas));
            if (defilement != avant) rebuildWidgets();
        } else {
            int max = Math.max(0, e.lignes().size() - matVisibles(e));
            defilementMat = Math.max(0, Math.min(max, defilementMat + pas * 3));
        }
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        GaleriePayload.Entree e = detail();
        if (e == null) dessinerCatalogue(g, mouseX, mouseY);
        else dessinerFiche(g, e);
    }

    private void dessinerCatalogue(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        List<GaleriePayload.Entree> liste = entrees();
        String titre = "Galerie commune (" + liste.size() + (liste.size() > 1 ? " projets)" : " projet)");
        g.text(font, titre, width / 2 - font.width(titre) / 2, 10, OR);
        String aide = !message.isEmpty() ? message : "Clique sur un projet pour voir ce qu'il faut · glisse un schéma ici pour l'importer";
        g.text(font, aide, width / 2 - font.width(aide) / 2, 22, GRIS);

        if (GalerieRecue.get() == null) { centre(g, "Chargement…", 60); return; }
        if (liste.isEmpty()) {
            centre(g, "Aucun projet partagé pour l'instant.", 60);
            centre(g, "« Mes schémas » → « Partager » pour en ajouter un.", 72);
            return;
        }

        int c = colonnes();
        int premier = defilement * c, fin = Math.min(liste.size(), premier + rangeesVisibles() * c);
        int survol = carteSous(mouseX, mouseY);
        for (int i = premier; i < fin; i++) {
            GaleriePayload.Entree e = liste.get(i);
            int k = i - premier;
            int x = grilleGauche() + (k % c) * (CARTE_L + ECART);
            int y = 36 + (k / c) * (CARTE_H + ECART);
            boolean dessus = i == survol;
            g.fill(x, y, x + CARTE_L, y + CARTE_H, dessus ? 0xC0303030 : 0xB0000000);
            if (dessus) cadre(g, x, y, CARTE_L, CARTE_H, OR);

            g.fill(x + 4, y + 4, x + 76, y + 76, 0xFF202020);
            dessinerImage(g, e, x + 4, y + 4, 72);

            int tx = x + 82, tl = CARTE_L - 86;
            g.text(font, font.plainSubstrByWidth(e.nom(), tl), tx, y + 5, BLANC);
            g.text(font, font.plainSubstrByWidth("par " + e.auteur() + " · " + e.lignes().size() + " matériaux", tl), tx, y + 16, GRIS);
            if (e.description().isBlank()) {
                g.text(font, "Pas de description", tx, y + 30, SOMBRE);
            } else {
                int ly = y + 30;
                for (String l : couper(font, e.description(), tl, 2)) { g.text(font, l, tx, ly, TEXTE); ly += 10; }
            }
        }
        int rangees = (liste.size() + c - 1) / c;
        if (rangees > rangeesVisibles()) {
            String pos = "Molette pour défiler (" + (defilement + 1) + "/" + (rangees - rangeesVisibles() + 1) + ")";
            g.text(font, pos, width / 2 - font.width(pos) / 2, height - 40, SOMBRE);
        }
    }

    private void dessinerFiche(GuiGraphicsExtractor g, GaleriePayload.Entree e) {
        int x = ficheGauche();
        g.text(font, e.nom(), width / 2 - font.width(e.nom()) / 2, 8, OR);
        String sous = !message.isEmpty() ? message : "par " + e.auteur() + " · glisse une image (.png/.jpg) pour changer l'illustration";
        g.text(font, sous, width / 2 - font.width(sous) / 2, 20, GRIS);

        int y = 36;
        for (String l : lignesDescription(e)) {
            g.text(font, l, x, y, e.description().isBlank() ? SOMBRE : TEXTE);
            y += 10;
        }

        int haut = ficheHaut(e), cote = coteApercu(e);
        g.fill(x, haut, x + cote, haut + cote, 0xFF202020);
        if (!dessinerImage(g, e, x, haut, cote)) g.text(font, "(pas d'aperçu)", x + 4, haut + 4, GRIS);

        // Tout ce qu'il faut, avec le nom de chaque objet
        int mx = x + cote + 14, mr = x + ficheLargeur();
        int total = e.lignes().stream().mapToInt(EtatPayload.Ligne::requis).sum();
        g.text(font, "Il te faut (" + e.lignes().size() + " matériaux, " + ChantierClient.quantite(total) + " au total) :", mx, haut, OR);
        int my = haut + 12;
        int fin = Math.min(e.lignes().size(), defilementMat + matVisibles(e));
        for (int i = defilementMat; i < fin; i++) {
            EtatPayload.Ligne l = e.lignes().get(i);
            Identifier id = Identifier.tryParse(l.item());
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) continue;
            ItemStack pile = new ItemStack(BuiltInRegistries.ITEM.getValue(id));
            if (i % 2 == 0) g.fill(mx - 2, my - 1, mr, my + 17, 0x40000000);
            g.item(pile, mx, my);
            String q = ChantierClient.quantite(l.requis());
            g.text(font, font.plainSubstrByWidth(pile.getHoverName().getString(), mr - mx - 30 - font.width(q)), mx + 20, my + 4, BLANC);
            g.text(font, q, mr - 2 - font.width(q), my + 4, TEXTE);
            my += LIGNE_MAT;
        }
        if (e.lignes().size() > matVisibles(e)) {
            String pos = "Molette : " + (defilementMat + 1) + "–" + fin + " sur " + e.lignes().size();
            g.text(font, pos, mr - font.width(pos), height - 40, SOMBRE);
        }
    }

    private void centre(GuiGraphicsExtractor g, String texte, int y) {
        g.text(font, texte, width / 2 - font.width(texte) / 2, y, GRIS);
    }

    private static void cadre(GuiGraphicsExtractor g, int x, int y, int l, int h, int couleur) {
        g.fill(x, y, x + l, y + 1, couleur);
        g.fill(x, y + h - 1, x + l, y + h, couleur);
        g.fill(x, y, x + 1, y + h, couleur);
        g.fill(x + l - 1, y, x + l, y + h, couleur);
    }

    /** Coupe un texte en lignes de largeur max ; « … » si ça dépasse le nombre de lignes. */
    static List<String> couper(Font font, String texte, int largeur, int maxLignes) {
        List<String> lignes = new ArrayList<>();
        StringBuilder courante = new StringBuilder();
        for (String mot : texte.strip().split("\\s+")) {
            if (mot.isEmpty()) continue;
            String essai = courante.isEmpty() ? mot : courante + " " + mot;
            if (font.width(essai) <= largeur) { courante.setLength(0); courante.append(essai); continue; }
            if (!courante.isEmpty()) lignes.add(courante.toString());
            courante.setLength(0);
            courante.append(font.width(mot) <= largeur ? mot : font.plainSubstrByWidth(mot, largeur));
        }
        if (!courante.isEmpty()) lignes.add(courante.toString());
        if (lignes.size() > maxLignes) {
            List<String> coupees = new ArrayList<>(lignes.subList(0, maxLignes));
            String derniere = coupees.get(maxLignes - 1);
            coupees.set(maxLignes - 1, font.plainSubstrByWidth(derniere, largeur - font.width("…")) + "…");
            return coupees;
        }
        return lignes;
    }

    /** Aperçus décompressés, gardés en mémoire tant que l'écran est ouvert. */
    private final Map<String, byte[]> images = new HashMap<>();

    private byte[] image(GaleriePayload.Entree e) {
        if (e.largeur() <= 0 || e.hauteur() <= 0) return null;
        String cle = e.id() + ":" + e.largeur() + "x" + e.hauteur() + ":" + e.apercu().length;
        return images.computeIfAbsent(cle, k -> fr.maxence.chantier.Images.decompresser(e.apercu(), e.largeur() * e.hauteur() * 3));
    }

    /** Dessine l'aperçu centré dans un carré de côté « boite ». @return false s'il n'y en a pas */
    private boolean dessinerImage(GuiGraphicsExtractor g, GaleriePayload.Entree e, int bx, int by, int boite) {
        byte[] p = image(e);
        if (p == null) return false;
        int w = e.largeur(), h = e.hauteur();
        double echelle = boite / (double) Math.max(w, h);
        int iw = Math.max(1, (int) Math.round(w * echelle)), ih = Math.max(1, (int) Math.round(h * echelle));
        int ox = bx + (boite - iw) / 2, oy = by + (boite - ih) / 2;
        if (echelle >= 1) {
            for (int pz = 0; pz < h; pz++) {
                int y0 = oy + (int) (pz * echelle), y1 = oy + (int) ((pz + 1) * echelle);
                if (y1 == y0) continue;
                for (int px = 0; px < w; px++) {
                    int rgb = pixel(p, px + pz * w);
                    if (rgb == 0) continue;
                    int x0 = ox + (int) (px * echelle), x1 = ox + (int) ((px + 1) * echelle);
                    if (x1 > x0) g.fill(x0, y0, x1, y1, 0xFF000000 | rgb);
                }
            }
        } else {
            // Image plus grande que la case : un point par pixel d'écran (léger pour les vignettes)
            for (int dy = 0; dy < ih; dy++) {
                int sy = Math.min(h - 1, (int) (dy / echelle));
                for (int dx = 0; dx < iw; dx++) {
                    int rgb = pixel(p, Math.min(w - 1, (int) (dx / echelle)) + sy * w);
                    if (rgb != 0) g.fill(ox + dx, oy + dy, ox + dx + 1, oy + dy + 1, 0xFF000000 | rgb);
                }
            }
        }
        return true;
    }

    private static int pixel(byte[] p, int index) {
        int i = index * 3;
        return ((p[i] & 255) << 16) | ((p[i + 1] & 255) << 8) | (p[i + 2] & 255);
    }

    /** Schéma glissé : importé dans le chantier. Image glissée sur une fiche ouverte : devient son illustration. */
    @Override
    public void onFilesDrop(List<java.nio.file.Path> fichiers) {
        for (java.nio.file.Path f : fichiers) {
            String n = f.getFileName().toString().toLowerCase();
            if (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")) {
                GaleriePayload.Entree e = detail();
                if (e == null) { message = "Ouvre d'abord un projet, puis glisse l'image dessus."; return; }
                message = envoyerImage(e.id(), f);
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
