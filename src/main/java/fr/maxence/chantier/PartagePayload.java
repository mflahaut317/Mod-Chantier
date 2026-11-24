package fr.maxence.chantier;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Paquet joueur -> serveur : un schéma partagé dans la galerie commune. */
public record PartagePayload(GaleriePayload.Entree entree) implements CustomPacketPayload {

    public static final Type<PartagePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Chantier.MODID, "partage"));

    public static final StreamCodec<ByteBuf, PartagePayload> CODEC =
            GaleriePayload.Entree.CODEC.map(PartagePayload::new, PartagePayload::entree);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
