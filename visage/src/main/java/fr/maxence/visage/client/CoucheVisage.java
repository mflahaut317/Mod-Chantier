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
        pile.pushPose();
        getParentModel().head.translateAndRotate(pile);
        collecteur.submitCustomGeometry(pile, RenderTypes.entityCutout(texture), (pose, v) -> {
            sommet(v, pose, -DEMI, HAUT, 0f, 0f, lumiere);
            sommet(v, pose, -DEMI, BAS, 0f, 1f, lumiere);
            sommet(v, pose, DEMI, BAS, 1f, 1f, lumiere);
            sommet(v, pose, DEMI, HAUT, 1f, 0f, lumiere);
        });
        pile.popPose();
    }

    private static void sommet(VertexConsumer v, PoseStack.Pose pose, float x, float y, float u, float vv, int lumiere) {
        v.addVertex(pose, x, y, Z)
                .setColor(0xFFFFFFFF)
                .setUv(u, vv)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(lumiere)
                .setNormal(pose, 0f, 0f, -1f);
    }
}
