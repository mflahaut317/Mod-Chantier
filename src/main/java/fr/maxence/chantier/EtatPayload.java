package fr.maxence.chantier;

import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Paquet serveur -> client : titre du chantier et liste des matériaux (requis / en stock). */
/** lignes : la liste du chantier ; recolte : matières premières à récolter (requis = quantité, possede = 0). */
public record EtatPayload(String titre, List<Ligne> lignes, List<Ligne> recolte,
                          String dimension, List<Integer> coffres) implements CustomPacketPayload {

    public record Ligne(String item, int requis, int possede) {
        public static final StreamCodec<ByteBuf, Ligne> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Ligne::item,
                ByteBufCodecs.VAR_INT, Ligne::requis,
                ByteBufCodecs.VAR_INT, Ligne::possede,
                Ligne::new);
    }

    public static final Type<EtatPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Chantier.MODID, "etat"));

    public static final StreamCodec<ByteBuf, EtatPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, EtatPayload::titre,
            Ligne.CODEC.apply(ByteBufCodecs.list()), EtatPayload::lignes,
            Ligne.CODEC.apply(ByteBufCodecs.list()), EtatPayload::recolte,
            ByteBufCodecs.STRING_UTF8, EtatPayload::dimension,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), EtatPayload::coffres,
            EtatPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
