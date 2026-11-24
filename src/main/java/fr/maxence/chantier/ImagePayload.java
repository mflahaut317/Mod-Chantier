package fr.maxence.chantier;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Paquet joueur -> serveur : une image (RGB compressé) qui remplace l'aperçu d'un projet de la galerie. */
public record ImagePayload(String id, int largeur, int hauteur, byte[] image) implements CustomPacketPayload {

    public static final Type<ImagePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Chantier.MODID, "image"));

    public static final StreamCodec<ByteBuf, ImagePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ImagePayload::id,
            ByteBufCodecs.VAR_INT, ImagePayload::largeur,
            ByteBufCodecs.VAR_INT, ImagePayload::hauteur,
            ByteBufCodecs.BYTE_ARRAY, ImagePayload::image,
            ImagePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
