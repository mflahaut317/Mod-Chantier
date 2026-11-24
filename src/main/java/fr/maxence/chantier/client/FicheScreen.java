package fr.maxence.chantier.client;

import java.util.function.BiConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Fiche d'un projet de la galerie : intitulé + description (pour le partager ou le modifier). */
public class FicheScreen extends Screen {
    private static final int LARGEUR = 300;
    private static final int GRIS = 0xFFAAAAAA;
    private static final int OR = 0xFFFFD24A;
    private static final int TEXTE = 0xFFDDDDDD;

    private final Screen parent;
    private final String nomInitial;
    private final String descriptionInitiale;
    private final String libelleValider;
    private final BiConsumer<String, String> valider;
    private EditBox champNom;
    private EditBox champDescription;

    public FicheScreen(Screen parent, String titre, String nom, String description, String libelleValider,
                       BiConsumer<String, String> valider) {
        super(Component.literal(titre));
        this.parent = parent;
        this.nomInitial = nom;
        this.descriptionInitiale = description;
        this.libelleValider = libelleValider;
        this.valider = valider;
    }

    @Override
    protected void init() {
        int x = (width - LARGEUR) / 2;
        String nom = champNom != null ? champNom.getValue() : nomInitial;
        String desc = champDescription != null ? champDescription.getValue() : descriptionInitiale;

        champNom = new EditBox(font, x, 56, LARGEUR, 18, Component.literal("Intitulé"));
        champNom.setMaxLength(60);
        champNom.setValue(nom);
        addRenderableWidget(champNom);

        champDescription = new EditBox(font, x, 96, LARGEUR, 18, Component.literal("Description"));
        champDescription.setMaxLength(250);
        champDescription.setValue(desc);
        addRenderableWidget(champDescription);

        addRenderableWidget(Button.builder(Component.literal(libelleValider), b -> {
            String n = champNom.getValue().strip();
            if (n.isEmpty()) return;
            valider.accept(n, champDescription.getValue().strip());
            onClose();
        }).bounds(width / 2 - 105, height - 40, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Annuler"), b -> onClose())
                .bounds(width / 2 + 5, height - 40, 100, 20).build());
        setInitialFocus(champNom);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        int x = (width - LARGEUR) / 2;
        String titre = getTitle().getString();
        g.text(font, titre, width / 2 - font.width(titre) / 2, 20, OR);
        g.text(font, "Intitulé", x, 44, GRIS);
        g.text(font, "Description (ce que c'est, astuces…)", x, 84, GRIS);
        String compte = champDescription.getValue().length() + " / 250";
        g.text(font, compte, x + LARGEUR - font.width(compte), 84, GRIS);
        // Aperçu de la description telle qu'elle apparaîtra dans la galerie
        int y = 122;
        for (String ligne : GalerieScreen.couper(font, champDescription.getValue(), LARGEUR, 8)) {
            g.text(font, ligne, x, y, TEXTE);
            y += 10;
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
