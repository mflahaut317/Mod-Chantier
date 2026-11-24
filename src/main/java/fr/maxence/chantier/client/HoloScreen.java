package fr.maxence.chantier.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Réglages de l'hologramme dans un panneau clair à gauche : le reste de l'écran montre le monde,
 * sans flou, pour voir l'hologramme bouger pendant qu'on le règle.
 */
public class HoloScreen extends CadreScreen {
    private static final int X = 8;
    private static final int L = 150;
    private static final int DEMI = 73;
    private static final int TIERS = 48;

    private String message = "";

    public HoloScreen(Screen parent) {
        super("Sur le terrain", parent);
        sansPanneau = true;
    }

    private Button bouton(String texte, int x, int y, int l, Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(texte), b -> {
            action.run();
            rebuildWidgets();
        }).bounds(x, y, l, 20).build());
    }

    @Override
    protected void init() {
        int y = 54;
        if (Hologramme.plan == null) {
            if (Hologramme.dernierImporte != null) {
                String nom = font.plainSubstrByWidth("Afficher « " + Hologramme.dernierImporte.getFileName() + " »", L - 8);
                bouton(nom, X, y, L, () -> {
                    String erreur = Hologramme.charger(Hologramme.dernierImporte);
                    message = erreur == null ? "" : erreur;
                    if (erreur == null) minecraft.gui.setScreen(null); // on le place en visant
                });
                y += 24;
            }
            addRenderableWidget(Button.builder(Component.literal("Choisir un schéma"),
                    b -> minecraft.gui.setScreen(new SchemaScreen(this))).bounds(X, y, L, 20).build());
            y += 24;
            bouton("Fermer", X, y, L, this::onClose);
            return;
        }

        bouton("En visant", X, y, DEMI, () -> {
            Hologramme.commencerSuivi();
            minecraft.gui.setScreen(null);
        });
        bouton("Tourner", X + L - DEMI, y, DEMI, Hologramme::tourner);
        y += 22;
        bouton("Avancer", X, y, TIERS, () -> Hologramme.deplacer(1, 0, 0));
        bouton("Reculer", X + 51, y, TIERS, () -> Hologramme.deplacer(-1, 0, 0));
        bouton("Monter", X + 102, y, TIERS, () -> Hologramme.deplacer(0, 0, 1));
        y += 21;
        bouton("Gauche", X, y, TIERS, () -> Hologramme.deplacer(0, -1, 0));
        bouton("Droite", X + 51, y, TIERS, () -> Hologramme.deplacer(0, 1, 0));
        bouton("Baisser", X + 102, y, TIERS, () -> Hologramme.deplacer(0, 0, -1));
        y += 35; // place pour le texte « Couche »
        bouton("Couche -", X, y, TIERS, () -> Hologramme.changerCouche(-1));
        bouton("Toutes", X + 51, y, TIERS, Hologramme::toutesLesCouches);
        bouton("Couche +", X + 102, y, TIERS, () -> Hologramme.changerCouche(1));
        y += 24;
        bouton(Hologramme.modeBlocs ? "Rendu : blocs" : "Rendu : cubes", X, y, DEMI,
                () -> Hologramme.modeBlocs = !Hologramme.modeBlocs);
        bouton(Hologramme.parDessus ? "À travers : oui" : "À travers : non", X + L - DEMI, y, DEMI,
                () -> Hologramme.parDessus = !Hologramme.parDessus);
        y += 21;
        bouton(Hologramme.visible ? "Masquer" : "Afficher", X, y, DEMI, Hologramme::basculerVisible);
        bouton(Hologramme.partage ? "Retirer (groupe)" : "Retirer", X + L - DEMI, y, DEMI, Hologramme::retirer);
        y += 21;
        Button groupe = bouton(Hologramme.partage ? "Partagé avec le groupe ✔" : "Partager avec le groupe", X, y, L,
                () -> message = Hologramme.partagerAvecGroupe());
        groupe.active = !Hologramme.partage;
        y += 21;
        bouton("Autre schéma", X, y, DEMI, () -> minecraft.gui.setScreen(new SchemaScreen(this)));
        bouton("Fermer", X + L - DEMI, y, DEMI, this::onClose);
    }

    /** Panneau clair à gauche seulement : le monde reste net derrière. */
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        panneau(g, 2, 2, L + 12, height - 4);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        texte(g, font, "Sur le terrain", X, 9, TEXTE);
        g.fill(X, 19, X + L, 20, CREUX);
        if (Hologramme.plan == null) {
            texte(g, font, "Aucun hologramme affiché.", X, 26, DOUX);
            texte(g, font, font.plainSubstrByWidth(message, L), X, 38, ROUGE);
            return;
        }
        texte(g, font, font.plainSubstrByWidth(Hologramme.nom, L), X, 24, TEXTE);
        String avancement = Hologramme.total == 0 ? "Posés : …"
                : "Posés : " + Hologramme.poses + " / " + Hologramme.total
                + " (" + (Hologramme.poses * 100 / Hologramme.total) + " %)";
        texte(g, font, avancement, X, 35, Hologramme.total > 0 && Hologramme.poses == Hologramme.total ? VERT : DOUX);
        String ligne = !message.isEmpty() ? message : Hologramme.largeur() + " x " + Hologramme.plan.sy + " x "
                + Hologramme.profondeur() + (Hologramme.plan.tronque ? " (tronqué)" : "") + (Hologramme.partage ? " · groupe" : "");
        texte(g, font, font.plainSubstrByWidth(ligne, L), X, 45, message.isEmpty() ? DOUX : OR);
        String couche = Hologramme.couche < 0 ? "Couche : toutes"
                : "Couche : " + (Hologramme.couche + 1) + " / " + Hologramme.plan.sy;
        texte(g, font, couche, X, 122, TEXTE);

        // Légende des couleurs
        int y = height - 26;
        carre(g, X, y, 0xFF9EC0E8);
        texte(g, font, "à poser", X + 14, y + 2, TEXTE);
        carre(g, X + 66, y, 0xFFE04040);
        texte(g, font, "à changer", X + 80, y + 2, TEXTE);
        texte(g, font, "Bien posé : plus rien affiché.", X, y + 13, DOUX);
    }
}
