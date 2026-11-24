package fr.maxence.chantier;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * Point d'entrée du mod "Chantier".
 * Côté serveur : lit la liste de matériaux, compte les coffres choisis et les inventaires, envoie l'état aux joueurs.
 * Côté client : voir {@link fr.maxence.chantier.client.ChantierClient} pour l'affichage.
 */
@Mod(Chantier.MODID)
public class Chantier {
    public static final String MODID = "chantier";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** Recompte toutes les 5 secondes (100 ticks) pour ménager le serveur. */
    private static final int INTERVALLE_TICKS = 100;
    private static int compteurTicks = 0;

    public Chantier(IEventBus modBus, ModContainer container) {
        modBus.addListener(Reseau::enregistrer);

        NeoForge.EVENT_BUS.addListener(Chantier::onCommandes);
        NeoForge.EVENT_BUS.addListener(Chantier::onServeurDemarre);
        NeoForge.EVENT_BUS.addListener(Chantier::onTick);
        NeoForge.EVENT_BUS.addListener(Chantier::onConnexion);
        NeoForge.EVENT_BUS.addListener(Actions::onClicDroit);
    }

    private static void onCommandes(RegisterCommandsEvent event) {
        Commandes.enregistrer(event.getDispatcher());
    }

    private static void onServeurDemarre(ServerStartedEvent event) {
        Chantiers.charger(event.getServer());
    }

    private static void onTick(ServerTickEvent.Post event) {
        if (++compteurTicks < INTERVALLE_TICKS) return;
        compteurTicks = 0;
        Chantiers.tick(event.getServer());
    }

    private static void onConnexion(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer joueur) {
            Chantiers.onConnexion(joueur);
        }
    }
}
