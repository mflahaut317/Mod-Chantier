package fr.maxence.visage.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.maxence.visage.ImageEnvoi;
import fr.maxence.visage.Recu;
import fr.maxence.visage.Visage;
import java.util.Map;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Côté joueur : touche N pour la caméra, envoi des images, affichage sur les têtes. */
@Mod(value = Visage.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Visage.MODID, value = Dist.CLIENT)
public class VisageClient {
    static final Logger LOGGER = LoggerFactory.getLogger("Visage");

    private static final KeyMapping.Category CATEGORIE =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(Visage.MODID, "visage"));
    private static final KeyMapping CAMERA = new KeyMapping("key.visage.camera", InputConstants.KEY_N, CATEGORIE);

    private static long derniereAffichee = -1;
    private static String message = "";
    private static long messageJusqua = 0;

    @SubscribeEvent
    static void onTouches(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORIE);
        event.register(CAMERA);
    }

    @SubscribeEvent
    static void onCouches(EntityRenderersEvent.AddLayers event) {
        for (PlayerModelType type : event.getSkins()) {
            AvatarRenderer<AbstractClientPlayer> rendu = event.getPlayerRenderer(type);
            if (rendu != null) rendu.addLayer(new CoucheVisage(rendu));
        }
    }

    @SubscribeEvent
    static void onCalques(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Identifier.fromNamespaceAndPath(Visage.MODID, "apercu"), VisageClient::dessiner);
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        while (CAMERA.consumeClick()) {
            boolean avant = Camera.allumee();
            Camera.basculer();
            if (avant) {
                ClientPacketDistributor.sendToServer(new ImageEnvoi(new byte[0]));
                Textures.eteindre(mc.player.getId());
                afficher("Caméra éteinte");
            } else {
                afficher("Caméra allumée : tes potes proches te voient");
            }
        }

        if (!Camera.allumee() && !Camera.etat().isEmpty()) {
            afficher(Camera.etat());
            Camera.effacerEtat();
        }
        if (mc.player.tickCount % 200 == 0) Textures.nettoyer();
    }

    /** À chaque image affichée (et pas seulement 20 fois/s) : textures à jour, pour suivre les 25 images/s des caméras. */
    @SubscribeEvent
    static void onImage(RenderFrameEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        // Notre caméra, sur notre tête (vue F5). L'envoi au serveur se fait dans le fil de la caméra.
        Camera.Image image = Camera.derniere();
        if (image != null && image.numero() != derniereAffichee) {
            derniereAffichee = image.numero();
            Textures.mettreAJour(mc.player.getId(), image.pixels());
        }
        // Caméras des autres joueurs
        for (Map.Entry<Integer, byte[]> e : Recu.IMAGES.entrySet()) {
            Recu.IMAGES.remove(e.getKey(), e.getValue());
            if (e.getValue().length == 0) {
                Textures.eteindre(e.getKey());
            } else {
                int[] pixels = Textures.decoder(e.getValue());
                if (pixels != null) Textures.mettreAJour(e.getKey(), pixels);
            }
        }
    }

    @SubscribeEvent
    static void onDeconnexion(ClientPlayerNetworkEvent.LoggingOut event) {
        if (Camera.allumee()) Camera.basculer();
        Recu.IMAGES.clear();
        Textures.toutLiberer();
    }

    private static void afficher(String texte) {
        message = texte;
        messageJusqua = System.currentTimeMillis() + 3000;
    }

    /** En haut à droite : aperçu de sa propre caméra et point rouge « en direct ». */
    private static void dessiner(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int largeur = mc.getWindow().getGuiScaledWidth();
        if (Camera.allumee()) {
            int taille = 40, x = largeur - taille - 6, y = 6;
            Identifier moi = Textures.active(mc.player.getId());
            g.fill(x - 1, y - 1, x + taille + 1, y + taille + 1, 0xFFFF3B3B);
            if (moi != null) {
                g.blit(RenderPipelines.GUI_TEXTURED, moi, x, y, 0f, 0f, taille, taille,
                        Camera.TAILLE, Camera.TAILLE, Camera.TAILLE, Camera.TAILLE);
            } else {
                g.fill(x, y, x + taille, y + taille, 0xFF000000);
            }
            g.text(mc.font, "● direct", x, y + taille + 3, 0xFFFF5555, true);
        }
        if (System.currentTimeMillis() < messageJusqua) {
            int w = mc.font.width(message);
            int x = largeur / 2 - w / 2, y = 30;
            g.fill(x - 4, y - 3, x + w + 4, y + 11, 0xA0000000);
            g.text(mc.font, message, x, y, 0xFFFFFFFF, true);
        }
    }
}
