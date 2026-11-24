package fr.maxence.souvenirs;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

/** Journal du serveur : les moments marquants notés automatiquement, avec captures d'écran. */
@Mod(Souvenirs.MODID)
public class Souvenirs {
    public static final String MODID = "souvenirs";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Souvenirs(IEventBus modBus) {
        modBus.addListener(Souvenirs::reseau);
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> Journal.charger());
        NeoForge.EVENT_BUS.addListener(Evenements::onSucces);
        NeoForge.EVENT_BUS.addListener(Evenements::onMort);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> Journal.oublier(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener(Souvenirs::commandes);
    }

    private static void commandes(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("journal").executes(ctx -> {
            ServerPlayer joueur = ctx.getSource().getPlayer();
            if (joueur != null) Journal.envoyerListe(joueur, true);
            return 1;
        }));
    }

    /** "optional" : un joueur sans le mod peut quand même se connecter. Côté joueur on ne fait que stocker. */
    private static void reseau(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1").optional();
        r.playToClient(Paquets.Liste.TYPE, Paquets.Liste.CODEC, (p, c) -> Recu.liste(p));
        r.playToClient(Paquets.Photo.TYPE, Paquets.Photo.CODEC, (p, c) -> Recu.photo(p));
        r.playToServer(Paquets.Demande.TYPE, Paquets.Demande.CODEC, Journal::demande);
        r.playToServer(Paquets.Creer.TYPE, Paquets.Creer.CODEC, Journal::creer);
        r.playToServer(Paquets.PhotoEnvoi.TYPE, Paquets.PhotoEnvoi.CODEC, Journal::photoRecue);
    }
}
