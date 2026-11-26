package fr.maxence.visage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Dessine la webcam sur la face avant de la tête du joueur (elle suit les mouvements de la tête).
 * Repère du modèle : unités de 1/16 de bloc, y vers le bas, visage tourné vers -z.
 */
final class CoucheVisage extends RenderLayer<AvatarRenderState, PlayerModel> {
    /** Juste devant le chapeau (couche externe de la peau, gonflée de 0,5 pixel). */
    private static final float Z = -4.6f / 16f;
    private static final float DEMI = 4.4f / 16f;
    private static final float HAUT = -8.4f / 16f, BAS = 0.4f / 16f;

    CoucheVisage(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack pile, SubmitNodeCollector collecteur, int lumiere, AvatarRenderState etat, float yRot, float xRot) {
        if (etat.isInvisible || etat.isSpectator) return;
        Identifier texture = Textures.active(etat.id);
        if (texture == null) return;
        if (Reglages.auDessus) {
            auDessus(pile, collecteur, texture);
            return;
        }
        pile.pushPose();
        getParentModel().head.translateAndRotate(pile);
        collecteur.submitCustomGeometry(pile, RenderTypes.entityCutout(texture), (pose, v) -> {
            sommet(v, pose, -DEMI, HAUT, Z, 0f, 0f, lumiere);
            sommet(v, pose, -DEMI, BAS, Z, 0f, 1f, lumiere);
            sommet(v, pose, DEMI, BAS, Z, 1f, 1f, lumiere);
            sommet(v, pose, DEMI, HAUT, Z, 1f, 0f, lumiere);
        });
        pile.popPose();
    }

    /** Hauteur du rond : environ 2,8 blocs au-dessus des pieds (au-dessus du pseudo). Le modèle a son origine à 1,5 bloc, y vers le bas. */
    private static final float HAUTEUR_MODELE = -1.3f;
    private static final float RAYON = 0.35f;
    private static final int SEGMENTS = 32;
    private static final int PLEINE_LUMIERE = 0xF000F0;

    /** Rond face à la caméra, au-dessus du joueur, toujours éclairé (visible la nuit). */
    private static void auDessus(PoseStack pile, SubmitNodeCollector collecteur, Identifier texture) {
        org.joml.Vector3f centre = pile.last().pose().transformPosition(0f, HAUTEUR_MODELE, 0f, new org.joml.Vector3f());
        org.joml.Quaternionf orientation = new org.joml.Quaternionf(
                net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
        pile.pushPose();
        pile.last().pose().identity().translate(centre).rotate(orientation);
        pile.last().normal().identity().rotate(orientation);
        collecteur.submitCustomGeometry(pile, RenderTypes.entityCutout(texture), (pose, v) -> {
            // Disque découpé en quadrilatères (centre, centre, bord, bord suivant)
            for (int i = 0; i < SEGMENTS; i++) {
                double a1 = 2 * Math.PI * i / SEGMENTS, a2 = 2 * Math.PI * (i + 1) / SEGMENTS;
                float x1 = (float) Math.cos(a1), y1 = (float) Math.sin(a1);
                float x2 = (float) Math.cos(a2), y2 = (float) Math.sin(a2);
                sommet(v, pose, 0f, 0f, 0f, 0.5f, 0.5f, PLEINE_LUMIERE);
                sommet(v, pose, 0f, 0f, 0f, 0.5f, 0.5f, PLEINE_LUMIERE);
                sommet(v, pose, x2 * RAYON, y2 * RAYON, 0f, 0.5f + x2 / 2, 0.5f - y2 / 2, PLEINE_LUMIERE);
                sommet(v, pose, x1 * RAYON, y1 * RAYON, 0f, 0.5f + x1 / 2, 0.5f - y1 / 2, PLEINE_LUMIERE);
            }
        });
        pile.popPose();
    }

    private static void sommet(VertexConsumer v, PoseStack.Pose pose, float x, float y, float z, float u, float vv, int lumiere) {
        v.addVertex(pose, x, y, z)
                .setColor(0xFFFFFFFF)
                .setUv(u, vv)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(lumiere)
                .setNormal(pose, 0f, 0f, z == 0f ? 1f : -1f);
    }
}
