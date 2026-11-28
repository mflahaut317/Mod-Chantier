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
 * Pixel Potes (côté joueur) : statut Discord pendant la partie ; quand on quitte le serveur (bouton « Se déconnecter »), le jeu se ferme
 * au lieu de revenir aux menus. Après une déconnexion forcée (serveur coupé, expulsion), l'écran d'erreur
 * reste affiché pour qu'on puisse le lire.
 */
@Mod(value = PixelPotes.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = PixelPotes.MODID, value = Dist.CLIENT)
public class PixelPotes {
    public static final String MODID = "pixelpotes";

    private static boolean enPartie = false;

    public PixelPotes() {
        Discord.demarrer();
    }

    @SubscribeEvent
    static void onConnexion(ClientPlayerNetworkEvent.LoggingIn event) {
        enPartie = true;
        Discord.enPartie = true;
        Discord.debutPartie = System.currentTimeMillis() / 1000;
    }

    @SubscribeEvent
    static void onDeconnexion(ClientPlayerNetworkEvent.LoggingOut event) {
        Discord.enPartie = false;
        Discord.debutPartie = 0;
    }

    /** Nombre de joueurs connectés, pour le statut Discord (toutes les 5 s). */
    @SubscribeEvent
    static void onTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.tickCount % 100 != 0) return;
        var connexion = mc.getConnection();
        if (connexion != null) Discord.joueurs = connexion.getOnlinePlayers().size();
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
