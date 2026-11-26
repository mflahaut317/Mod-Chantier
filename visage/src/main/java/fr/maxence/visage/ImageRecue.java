package fr.maxence.visage;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Serveur -> joueurs proches : l'image de la webcam d'un joueur (identifié par son numéro d'entité). Vide = caméra éteinte. */
public record ImageRecue(int entite, byte[] jpeg) implements CustomPacketPayload {
    public static final Type<ImageRecue> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Visage.MODID, "image_recue"));
    public static final StreamCodec<ByteBuf, ImageRecue> CODEC = StreamCodec.of(
            (buf, p) -> {
                ByteBufCodecs.VAR_INT.encode(buf, p.entite());
                ByteBufCodecs.byteArray(Visage.TAILLE_MAX).encode(buf, p.jpeg());
            },
            buf -> new ImageRecue(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.byteArray(Visage.TAILLE_MAX).decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
