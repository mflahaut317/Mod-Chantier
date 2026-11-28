package fr.maxence.pixelpotes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Pixel Potes (côté joueur) : quand on quitte le serveur (bouton « Se déconnecter »), le jeu se ferme
 * au lieu de revenir aux menus. Après une déconnexion forcée (serveur coupé, expulsion), l'écran d'erreur
 * reste affiché pour qu'on puisse le lire.
 */
@Mod(value = PixelPotes.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = PixelPotes.MODID, value = Dist.CLIENT)
public class PixelPotes {
    public static final String MODID = "pixelpotes";

    private static boolean enPartie = false;

    @SubscribeEvent
    static void onConnexion(ClientPlayerNetworkEvent.LoggingIn event) {
        enPartie = true;
    }

    @SubscribeEvent
    static void onEcran(ScreenEvent.Opening event) {
        if (!enPartie) return;
        if (event.getNewScreen() instanceof TitleScreen || event.getNewScreen() instanceof JoinMultiplayerScreen) {
            enPartie = false;
            Minecraft.getInstance().stop();
        }
    }
}
