package fr.maxence.chantier.client;

import fr.maxence.chantier.ActionPayload;
import fr.maxence.chantier.ConfigPayload;
import fr.maxence.chantier.ConfigRecue;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Écran Chantier : lien de la feuille, titre, coffres choisis, affichage.
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
    private Button boutonMasquer;
    private Button boutonSelection;
    private boolean confirmerArret = false;
    private ConfigPayload dernierAffiche = null;

    private int gauche;
    private int haut;

    public ChantierScreen() {
        super(Component.literal("Chantier"));
    }

    private static void envoyer(String action, String valeur) {
        ClientPacketDistributor.sendToServer(new ActionPayload(action, valeur));
    }

    @Override
    protected void init() {
        gauche = (width - LARGEUR) / 2;
        haut = Math.max(6, height / 2 - 115);
        int x = gauche + 50;

        // Titre
        champTitre = new EditBox(font, x, haut + 20, 170, 18, Component.literal("Titre"));
        champTitre.setMaxLength(60);
        champTitre.setHint(Component.literal("Ex. Underground Farm"));
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
        }).bounds(x, haut + 76, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Vider"),
                b -> envoyer("vider_coffres", "")).bounds(x + 155, haut + 76, 85, 20).build());

        // Actions
        addRenderableWidget(Button.builder(Component.literal("Relire la feuille"),
                b -> envoyer("actualiser", "")).bounds(gauche, haut + 114, 94, 20).build());
        boutonMasquer = addRenderableWidget(Button.builder(Component.literal("Masquer la liste"),
                b -> envoyer(estMasque() ? "afficher" : "masquer", "")).bounds(gauche + 98, haut + 114, 94, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Arrêter"), b -> {
            if (confirmerArret) {
                envoyer("arreter", "");
                confirmerArret = false;
                b.setMessage(Component.literal("Arrêter"));
            } else {
                confirmerArret = true;
                b.setMessage(Component.literal("Sûr ? Re-clique"));
            }
        }).bounds(gauche + 196, haut + 114, 94, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Voir la liste complète"),
                b -> minecraft.gui.setScreen(new ListeScreen(this)))
                .bounds(width / 2 - 145, haut + 174, 140, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Fermer"), b -> onClose())
                .bounds(width / 2 + 5, haut + 174, 100, 20).build());

        dernierAffiche = null;
        remplirDepuisConfig();
        envoyer("config", ""); // demande les réglages à jour au serveur
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
        if (!champTitre.isFocused()) champTitre.setValue(c.titre());
        if (!champLien.isFocused()) champLien.setValue(c.lien());
        boutonMasquer.setMessage(Component.literal(c.masque() ? "Afficher la liste" : "Masquer la liste"));
        boutonSelection.setMessage(Component.literal(c.selection() ? "Terminer la sélection" : "Choisir les coffres"));
        dernierAffiche = c;
    }

    @Override
    public void tick() {
        super.tick();
        remplirDepuisConfig();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        ConfigPayload c = ConfigRecue.get();

        String titre = "Chantier";
        g.text(font, titre, width / 2 - font.width(titre) / 2, haut, OR);
        g.text(font, "Titre", gauche, haut + 25, BLANC);
        g.text(font, "Lien", gauche, haut + 51, BLANC);
        g.text(font, "Coffres", gauche, haut + 82, BLANC);
        if (c != null) {
            String infoCoffres = c.coffres() == 0
                    ? "Aucun coffre choisi : clique sur « Choisir les coffres »."
                    : c.coffres() + " emplacements de coffre choisis.";
            g.text(font, infoCoffres, gauche + 50, haut + 100, GRIS);
        }


        if (c == null) {
            g.text(font, "En attente du serveur…", gauche, haut + 142, GRIS);
            return;
        }
        String etat = c.materiaux() + " matériaux · " + c.pourcent() + " %";
        g.text(font, etat, gauche, haut + 142, c.pourcent() >= 100 ? VERT : OR);
        if (!c.message().isEmpty()) {
            String msg = font.plainSubstrByWidth(c.message(), LARGEUR);
            g.text(font, msg, gauche, haut + 156, BLANC);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
