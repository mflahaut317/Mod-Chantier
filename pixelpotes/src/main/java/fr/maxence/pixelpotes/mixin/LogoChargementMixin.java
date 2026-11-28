package fr.maxence.pixelpotes.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.MipmapStrategy;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStream;

/**
 * Écran de chargement : le logo « Mojang Studios » est remplacé par celui de Pixel Potes.
 * L'image est coupée en deux : la moitié haute est la partie gauche du logo, la moitié basse la partie droite
 * (le jeu dessine les deux moitiés côte à côte).
 */
@Mixin(targets = "net.minecraft.client.gui.screens.LoadingOverlay$LogoTexture")
public class LogoChargementMixin {
    @Inject(method = "loadContents", at = @At("HEAD"), cancellable = true)
    private void pixelpotes$logo(ResourceManager ressources, CallbackInfoReturnable<TextureContents> retour) {
        try (InputStream in = LogoChargementMixin.class.getResourceAsStream("/assets/pixelpotes/textures/gui/logo_chargement.png")) {
            if (in == null) return;
            retour.setReturnValue(new TextureContents(NativeImage.read(in),
                    new TextureMetadataSection(true, true, MipmapStrategy.MEAN, 0f)));
        } catch (Exception e) {
            // en cas de souci, le logo d'origine reste affiché
        }
    }
}
