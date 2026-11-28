package fr.maxence.evenements.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.maxence.evenements.Evenements;
import fr.maxence.evenements.Paquets;
import fr.maxence.evenements.Recu;
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

/** Côté joueur : touche O, bouton « Événement » dans l'inventaire, commande /event. */
@Mod(value = Evenements.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Evenements.MODID, value = Dist.CLIENT)
public class EvenementsClient {
    static final KeyMapping.Category CATEGORIE = new KeyMapping.Category(Identifier.fromNamespaceAndPath(Evenements.MODID, "evenements"));
    static final KeyMapping OUVRIR = new KeyMapping("key.evenements.menu", InputConstants.KEY_O, CATEGORIE);

    public EvenementsClient() {}

    @SubscribeEvent
    static void onTouches(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORIE);
        event.register(OUVRIR);
    }

    static void envoyer(String commande) {
        ClientPacketDistributor.sendToServer(new Paquets.Action(commande));
    }

    static void ouvrir() {
        envoyer("etat");
        Minecraft.getInstance().gui.setScreen(new EvenementScreen());
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (OUVRIR.consumeClick()) if (mc.player != null) ouvrir();
        if (Recu.ouvrir) { // commande /event
            Recu.ouvrir = false;
            if (!(mc.gui.screen() instanceof EvenementScreen)) mc.gui.setScreen(new EvenementScreen());
        }
    }

    /** Bouton « Événement » sur le bord gauche de l'inventaire (sous ceux de Chantier et du Journal). */
    @SubscribeEvent
    static void onEcranOuvert(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen ecran)) return;
        event.addListener(Button.builder(Component.literal("Événement"), b -> ouvrir())
                .bounds(4, ecran.height / 2 + 38, 56, 20).build());
    }
}
