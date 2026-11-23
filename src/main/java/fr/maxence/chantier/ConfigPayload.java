package fr.maxence.chantier;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Paquet serveur -> joueur : réglages actuels du chantier, pour remplir l'écran.
 * @param ouvrir true pour demander au jeu d'ouvrir l'écran (commande /chantier sans argument)
 */
public record ConfigPayload(String titre, String lien, String coin1, String coin2,
                            int materiaux, int pourcent, boolean masque,
                            int coffres, boolean selection,
                            String message, boolean ouvrir) implements CustomPacketPayload {

    public static final Type<ConfigPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Chantier.MODID, "config"));

    public static final StreamCodec<ByteBuf, ConfigPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, p.titre());
                ByteBufCodecs.STRING_UTF8.encode(buf, p.lien());
                ByteBufCodecs.STRING_UTF8.encode(buf, p.coin1());
                ByteBufCodecs.STRING_UTF8.encode(buf, p.coin2());
                ByteBufCodecs.VAR_INT.encode(buf, p.materiaux());
                ByteBufCodecs.VAR_INT.encode(buf, p.pourcent());
                ByteBufCodecs.BOOL.encode(buf, p.masque());
                ByteBufCodecs.VAR_INT.encode(buf, p.coffres());
                ByteBufCodecs.BOOL.encode(buf, p.selection());
                ByteBufCodecs.STRING_UTF8.encode(buf, p.message());
                ByteBufCodecs.BOOL.encode(buf, p.ouvrir());
            },
            buf -> new ConfigPayload(
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
