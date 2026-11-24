package fr.maxence.souvenirs.client;

import fr.maxence.souvenirs.Paquets;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Écrire un souvenir à la main ; l'icône est l'objet tenu en main (sinon un livre). */
public class NouveauScreen extends Screen {
    private static final int LARGEUR = 300;

    private final Screen parent;
    private EditBox titre;
    private EditBox texte;

    public NouveauScreen(Screen parent) {
        super(Component.literal("Nouveau souvenir"));
        this.parent = parent;
    }

    private ItemStack icone() {
        ItemStack main = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getMainHandItem();
        return main.isEmpty() ? new ItemStack(Items.WRITABLE_BOOK) : main;
    }

    @Override
    protected void init() {
        int x = (width - LARGEUR) / 2;
        titre = addRenderableWidget(new EditBox(font, x, 70, LARGEUR, 18, Component.literal("Titre")));
        titre.setMaxLength(80);
        titre.setHint(Component.literal("Ex. Inauguration de la base"));
        texte = addRenderableWidget(new EditBox(font, x, 110, LARGEUR, 18, Component.literal("Texte")));
        texte.setMaxLength(250);
        texte.setHint(Component.literal("Ce qui s'est passé…"));
        addRenderableWidget(Button.builder(Component.literal("Ajouter au journal"), b -> {
            if (titre.getValue().isBlank()) return;
            String id = BuiltInRegistries.ITEM.getKey(icone().getItem()).toString();
            ClientPacketDistributor.sendToServer(new Paquets.Creer(titre.getValue(), texte.getValue(), id));
            onClose();
        }).bounds(width / 2 - 125, 150, 120, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Annuler"), b -> onClose())
                .bounds(width / 2 + 5, 150, 120, 20).build());
        setInitialFocus(titre);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        int x = (width - LARGEUR) / 2;
        g.text(font, "Nouveau souvenir", width / 2 - font.width("Nouveau souvenir") / 2, 20, 0xFFFFD24A);
        g.item(icone(), x, 36);
        g.text(font, "Icône : l'objet que tu tiens en main", x + 20, 40, 0xFFAAAAAA);
        g.text(font, "Titre", x, 60, 0xFFFFFFFF);
        g.text(font, "Texte (facultatif)", x, 100, 0xFFFFFFFF);
        g.text(font, "Tu pourras ajouter une capture ensuite, depuis le journal.", x, 180, 0xFFAAAAAA);
    }

    @Override
    public void onClose() { minecraft.gui.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
