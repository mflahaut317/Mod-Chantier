package fr.maxence.chantier.client;

import fr.maxence.chantier.ActionPayload;
import fr.maxence.chantier.ConfigPayload;
import fr.maxence.chantier.ConfigRecue;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Écran Chantier. Sans chantier : on en crée un. Avec un chantier : lien, titre, coffres, membres, affichage.
 * Ouvert par le bouton « Chantier » de l'inventaire ou la commande /chantier.
 */
public class ChantierScreen extends Screen {
    private static final int LARGEUR = 290;
    private static final int BLANC = 0xFFFFFFFF;
    private static final int GRIS = 0xFFAAAAAA;
    private static final int OR = 0xFFFFD24A;
    private static final int VERT = 0xFF55FF55;

    private EditBox champTitre;
    private EditBox champLien;
    private EditBox champPseudo;
    private Button boutonMasquer;
    private Button boutonSelection;
    private boolean confirmerArret = false;
    private ConfigPayload dernierAffiche = null;
    /** Mode affiché : null = pas encore de réponse du serveur. */
    private Boolean modeChantier = null;

    private int gauche;
    private int haut;

    public ChantierScreen() {
        super(Component.literal("Chantier"));
    }

    private static void envoyer(String action, String valeur) {
        ClientPacketDistributor.sendToServer(new ActionPayload(action, valeur));
    }

    private static boolean dansChantier() {
        ConfigPayload c = ConfigRecue.get();
        return c != null && c.dansChantier();
    }

    @Override
    protected void init() {
        gauche = (width - LARGEUR) / 2;
        haut = Math.max(6, height / 2 - 115);
        modeChantier = dansChantier();
        champPseudo = null;
        boutonMasquer = null;
        boutonSelection = null;
        if (modeChantier) initChantier(); else initCreation();

        addRenderableWidget(Button.builder(Component.literal("Fermer"), b -> onClose())
                .bounds(width / 2 + 5, haut + 212, 100, 20).build());

        dernierAffiche = null;
        remplirDepuisConfig();
        envoyer("config", ""); // demande les réglages à jour au serveur
    }

    /** Pas encore de chantier : un titre et « Créer ». */
    private void initCreation() {
        int x = gauche + 50;
        champTitre = new EditBox(font, x, haut + 40, 170, 18, Component.literal("Titre"));
        champTitre.setMaxLength(60);
        champTitre.setHint(Component.literal("Ex. Underground Farm"));
        addRenderableWidget(champTitre);
        addRenderableWidget(Button.builder(Component.literal("Créer"),
                b -> envoyer("creer", champTitre.getValue())).bounds(x + 175, haut + 39, 65, 20).build());
        champLien = null;
    }

    private void initChantier() {
        int x = gauche + 50;

        // Titre
        champTitre = new EditBox(font, x, haut + 20, 170, 18, Component.literal("Titre"));
        champTitre.setMaxLength(60);
        addRenderableWidget(champTitre);
        addRenderableWidget(Button.builder(Component.literal("OK"),
                b -> envoyer("titre", champTitre.getValue())).bounds(x + 175, haut + 19, 65, 20).build());

        // Lien Google Sheets
        champLien = new EditBox(font, x, haut + 46, 170, 18, Component.literal("Lien"));
        champLien.setMaxLength(512);
        champLien.setHint(Component.literal("Colle le lien Google Sheets"));
        addRenderableWidget(champLien);
        addRenderableWidget(Button.builder(Component.literal("Charger"),
                b -> envoyer("lien", champLien.getValue())).bounds(x + 175, haut + 45, 65, 20).build());

        // Coffres choisis à la main
        boutonSelection = addRenderableWidget(Button.builder(Component.literal("Choisir les coffres"), b -> {
            if (enSelection()) {
                envoyer("selection_off", "");
            } else {
                envoyer("selection_on", "");
                onClose(); // ferme l'écran pour pouvoir cliquer sur les coffres
            }
        }).bounds(x, haut + 72, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Vider"),
                b -> envoyer("vider_coffres", "")).bounds(x + 155, haut + 72, 85, 20).build());

        // Membres
        champPseudo = new EditBox(font, x, haut + 110, 170, 18, Component.literal("Pseudo"));
        champPseudo.setMaxLength(16);
        champPseudo.setHint(Component.literal("Pseudo d'un joueur connecté"));
        addRenderableWidget(champPseudo);
        addRenderableWidget(Button.builder(Component.literal("Ajouter"), b -> {
            envoyer("ajouter", champPseudo.getValue());
            champPseudo.setValue("");
        }).bounds(x + 175, haut + 109, 65, 20).build());

        // Actions
        addRenderableWidget(Button.builder(Component.literal("Relire"),
                b -> envoyer("actualiser", "")).bounds(gauche, haut + 148, 70, 20).build());
        boutonMasquer = addRenderableWidget(Button.builder(Component.literal("Masquer"),
                b -> envoyer(estMasque() ? "afficher" : "masquer", "")).bounds(gauche + 73, haut + 148, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Quitter"),
                b -> envoyer("quitter", "")).bounds(gauche + 146, haut + 148, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Supprimer"), b -> {
            if (confirmerArret) {
                envoyer("arreter", "");
                confirmerArret = false;
                b.setMessage(Component.literal("Supprimer"));
            } else {
                confirmerArret = true;
                b.setMessage(Component.literal("Sûr ?"));
            }
        }).bounds(gauche + 219, haut + 148, 71, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Voir la liste complète"),
                b -> minecraft.gui.setScreen(new ListeScreen(this)))
                .bounds(width / 2 - 145, haut + 212, 140, 20).build());
    }

    private boolean enSelection() {
        ConfigPayload c = ConfigRecue.get();
        return c != null && c.selection();
    }

    private boolean estMasque() {
        ConfigPayload c = ConfigRecue.get();
        return c != null && c.masque();
    }

    /** Recopie les réglages reçus dans les champs (sans écraser ce que le joueur est en train de taper). */
    private void remplirDepuisConfig() {
        ConfigPayload c = ConfigRecue.get();
        if (c == null || c == dernierAffiche) return;
        dernierAffiche = c;
        if (!modeChantier) return;
        if (!champTitre.isFocused()) champTitre.setValue(c.titre());
        if (champLien != null && !champLien.isFocused()) champLien.setValue(c.lien());
        if (boutonMasquer != null) boutonMasquer.setMessage(Component.literal(c.masque() ? "Afficher" : "Masquer"));
        if (boutonSelection != null) {
            boutonSelection.setMessage(Component.literal(c.selection() ? "Terminer la sélection" : "Choisir les coffres"));
        }
    }

    @Override
    public void tick() {
        super.tick();
        // Chantier créé, quitté ou supprimé : on reconstruit l'écran dans le bon mode.
        if (modeChantier != null && modeChantier != dansChantier()) {
            rebuildWidgets();
            return;
        }
        remplirDepuisConfig();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        ConfigPayload c = ConfigRecue.get();

        String titre = "Chantier";
        g.text(font, titre, width / 2 - font.width(titre) / 2, haut, OR);

        if (!modeChantier) {
            g.text(font, "Tu n'as pas encore de chantier. Donne-lui un nom :", gauche, haut + 22, GRIS);
            g.text(font, "Titre", gauche, haut + 45, BLANC);
            g.text(font, "Tu pourras ensuite ajouter qui tu veux (ou rester seul).", gauche, haut + 66, GRIS);
            afficherMessage(g, c, haut + 90);
            return;
        }

        g.text(font, "Titre", gauche, haut + 25, BLANC);
        g.text(font, "Lien", gauche, haut + 51, BLANC);
        g.text(font, "Coffres", gauche, haut + 78, BLANC);
        g.text(font, "Membres", gauche, haut + 115, BLANC);
        if (c == null) {
            g.text(font, "En attente du serveur…", gauche, haut + 178, GRIS);
            return;
        }
        String infoCoffres = c.coffres() == 0
                ? "Aucun coffre : seuls les inventaires des membres comptent."
                : c.coffres() + " emplacements de coffre choisis (+ inventaires).";
        g.text(font, font.plainSubstrByWidth(infoCoffres, LARGEUR - 50), gauche + 50, haut + 96, GRIS);
        g.text(font, font.plainSubstrByWidth(c.membres(), LARGEUR - 50), gauche + 50, haut + 133, GRIS);

        String etat = c.materiaux() + " matériaux · " + c.pourcent() + " %";
        g.text(font, etat, gauche, haut + 176, c.pourcent() >= 100 ? VERT : OR);
        afficherMessage(g, c, haut + 190);
    }

    private void afficherMessage(GuiGraphicsExtractor g, ConfigPayload c, int y) {
        if (c == null || c.message().isEmpty()) return;
        g.text(font, font.plainSubstrByWidth(c.message(), LARGEUR), gauche, y, BLANC);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
