package fr.maxence.chantier.client;

import fr.maxence.chantier.ActionPayload;
import fr.maxence.chantier.ConfigPayload;
import fr.maxence.chantier.ConfigRecue;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** D'où vient la liste du chantier : un schéma (galerie) ou une feuille Google Sheets. */
public class SourceScreen extends CadreScreen {
    private static final int LARGEUR = 290;

    private EditBox champLien;
    private ConfigPayload messageDepart;

    public SourceScreen(Screen parent) {
        super("Importer une liste ou un schéma", parent);
    }

    @Override
    protected void init() {
        cadre(LARGEUR + 24, 176);
        int x = px + 12;
        messageDepart = ConfigRecue.get();

        addRenderableWidget(Button.builder(Component.literal("Ouvrir la galerie"),
                b -> minecraft.gui.setScreen(new GalerieScreen(this))).bounds(x + 26, py + 40, 140, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Mes schémas"),
                b -> minecraft.gui.setScreen(new SchemaScreen(this))).bounds(x + 170, py + 40, 120, 20).build());

        String lien = champLien != null ? champLien.getValue() : ConfigRecue.get() == null ? "" : ConfigRecue.get().lien();
        champLien = new EditBox(font, x + 26, py + 106, 194, 18, Component.literal("Lien"));
        champLien.setMaxLength(512);
        champLien.setHint(Component.literal("Colle le lien Google Sheets"));
        champLien.setValue(lien);
        addRenderableWidget(champLien);
        addRenderableWidget(Button.builder(Component.literal("Charger"), b -> {
            messageDepart = ConfigRecue.get();
            ClientPacketDistributor.sendToServer(new ActionPayload("lien", champLien.getValue()));
        }).bounds(x + 225, py + 105, 65, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Retour"), b -> onClose())
                .bounds(width / 2 - 50, py + ph - 28, 100, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        int x = px + 12;
        icone(g, new ItemStack(Items.MAP), x, py + 12);
        texte(g, font, "Depuis un schéma", x + 26, py + 13, TEXTE);
        texte(g, font, ".litematic, .schem ou .nbt (ou glisse-le dans la fenêtre)", x + 26, py + 24, DOUX);

        g.fill(x, py + 70, x + LARGEUR, py + 71, CREUX);

        icone(g, new ItemStack(Items.PAPER), x, py + 78);
        texte(g, font, "Depuis Google Sheets", x + 26, py + 79, TEXTE);
        texte(g, font, "Une liste faite à la main : objet + quantité", x + 26, py + 90, DOUX);

        // Réponse du serveur après « Charger »
        ConfigPayload c = ConfigRecue.get();
        if (c != null && c != messageDepart && !c.message().isEmpty()) {
            texte(g, font, font.plainSubstrByWidth(c.message(), LARGEUR), x, py + 132, OR);
        }
    }

    /** Glisser un schéma ici l'importe aussi. */
    @Override
    public void onFilesDrop(java.util.List<java.nio.file.Path> fichiers) {
        SchemaScreen.deposer(fichiers);
    }
}
