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
public class GalerieScreen extends CadreScreen {
    private static final int CARTE_L = 220;
    private static final int CARTE_H = 80;
    private static final int ECART = 6;
    private static final int LIGNE_MAT = 18;

    private GaleriePayload derniere = null;
    private boolean demandee = false;
    private int defilement = 0;        // catalogue : en rangées
    private int defilementMat = 0;     // fiche : en lignes de matériaux
    private String detailId = null;    // null = catalogue
    private boolean confirmerSuppression = false;
    private String message = "";
    private int rotation = 0;
    /** Scènes 3D des fiches déjà construites (id + rotation). */
    private final Map<String, Scene> scenes = new HashMap<>();

    /** Bouton invisible qui couvre toute une carte : un clic n'importe où sur la carte ouvre la fiche. */
    private static final class CarteBouton extends Button {
        CarteBouton(Button.Builder b) { super(b); }

        @Override
        protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
            if (isHovered) {
                int x = getX(), y = getY(), l = getWidth(), h = getHeight();
                g.fill(x, y, x + l, y + 1, 0xFFFFFFFF);
                g.fill(x, y + h - 1, x + l, y + h, 0xFFFFFFFF);
                g.fill(x, y, x + 1, y + h, 0xFFFFFFFF);
                g.fill(x + l - 1, y, x + l, y + h, 0xFFFFFFFF);
            }
        }
    }

    public GalerieScreen(Screen parent) {
        super("Galerie commune", parent);
    }

    @Override
    protected String titre() {
        GaleriePayload.Entree e = detail();
        return e == null ? "Galerie commune" : e.nom();
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
        if (fr.maxence.chantier.VolumeRecu.get(id) == null && demandes.add(id)) envoyer("galerie_volume", id);
        rotation = 0;
        detailId = id;
        defilementMat = 0;
        confirmerSuppression = false;
        message = "";
        rebuildWidgets();
    }

    // ---------- Catalogue : grille de cartes ----------

    private int colonnes() { return Math.max(1, Math.min(3, (width - 32 + ECART) / (CARTE_L + ECART))); }

    private int rangeesVisibles() { return Math.max(1, (ph - 58) / (CARTE_H + ECART)); }

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
            int y = py + 12 + (k / c) * (CARTE_H + ECART);
            if (mx >= x && mx < x + CARTE_L && my >= y && my < y + CARTE_H) return i;
        }
        return -1;
    }

    // ---------- Fiche ----------

    private int ficheGauche() { return px + 12; }

    private int ficheLargeur() { return pw - 24; }

    private List<String> lignesDescription(GaleriePayload.Entree e) {
        String d = e.description().isBlank() ? "Pas de description." : e.description();
        return couper(font, d, ficheLargeur(), 3);
    }

    private int ficheHaut(GaleriePayload.Entree e) { return py + 10 + lignesDescription(e).size() * 10 + 6; }

    private int coteApercu(GaleriePayload.Entree e) { return Math.max(48, Math.min(150, py + ph - 46 - ficheHaut(e))); }

    private int matVisibles(GaleriePayload.Entree e) { return Math.max(1, (py + ph - 46 - (ficheHaut(e) + 12)) / LIGNE_MAT); }

    @Override
    protected void init() {
        if (!demandee) { envoyer("galerie", ""); demandee = true; }
        derniere = GalerieRecue.get();
        GaleriePayload.Entree e = detail();
        if (detailId != null && e == null && derniere != null) detailId = null; // projet retiré entre-temps
        if (e == null) {
            int c = colonnes();
            cadre(c * CARTE_L + (c - 1) * ECART + 24, 1000);
            info = entrees().size() + (entrees().size() > 1 ? " projets" : " projet");
        } else {
            cadre(484, 1000);
            info = "par " + e.auteur();
        }

        if (e == null) {
            // Un bouton « Voir » par carte (le clic sur la carte entière marche aussi quand le jeu le transmet)
            List<GaleriePayload.Entree> liste = entrees();
            int c = colonnes();
            int premier = defilement * c, fin = Math.min(liste.size(), premier + rangeesVisibles() * c);
            for (int i = premier; i < fin; i++) {
                String id = liste.get(i).id();
                if (fr.maxence.chantier.VolumeRecu.get(id) == null && demandes.add(id)) envoyer("galerie_volume", id);
                int k = i - premier;
                int x = grilleGauche() + (k % c) * (CARTE_L + ECART);
                int y = py + 12 + (k / c) * (CARTE_H + ECART);
                addRenderableWidget(Button.builder(Component.literal("Voir"), b -> ouvrir(id))
                        .bounds(x + CARTE_L - 46, y + CARTE_H - 24, 42, 20).build());
                addRenderableWidget(Button.builder(Component.literal(liste.get(i).nom()), b -> ouvrir(id))
                        .bounds(x, y, CARTE_L, CARTE_H).build(CarteBouton::new));
            }
            addRenderableWidget(Button.builder(Component.literal("Mes schémas"),
                            b -> minecraft.gui.setScreen(new SchemaScreen(this)))
                    .bounds(width / 2 - 160, py + ph - 28, 100, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Retour"), b -> onClose())
                    .bounds(width / 2 - 55, py + ph - 28, 100, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Trouver des schémas"),
                            ConfirmLinkScreen.confirmLink(this, URI.create("https://www.minecraft-schematics.com/")))
                    .bounds(width / 2 + 50, py + ph - 28, 110, 20).build());
            return;
        }

        int ax = ficheGauche(), ay = ficheHaut(e), cote = coteApercu(e);
        addRenderableWidget(Button.builder(Component.literal("<"), b -> { rotation = (rotation + 3) % 4; })
                .bounds(ax + 2, ay + cote - 22, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> { rotation = (rotation + 1) % 4; })
                .bounds(ax + cote - 22, ay + cote - 22, 20, 20).build());
        int x = width / 2 - 188, y = py + ph - 28;
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
        addRenderableWidget(Button.builder(Component.literal("Retour"), b -> { detailId = null; rebuildWidgets(); })
                .bounds(x + 305, y, 70, 20).build());
    }

    @Override
    public void tick() {
        super.tick();
        if (GalerieRecue.get() != derniere) rebuildWidgets(); // la galerie vient d'arriver ou a changé
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClic) {
        // La carte d'abord : un clic n'importe où dessus ouvre la fiche
        if (detailId == null && event.button() == 0) {
            int i = carteSous(event.x(), event.y());
            if (i >= 0) { ouvrir(entrees().get(i).id()); return true; }
        }
        return super.mouseClicked(event, doubleClic);
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

    /** Cartes et fiche dessinées sous les boutons (sinon elles les cacheraient). */
    @Override
    protected void dessinerFond(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        GaleriePayload.Entree e = detail();
        if (e == null) dessinerCatalogue(g, mouseX, mouseY);
        else dessinerFiche(g, e);
    }

    private void dessinerCatalogue(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        List<GaleriePayload.Entree> liste = entrees();
        String aide = !message.isEmpty() ? message : "« Voir la fiche » pour ce qu'il faut · glisse un schéma ici pour l'importer";
        texte(g, font, font.plainSubstrByWidth(aide, pw - 24), px + 12, py + ph - 42, message.isEmpty() ? DOUX : OR);

        if (GalerieRecue.get() == null) { centre(g, "Chargement…", py + 30); return; }
        if (liste.isEmpty()) {
            centre(g, "Aucun projet partagé pour l'instant.", py + 30);
            centre(g, "« Mes schémas » puis « Partager » pour en ajouter un.", py + 42);
            return;
        }

        int c = colonnes();
        int premier = defilement * c, fin = Math.min(liste.size(), premier + rangeesVisibles() * c);
        int survol = carteSous(mouseX, mouseY);
        for (int i = premier; i < fin; i++) {
            GaleriePayload.Entree e = liste.get(i);
            int k = i - premier;
            int x = grilleGauche() + (k % c) * (CARTE_L + ECART);
            int y = py + 12 + (k / c) * (CARTE_H + ECART);
            boolean dessus = i == survol;
            creux(g, x, y, CARTE_L, CARTE_H, dessus ? 0xFFE2E2E2 : 0xFFD4D4D4);
            if (dessus) cadreCouleur(g, x, y, CARTE_L, CARTE_H, 0xFFFFFFFF);

            ciel(g, x + 4, y + 4, 72);
            Scene vignette = scene(e, 0);
            if (vignette != null) {
                g.enableScissor(x + 4, y + 4, x + 76, y + 76);
                vignette.dessinerCentre(g, x + 4, y + 4, 72, 72, 8);
                g.disableScissor();
            } else {
                dessinerImage(g, e, x + 4, y + 4, 72);
            }

            int tx = x + 82, tl = CARTE_L - 86;
            texte(g, font, font.plainSubstrByWidth(e.nom(), tl), tx, y + 6, TEXTE);
            texte(g, font, font.plainSubstrByWidth("par " + e.auteur() + " · " + e.lignes().size() + " matériaux", tl), tx, y + 17, DOUX);
            if (e.description().isBlank()) {
                texte(g, font, "Pas de description", tx, y + 31, DOUX);
            } else {
                int ly = y + 31;
                for (String l : couper(font, e.description(), tl, 2)) { texte(g, font, l, tx, ly, TEXTE); ly += 10; }
            }
        }
        int rangees = (liste.size() + c - 1) / c;
        if (rangees > rangeesVisibles()) {
            String pos = "Molette (" + (defilement + 1) + "/" + (rangees - rangeesVisibles() + 1) + ")";
            texte(g, font, pos, px + pw - 12 - font.width(pos), py + ph - 42, DOUX);
        }
    }

    /** Scène 3D texturée du projet (null tant que ses blocs ne sont pas arrivés, ou s'il n'en a pas). */
    private Scene scene(GaleriePayload.Entree e) { return scene(e, rotation); }

    private Scene scene(GaleriePayload.Entree e, int rot) {
        byte[] donnees = fr.maxence.chantier.VolumeRecu.get(e.id());
        if (donnees == null || donnees.length == 0) return null;
        return scenes.computeIfAbsent(e.id() + "#" + rot, k -> {
            Volume.Grille grille = Volume.decoder(donnees);
            return grille == null ? null : Scene.depuis(grille, rot);
        });
    }

    /** Ids dont on a déjà demandé les blocs au serveur. */
    private final java.util.Set<String> demandes = new java.util.HashSet<>();

    /** Fond « ciel » derrière les aperçus, comme une petite scène. */
    private static void ciel(GuiGraphicsExtractor g, int x, int y, int cote) {
        g.fill(x - 1, y - 1, x + cote + 1, y + cote + 1, OMBRE);
        g.fillGradient(x, y, x + cote, y + cote, 0xFF6F9AD0, 0xFFA9C6EA);
    }

    private void dessinerFiche(GuiGraphicsExtractor g, GaleriePayload.Entree e) {
        int x = ficheGauche();
        int y = py + 10;
        for (String l : lignesDescription(e)) {
            texte(g, font, l, x, y, e.description().isBlank() ? DOUX : TEXTE);
            y += 10;
        }

        int haut = ficheHaut(e), cote = coteApercu(e);
        ciel(g, x, haut, cote);
        Scene scene = scene(e);
        if (scene != null) {
            g.enableScissor(x, haut, x + cote, haut + cote);
            scene.dessinerCentre(g, x, haut + 2, cote, cote - 22, 14);
            g.disableScissor();
        } else if (!dessinerImage(g, e, x, haut, cote)) {
            texte(g, font, "(pas d'aperçu)", x + 4, haut + 4, TEXTE);
        }

        // Tout ce qu'il faut, avec le nom de chaque objet
        int mx = x + cote + 12, mr = x + ficheLargeur();
        int total = e.lignes().stream().mapToInt(EtatPayload.Ligne::requis).sum();
        texte(g, font, font.plainSubstrByWidth("Il te faut " + e.lignes().size() + " matériaux (" + ChantierClient.quantite(total) + ") :", mr - mx), mx, haut, TEXTE);
        int my = haut + 12;
        creux(g, mx - 3, my - 2, mr - mx + 4, matVisibles(e) * LIGNE_MAT + 3, 0xFFB8B8B8);
        int fin = Math.min(e.lignes().size(), defilementMat + matVisibles(e));
        for (int i = defilementMat; i < fin; i++) {
            EtatPayload.Ligne l = e.lignes().get(i);
            Identifier id = Identifier.tryParse(l.item());
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) continue;
            ItemStack pile = new ItemStack(BuiltInRegistries.ITEM.getValue(id));
            if (i % 2 == 0) g.fill(mx - 2, my - 1, mr, my + 17, 0x18000000);
            g.item(pile, mx, my);
            String q = ChantierClient.quantite(l.requis());
            texte(g, font, font.plainSubstrByWidth(pile.getHoverName().getString(), mr - mx - 30 - font.width(q)), mx + 20, my + 4, TEXTE);
            texte(g, font, q, mr - 3 - font.width(q), my + 4, TEXTE);
            my += LIGNE_MAT;
        }
        String bas = !message.isEmpty() ? message
                : e.lignes().size() > matVisibles(e)
                ? "Molette : " + (defilementMat + 1) + "–" + fin + " sur " + e.lignes().size()
                : "Glisse une image (.png/.jpg) pour changer l'illustration";
        texte(g, font, font.plainSubstrByWidth(bas, ficheLargeur()), x, py + ph - 42, message.isEmpty() ? DOUX : OR);
    }

    private void centre(GuiGraphicsExtractor g, String texte, int y) {
        texte(g, font, texte, width / 2 - font.width(texte) / 2, y, DOUX);
    }

    private static void cadreCouleur(GuiGraphicsExtractor g, int x, int y, int l, int h, int couleur) {
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
}
