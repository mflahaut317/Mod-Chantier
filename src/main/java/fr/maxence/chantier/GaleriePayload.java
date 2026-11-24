package fr.maxence.chantier;

import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Galerie commune du serveur.
 * Joueur -> serveur : un schéma partagé (une seule entrée, id vide).
 * Serveur -> joueur : toutes les entrées.
 * apercu : vue du dessus en RGB (3 octets par pixel, largeur x hauteur ; noir 0,0,0 = vide).
 */
public record GaleriePayload(List<Entree> entrees) implements CustomPacketPayload {

    public record Entree(String id, String nom, String auteur, String description, int largeur, int hauteur,
                         byte[] apercu, List<EtatPayload.Ligne> lignes) {
        private static final StreamCodec<ByteBuf, List<EtatPayload.Ligne>> LIGNES =
                EtatPayload.Ligne.CODEC.apply(ByteBufCodecs.list());

        public static final StreamCodec<ByteBuf, Entree> CODEC = StreamCodec.of(
                (buf, e) -> {
                    ByteBufCodecs.STRING_UTF8.encode(buf, e.id());
                    ByteBufCodecs.STRING_UTF8.encode(buf, e.nom());
                    ByteBufCodecs.STRING_UTF8.encode(buf, e.auteur());
                    ByteBufCodecs.STRING_UTF8.encode(buf, e.description());
                    ByteBufCodecs.VAR_INT.encode(buf, e.largeur());
                    ByteBufCodecs.VAR_INT.encode(buf, e.hauteur());
                    ByteBufCodecs.BYTE_ARRAY.encode(buf, e.apercu());
                    LIGNES.encode(buf, e.lignes());
                },
                buf -> new Entree(
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.BYTE_ARRAY.decode(buf),
                        LIGNES.decode(buf)));
    }

    public static final Type<GaleriePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Chantier.MODID, "galerie"));

    public static final StreamCodec<ByteBuf, GaleriePayload> CODEC = StreamCodec.composite(
            Entree.CODEC.apply(ByteBufCodecs.list()), GaleriePayload::entrees,
            GaleriePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
