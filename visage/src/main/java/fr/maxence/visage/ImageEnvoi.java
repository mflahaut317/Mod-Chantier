package fr.maxence.visage;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Joueur -> serveur : une image de sa webcam (JPEG). Vide = caméra éteinte. */
public record ImageEnvoi(byte[] jpeg) implements CustomPacketPayload {
    public static final Type<ImageEnvoi> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Visage.MODID, "image_envoi"));
    public static final StreamCodec<ByteBuf, ImageEnvoi> CODEC = StreamCodec.of(
            (buf, p) -> ByteBufCodecs.byteArray(Visage.TAILLE_MAX).encode(buf, p.jpeg()),
            buf -> new ImageEnvoi(ByteBufCodecs.byteArray(Visage.TAILLE_MAX).decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
