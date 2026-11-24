package fr.maxence.souvenirs.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.maxence.souvenirs.Paquets;
import fr.maxence.souvenirs.Recu;
import fr.maxence.souvenirs.Souvenirs;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Côté joueur : touche J, bouton dans l'inventaire, ouverture du journal. */
@Mod(value = Souvenirs.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Souvenirs.MODID, value = Dist.CLIENT)
public class SouvenirsClient {
    static final KeyMapping.Category CATEGORIE = new KeyMapping.Category(Identifier.fromNamespaceAndPath(Souvenirs.MODID, "souvenirs"));
    static final KeyMapping OUVRIR = new KeyMapping("key.souvenirs.journal", InputConstants.KEY_J, CATEGORIE);

    public SouvenirsClient() {}

    @SubscribeEvent
    static void onTouches(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORIE);
        event.register(OUVRIR);
    }

    static void ouvrir() {
        ClientPacketDistributor.sendToServer(new Paquets.Demande("liste", ""));
        Minecraft.getInstance().gui.setScreen(new JournalScreen());
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (OUVRIR.consumeClick()) if (mc.player != null) ouvrir();
        if (Recu.ouvrir) { // commande /journal
            Recu.ouvrir = false;
            mc.gui.setScreen(new JournalScreen());
        }
    }

    /** Bouton « Journal » sur le bord gauche de l'inventaire (sous celui de Chantier). */
    @SubscribeEvent
    static void onEcranOuvert(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen ecran)) return;
        event.addListener(Button.builder(Component.literal("Journal"), b -> ouvrir())
                .bounds(4, ecran.height / 2 + 14, 56, 20).build());
    }
}
