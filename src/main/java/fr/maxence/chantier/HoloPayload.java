package fr.maxence.chantier;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Serveur -> joueur : le hologramme du chantier.
 * total = -1 : retiré ; total = 0 : nouvelle position seulement ; sinon un morceau des blocs compressés.
 */
public record HoloPayload(int version, String nom, int x, int y, int z, int rotation, String dimension,
                          int morceau, int total, byte[] donnees) implements CustomPacketPayload {
    public static final Type<HoloPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Chantier.MODID, "holo"));

    public static final StreamCodec<ByteBuf, HoloPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                ByteBufCodecs.VAR_INT.encode(buf, p.version());
                ByteBufCodecs.STRING_UTF8.encode(buf, p.nom());
                ByteBufCodecs.VAR_INT.encode(buf, p.x()); ByteBufCodecs.VAR_INT.encode(buf, p.y()); ByteBufCodecs.VAR_INT.encode(buf, p.z());
                ByteBufCodecs.VAR_INT.encode(buf, p.rotation());
                ByteBufCodecs.STRING_UTF8.encode(buf, p.dimension());
                ByteBufCodecs.VAR_INT.encode(buf, p.morceau()); ByteBufCodecs.VAR_INT.encode(buf, p.total());
                ByteBufCodecs.BYTE_ARRAY.encode(buf, p.donnees());
            },
            buf -> new HoloPayload(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.BYTE_ARRAY.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
