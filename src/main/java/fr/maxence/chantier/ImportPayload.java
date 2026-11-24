package fr.maxence.chantier;

import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Paquet joueur -> serveur : liste de matériaux lue dans un schéma .litematic (requis = quantité). */
public record ImportPayload(String nom, List<EtatPayload.Ligne> lignes) implements CustomPacketPayload {

    public static final Type<ImportPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Chantier.MODID, "import"));

    public static final StreamCodec<ByteBuf, ImportPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ImportPayload::nom,
            EtatPayload.Ligne.CODEC.apply(ByteBufCodecs.list()), ImportPayload::lignes,
            ImportPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
