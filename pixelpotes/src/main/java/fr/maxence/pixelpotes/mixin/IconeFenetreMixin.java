package fr.maxence.pixelpotes.mixin;

import com.mojang.blaze3d.platform.IconSet;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.server.packs.PackMetadataResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fenêtre du jeu :
 * - dès que le jeu pose son icône (pendant le chargement), on la remplace par le logo Pixel Potes ;
 * - au passage de relais de l'écran de chargement NeoForge, le redimensionnement arrive avant que l'interface
 *   du jeu existe : FancyMenu planterait (« this.gui is null »), on l'ignore donc à ce moment-là.
 */
@Mixin(Window.class)
public class IconeFenetreMixin {
    @Inject(method = "setIcon(Lnet/minecraft/server/packs/PackMetadataResources;Lcom/mojang/blaze3d/platform/IconSet;)V", at = @At("TAIL"))
    private void pixelpotes$icone(PackMetadataResources ressources, IconSet icones, CallbackInfo ci) {
        fr.maxence.pixelpotes.Icone.appliquer();
    }

    @Inject(method = "onFramebufferResize", at = @At("HEAD"), cancellable = true)
    private void pixelpotes$redimensionnementTropTot(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null) ci.cancel();
    }
}
