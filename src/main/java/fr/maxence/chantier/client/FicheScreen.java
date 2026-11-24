package fr.maxence.chantier.client;

import java.util.function.BiConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Fiche d'un projet de la galerie : intitulé + description (pour le partager ou le modifier). */
public class FicheScreen extends CadreScreen {
    private static final int LARGEUR = 300;

    private final String nomInitial;
    private final String descriptionInitiale;
    private final String libelleValider;
    private final BiConsumer<String, String> valider;
    private EditBox champNom;
    private EditBox champDescription;

    public FicheScreen(Screen parent, String titre, String nom, String description, String libelleValider,
                       BiConsumer<String, String> valider) {
        super(titre, parent);
        this.nomInitial = nom;
        this.descriptionInitiale = description;
        this.libelleValider = libelleValider;
        this.valider = valider;
    }

    @Override
    protected void init() {
        cadre(LARGEUR + 24, 200);
        int x = px + 12;
        String nom = champNom != null ? champNom.getValue() : nomInitial;
        String desc = champDescription != null ? champDescription.getValue() : descriptionInitiale;

        champNom = new EditBox(font, x + 24, py + 24, LARGEUR - 24, 18, Component.literal("Intitulé"));
        champNom.setMaxLength(60);
        champNom.setValue(nom);
        addRenderableWidget(champNom);

        champDescription = new EditBox(font, x + 24, py + 64, LARGEUR - 24, 18, Component.literal("Description"));
        champDescription.setMaxLength(250);
        champDescription.setValue(desc);
        addRenderableWidget(champDescription);

        addRenderableWidget(Button.builder(Component.literal(libelleValider), b -> {
            String n = champNom.getValue().strip();
            if (n.isEmpty()) return;
            valider.accept(n, champDescription.getValue().strip());
            onClose();
        }).bounds(width / 2 - 105, py + ph - 28, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Annuler"), b -> onClose())
                .bounds(width / 2 + 5, py + ph - 28, 100, 20).build());
        setInitialFocus(champNom);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        int x = px + 12;
        texte(g, font, "Intitulé", x, py + 12, TEXTE);
        icone(g, new ItemStack(Items.NAME_TAG), x, py + 23);
        texte(g, font, "Description (ce que c'est, astuces…)", x, py + 52, TEXTE);
        String compte = champDescription.getValue().length() + " / 250";
        texte(g, font, compte, x + LARGEUR - font.width(compte), py + 52, DOUX);
        icone(g, new ItemStack(Items.WRITABLE_BOOK), x, py + 63);
        // Aperçu de la description telle qu'elle apparaîtra dans la galerie
        texte(g, font, "Aperçu :", x, py + 92, DOUX);
        int y = py + 104;
        for (String ligne : GalerieScreen.couper(font, champDescription.getValue(), LARGEUR, 6)) {
            texte(g, font, ligne, x, y, TEXTE);
            y += 10;
        }
    }
}
