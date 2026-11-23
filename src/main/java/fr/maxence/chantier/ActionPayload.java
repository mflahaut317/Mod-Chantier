package fr.maxence.chantier;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Paquet joueur -> serveur : un bouton de l'écran Chantier a été cliqué.
 * action : "config", "lien", "titre", "coin1", "coin2", "actualiser", "masquer", "afficher", "arreter".
 * valeur : texte associé (lien, titre), vide sinon.
 */
public record ActionPayload(String action, String valeur) implements CustomPacketPayload {

    public static final Type<ActionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Chantier.MODID, "action"));

    public static final StreamCodec<ByteBuf, ActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ActionPayload::action,
            ByteBufCodecs.STRING_UTF8, ActionPayload::valeur,
            ActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
