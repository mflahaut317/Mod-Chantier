package fr.maxence.chantier;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Serveur -> joueur : les blocs d'un projet de la galerie (compressés), pour l'aperçu 3D de sa fiche. */
public record VolumePayload(String id, byte[] donnees) implements CustomPacketPayload {
    public static final Type<VolumePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Chantier.MODID, "volume"));

    public static final StreamCodec<ByteBuf, VolumePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, VolumePayload::id,
            ByteBufCodecs.BYTE_ARRAY, VolumePayload::donnees,
            VolumePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
