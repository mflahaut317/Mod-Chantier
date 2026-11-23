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
 * Écran Chantier : lien de la feuille, titre, zone des coffres, affichage.
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
        haut = Math.max(10, height / 2 - 100);
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

        // Zone des coffres
        addRenderableWidget(Button.builder(Component.literal("Coin 1 ici"),
                b -> envoyer("coin1", "")).bounds(x, haut + 76, 117, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Coin 2 ici"),
                b -> envoyer("coin2", "")).bounds(x + 123, haut + 76, 117, 20).build());

        // Actions
        addRenderableWidget(Button.builder(Component.literal("Relire la feuille"),
                b -> envoyer("actualiser", "")).bounds(gauche, haut + 122, 94, 20).build());
        boutonMasquer = addRenderableWidget(Button.builder(Component.literal("Masquer la liste"),
                b -> envoyer(estMasque() ? "afficher" : "masquer", "")).bounds(gauche + 98, haut + 122, 94, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Arrêter"), b -> {
            if (confirmerArret) {
                envoyer("arreter", "");
                confirmerArret = false;
                b.setMessage(Component.literal("Arrêter"));
            } else {
                confirmerArret = true;
                b.setMessage(Component.literal("Sûr ? Re-clique"));
            }
        }).bounds(gauche + 196, haut + 122, 94, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Fermer"), b -> onClose())
                .bounds(width / 2 - 50, haut + 186, 100, 20).build());

        dernierAffiche = null;
        remplirDepuisConfig();
        envoyer("config", ""); // demande les réglages à jour au serveur
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
        g.text(font, "Zone", gauche, haut + 82, BLANC);

        String coin1 = c == null || c.coin1().isEmpty() ? "—" : c.coin1();
        String coin2 = c == null || c.coin2().isEmpty() ? "—" : c.coin2();
        g.text(font, "Coin 1 : " + coin1 + "     Coin 2 : " + coin2, gauche + 50, haut + 102, GRIS);

        if (c == null) {
            g.text(font, "En attente du serveur…", gauche, haut + 152, GRIS);
            return;
        }
        String etat = c.materiaux() + " matériaux · " + c.pourcent() + " %";
        g.text(font, etat, gauche, haut + 152, c.pourcent() >= 100 ? VERT : OR);
        if (!c.message().isEmpty()) {
            String msg = font.plainSubstrByWidth(c.message(), LARGEUR);
            g.text(font, msg, gauche, haut + 166, BLANC);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
