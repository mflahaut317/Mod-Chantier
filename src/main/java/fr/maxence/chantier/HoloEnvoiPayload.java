package fr.maxence.chantier;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Joueur -> serveur : un morceau du hologramme partagé avec le groupe (blocs compressés + position). */
public record HoloEnvoiPayload(String nom, int x, int y, int z, int rotation, String dimension,
                               int morceau, int total, byte[] donnees) implements CustomPacketPayload {
    public static final Type<HoloEnvoiPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Chantier.MODID, "holo_envoi"));

    public static final StreamCodec<ByteBuf, HoloEnvoiPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, p.nom());
                ByteBufCodecs.VAR_INT.encode(buf, p.x()); ByteBufCodecs.VAR_INT.encode(buf, p.y()); ByteBufCodecs.VAR_INT.encode(buf, p.z());
                ByteBufCodecs.VAR_INT.encode(buf, p.rotation());
                ByteBufCodecs.STRING_UTF8.encode(buf, p.dimension());
                ByteBufCodecs.VAR_INT.encode(buf, p.morceau()); ByteBufCodecs.VAR_INT.encode(buf, p.total());
                ByteBufCodecs.BYTE_ARRAY.encode(buf, p.donnees());
            },
            buf -> new HoloEnvoiPayload(ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.BYTE_ARRAY.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
