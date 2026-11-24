package fr.maxence.chantier;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Paquet joueur -> serveur : un schéma partagé dans la galerie commune, avec ses blocs (compressés) pour la 3D. */
public record PartagePayload(GaleriePayload.Entree entree, byte[] volume) implements CustomPacketPayload {

    public static final Type<PartagePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Chantier.MODID, "partage"));

    public static final StreamCodec<ByteBuf, PartagePayload> CODEC = StreamCodec.composite(
            GaleriePayload.Entree.CODEC, PartagePayload::entree,
            ByteBufCodecs.BYTE_ARRAY, PartagePayload::volume,
            PartagePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
