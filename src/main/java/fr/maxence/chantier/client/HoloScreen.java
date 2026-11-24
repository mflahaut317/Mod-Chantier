package fr.maxence.chantier.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Réglages de l'hologramme, dans un panneau à gauche : le monde reste visible (pas de flou)
 * pour voir l'hologramme bouger pendant qu'on le règle.
 */
public class HoloScreen extends Screen {
    private static final int X = 6;
    private static final int L = 150;
    private static final int DEMI = 73;
    private static final int GRIS = 0xFFAAAAAA;
    private static final int OR = 0xFFFFD24A;
    private static final int BLANC = 0xFFFFFFFF;

    private final Screen parent;
    private String message = "";

    public HoloScreen(Screen parent) {
        super(Component.literal("Sur le terrain"));
        this.parent = parent;
    }

    private Button bouton(String texte, int x, int y, int l, Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(texte), b -> {
            action.run();
            rebuildWidgets();
        }).bounds(x, y, l, 20).build());
    }

    @Override
    protected void init() {
        int y = 58;
        if (Hologramme.plan == null) {
            if (Hologramme.dernierImporte != null) {
                String nom = font.plainSubstrByWidth("Afficher « " + Hologramme.dernierImporte.getFileName() + " »", L - 8);
                bouton(nom, X, y, L, () -> {
                    String erreur = Hologramme.charger(Hologramme.dernierImporte);
                    message = erreur == null ? "" : erreur;
                });
                y += 24;
            }
            addRenderableWidget(Button.builder(Component.literal("Choisir un schéma"),
                    b -> minecraft.gui.setScreen(new SchemaScreen(this))).bounds(X, y, L, 20).build());
            y += 24;
            bouton("Fermer", X, y, L, this::onClose);
            return;
        }

        bouton("Placer ici", X, y, L, Hologramme::placerIci);
        y += 24;
        bouton("Avancer", X, y, DEMI, () -> Hologramme.deplacer(1, 0, 0));
        bouton("Reculer", X + L - DEMI, y, DEMI, () -> Hologramme.deplacer(-1, 0, 0));
        y += 22;
        bouton("Gauche", X, y, DEMI, () -> Hologramme.deplacer(0, -1, 0));
        bouton("Droite", X + L - DEMI, y, DEMI, () -> Hologramme.deplacer(0, 1, 0));
        y += 22;
        bouton("Monter", X, y, DEMI, () -> Hologramme.deplacer(0, 0, 1));
        bouton("Descendre", X + L - DEMI, y, DEMI, () -> Hologramme.deplacer(0, 0, -1));
        y += 24;
        bouton("Tourner d'un quart", X, y, L, Hologramme::tourner);
        y += 36; // place pour le texte « Couche »
        bouton("Couche -", X, y, DEMI, () -> Hologramme.changerCouche(-1));
        bouton("Couche +", X + L - DEMI, y, DEMI, () -> Hologramme.changerCouche(1));
        y += 22;
        bouton("Toutes les couches", X, y, L, Hologramme::toutesLesCouches);
        y += 24;
        bouton(Hologramme.visible ? "Masquer" : "Afficher", X, y, DEMI, Hologramme::basculerVisible);
        bouton("Retirer", X + L - DEMI, y, DEMI, Hologramme::retirer);
        y += 22;
        bouton("Autre schéma", X, y, DEMI, () -> minecraft.gui.setScreen(new SchemaScreen(this)));
        bouton("Fermer", X + L - DEMI, y, DEMI, this::onClose);
    }

    /** Pas de fond flou : on doit voir l'hologramme derrière. */
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, X + L + 6, height, 0x90000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        g.text(font, "Sur le terrain", X, 8, OR);
        if (Hologramme.plan == null) {
            g.text(font, "Aucun hologramme affiché.", X, 22, GRIS);
            g.text(font, font.plainSubstrByWidth(message, L), X, 34, 0xFFFF7070);
            return;
        }
        g.text(font, font.plainSubstrByWidth(Hologramme.nom, L), X, 22, BLANC);
        String avancement = Hologramme.total == 0 ? "Posés : …"
                : "Posés : " + Hologramme.poses + " / " + Hologramme.total
                + " (" + (Hologramme.poses * 100 / Hologramme.total) + " %)";
        g.text(font, avancement, X, 34, GRIS);
        g.text(font, Hologramme.largeur() + " x " + Hologramme.plan.sy + " x " + Hologramme.profondeur()
                + (Hologramme.plan.tronque ? " (trop grand, tronqué)" : ""), X, 45, GRIS);
        String couche = Hologramme.couche < 0 ? "Couche : toutes"
                : "Couche : " + (Hologramme.couche + 1) + " / " + Hologramme.plan.sy;
        g.text(font, couche, X, 58 + 24 + 22 + 22 + 24 + 24 + 4, OR);
        g.text(font, "Ferme pour marcher autour.", X, height - 12, GRIS);
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
