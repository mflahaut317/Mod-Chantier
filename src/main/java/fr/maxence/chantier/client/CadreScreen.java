package fr.maxence.chantier.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Style commun des écrans Chantier : monde assombri (sans flou), grand titre en haut,
 * « Chantier » à gauche, une info à droite, et un panneau gris clair façon inventaire.
 * Les écrans placent leur panneau avec cadre() dans init().
 */
public abstract class CadreScreen extends Screen {
    // Couleurs du panneau clair
    static final int FOND = 0xFFC6C6C6;
    static final int CLAIR = 0xFFFFFFFF;
    static final int OMBRE = 0xFF555555;
    static final int NOIR = 0xFF000000;
    static final int CREUX = 0xFF8B8B8B;
    static final int TEXTE = 0xFF3F3F3F;
    static final int DOUX = 0xFF6E6E6E;
    static final int VERT = 0xFF2E7D32;
    static final int OR = 0xFF9A6A00;
    static final int ROUGE = 0xFFB02020;
    static final int SURVOL = 0xFFB0B0B0;

    /** Version installée, affichée en haut à gauche (pour savoir si la mise à jour est passée). */
    static final String VERSION = net.neoforged.fml.ModList.get().getModContainerById(fr.maxence.chantier.Chantier.MODID)
            .map(c -> c.getModInfo().getVersion().toString()).orElse("?");

    protected final Screen parent;
    protected int px, py, pw, ph;
    /** Info en haut à droite (ex. « Étape 2 sur 7 »). */
    protected String info = "";
    /** Points d'étapes sous le titre (etapes = 0 : aucun). */
    protected int etape, etapes;
    /** Écran sans panneau central (le monde doit rester visible). */
    protected boolean sansPanneau;

    protected CadreScreen(String titre, Screen parent) {
        super(Component.literal(titre));
        this.parent = parent;
    }

    /** Centre un panneau de cette taille, sous l'en-tête. */
    protected void cadre(int largeur, int hauteur) {
        pw = Math.min(largeur, width - 8);
        ph = Math.min(hauteur, height - 66);
        px = (width - pw) / 2;
        py = Math.max(32, (height - ph) / 2 - 8);
    }

    /** Haut de l'en-tête, collé au panneau. */
    protected int hautTitre() { return Math.max(4, py - 28); }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (sansPanneau) return;
        g.fill(0, 0, width, height, 0xA0000000);
        panneau(g, px, py, pw, ph);
        dessinerFond(g, mouseX, mouseY);
    }

    /** Contenu dessiné sous les boutons (cartes, images…). */
    protected void dessinerFond(GuiGraphicsExtractor g, int mouseX, int mouseY) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        if (sansPanneau) return;
        int y = hautTitre();
        // Grand titre, une fois et demie plus gros
        String titre = titre();
        g.pose().pushMatrix();
        g.pose().translate(width / 2f, y);
        g.pose().scale(1.5f, 1.5f);
        g.text(font, titre, -font.width(titre) / 2, 0, 0xFFFFFFFF);
        g.pose().popMatrix();
        int ligne = y + 16;
        g.text(font, "Chantier " + VERSION, px, ligne, 0xFFAAAAAA);
        if (!info.isEmpty()) g.text(font, info, px + pw - font.width(info), ligne, 0xFFAAAAAA);
        if (etapes > 0) {
            int total = etapes * 7 + 6;
            int x = width / 2 - total / 2;
            for (int i = 0; i < etapes; i++) {
                int l = i == etape ? 12 : 5;
                g.fill(x, ligne + 3, x + l, ligne + 6, i == etape ? 0xFFFFFFFF : 0xFF888888);
                x += l + 2;
            }
        }
    }

    /** Titre affiché en haut (par défaut celui de l'écran). */
    protected String titre() { return getTitle().getString(); }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------- Outils de dessin ----------

    /** Panneau en relief : bord noir, liseré blanc en haut à gauche, ombre en bas à droite. */
    static void panneau(GuiGraphicsExtractor g, int x, int y, int l, int h) {
        g.fill(x, y, x + l, y + h, NOIR);
        g.fill(x + 1, y + 1, x + l - 1, y + h - 1, CLAIR);
        g.fill(x + 3, y + 3, x + l - 1, y + h - 1, OMBRE);
        g.fill(x + 3, y + 3, x + l - 3, y + h - 3, FOND);
    }

    /** Zone en creux (comme une case d'inventaire). */
    static void creux(GuiGraphicsExtractor g, int x, int y, int l, int h, int fond) {
        g.fill(x, y, x + l, y + h, OMBRE);
        g.fill(x + 1, y + 1, x + l, y + h, CLAIR);
        g.fill(x + 1, y + 1, x + l - 1, y + h - 1, fond);
    }

    /** Texte sombre sans ombre, lisible sur le panneau clair. */
    static void texte(GuiGraphicsExtractor g, Font font, String s, int x, int y, int couleur) {
        g.text(font, s, x, y, couleur, false);
    }

    /** Icône d'objet dans une petite case en creux (20 x 20). */
    static void icone(GuiGraphicsExtractor g, ItemStack pile, int x, int y) {
        creux(g, x, y, 20, 20, CREUX);
        g.item(pile, x + 2, y + 2);
    }

    /** Petit carré de couleur avec bord noir (légende). */
    static void carre(GuiGraphicsExtractor g, int x, int y, int couleur) {
        g.fill(x, y, x + 11, y + 11, NOIR);
        g.fill(x + 1, y + 1, x + 10, y + 10, couleur);
    }
}
