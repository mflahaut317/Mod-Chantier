package fr.maxence.visage;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Visage : la webcam de chaque joueur affichée sur la tête de son personnage.
 * Le serveur ne fait que relayer les images (petites JPEG) aux joueurs qui voient l'émetteur.
 */
@Mod(Visage.MODID)
public class Visage {
    public static final String MODID = "visage";
    /** Taille maximale d'une image (octets). */
    public static final int TAILLE_MAX = 24_000;
    /** Au plus une image toutes les 60 ms par joueur (~15 images/s). */
    private static final long INTERVALLE_MIN = 60;

    private static final Map<UUID, Long> DERNIER_ENVOI = new ConcurrentHashMap<>();

    public Visage(IEventBus modBus) {
        modBus.addListener(Visage::reseau);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> DERNIER_ENVOI.remove(e.getEntity().getUUID()));
    }

    private static void reseau(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToServer(ImageEnvoi.TYPE, ImageEnvoi.CODEC, Visage::relayer);
        registrar.playToClient(ImageRecue.TYPE, ImageRecue.CODEC,
                (paquet, contexte) -> Recu.IMAGES.put(paquet.entite(), paquet.jpeg()));
    }

    private static void relayer(ImageEnvoi paquet, IPayloadContext contexte) {
        if (!(contexte.player() instanceof ServerPlayer joueur)) return;
        byte[] jpeg = paquet.jpeg();
        if (jpeg.length > TAILLE_MAX) return;
        long maintenant = System.currentTimeMillis();
        if (jpeg.length > 0) {
            Long avant = DERNIER_ENVOI.get(joueur.getUUID());
            if (avant != null && maintenant - avant < INTERVALLE_MIN) return;
        }
        DERNIER_ENVOI.put(joueur.getUUID(), maintenant);
        PacketDistributor.sendToPlayersTrackingEntity(joueur, new ImageRecue(joueur.getId(), jpeg));
    }
}
