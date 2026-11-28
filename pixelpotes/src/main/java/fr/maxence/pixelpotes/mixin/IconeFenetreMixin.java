package fr.maxence.pixelpotes.mixin;

import com.mojang.blaze3d.platform.IconSet;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.server.packs.PackMetadataResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dès que le jeu pose son icône (pendant le chargement), on la remplace par le logo Pixel Potes. */
@Mixin(Window.class)
public class IconeFenetreMixin {
    @Inject(method = "setIcon(Lnet/minecraft/server/packs/PackMetadataResources;Lcom/mojang/blaze3d/platform/IconSet;)V", at = @At("TAIL"))
    private void pixelpotes$icone(PackMetadataResources ressources, IconSet icones, CallbackInfo ci) {
        fr.maxence.pixelpotes.Icone.appliquer();
    }
}
