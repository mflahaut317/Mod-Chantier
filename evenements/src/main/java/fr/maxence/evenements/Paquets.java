package fr.maxence.evenements;

import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Paquets entre le serveur et l'écran « Événement » des joueurs. */
public final class Paquets {
    private Paquets() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String nom) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Evenements.MODID, nom));
    }

    /** Serveur -> joueur : où en est l'événement (programme en JSON). ouvrir = ouvrir l'écran. */
    public record Etat(boolean ouvert, boolean organisateur, boolean dedans, boolean construction, boolean jeuEnCours,
                       List<String> participants, String programme, boolean ouvrirEcran) implements CustomPacketPayload {
        public static final Type<Etat> TYPE = Paquets.type("etat");
        public static final StreamCodec<ByteBuf, Etat> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Etat::ouvert,
                ByteBufCodecs.BOOL, Etat::organisateur,
                ByteBufCodecs.BOOL, Etat::dedans,
                ByteBufCodecs.BOOL, Etat::construction,
                ByteBufCodecs.BOOL, Etat::jeuEnCours,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Etat::participants,
                ByteBufCodecs.STRING_UTF8, Etat::programme,
                ByteBufCodecs.BOOL, Etat::ouvrirEcran,
                Etat::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Joueur -> serveur : « etat » (demande), ou une commande /event ou /cachecache à exécuter en son nom. */
    public record Action(String commande) implements CustomPacketPayload {
        public static final Type<Action> TYPE = Paquets.type("action");
        public static final StreamCodec<ByteBuf, Action> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Action::commande, Action::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
