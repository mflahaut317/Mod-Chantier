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

    public record Entree(String id, String nom, String auteur, int largeur, int hauteur, byte[] apercu,
                         List<EtatPayload.Ligne> lignes) {
        public static final StreamCodec<ByteBuf, Entree> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Entree::id,
                ByteBufCodecs.STRING_UTF8, Entree::nom,
                ByteBufCodecs.STRING_UTF8, Entree::auteur,
                ByteBufCodecs.VAR_INT, Entree::largeur,
                ByteBufCodecs.VAR_INT, Entree::hauteur,
                ByteBufCodecs.BYTE_ARRAY, Entree::apercu,
                EtatPayload.Ligne.CODEC.apply(ByteBufCodecs.list()), Entree::lignes,
                Entree::new);
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
