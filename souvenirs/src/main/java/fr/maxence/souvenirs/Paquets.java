package fr.maxence.souvenirs;

import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Tous les paquets du mod. */
public final class Paquets {
    private Paquets() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String nom) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Souvenirs.MODID, nom));
    }

    /** Serveur -> joueur : tout le journal (ouvrir = ouvrir l'écran, pour /journal). */
    public record Liste(List<Entree> entrees, boolean ouvrir) implements CustomPacketPayload {
        public static final Type<Liste> TYPE = type("liste");
        public static final StreamCodec<ByteBuf, Liste> CODEC = StreamCodec.composite(
                Entree.CODEC.apply(ByteBufCodecs.list()), Liste::entrees,
                ByteBufCodecs.BOOL, Liste::ouvrir,
                Liste::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Joueur -> serveur : liste, image:<id>, supprimer:<id>. */
    public record Demande(String action, String valeur) implements CustomPacketPayload {
        public static final Type<Demande> TYPE = type("demande");
        public static final StreamCodec<ByteBuf, Demande> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Demande::action,
                ByteBufCodecs.STRING_UTF8, Demande::valeur,
                Demande::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Joueur -> serveur : un souvenir écrit à la main. */
    public record Creer(String titre, String texte, String icone) implements CustomPacketPayload {
        public static final Type<Creer> TYPE = type("creer");
        public static final StreamCodec<ByteBuf, Creer> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Creer::titre,
                ByteBufCodecs.STRING_UTF8, Creer::texte,
                ByteBufCodecs.STRING_UTF8, Creer::icone,
                Creer::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Joueur -> serveur : un morceau de capture d'écran (JPEG) pour le souvenir id. */
    public record PhotoEnvoi(String id, int numero, int total, byte[] donnees) implements CustomPacketPayload {
        public static final Type<PhotoEnvoi> TYPE = type("photo_envoi");
        public static final StreamCodec<ByteBuf, PhotoEnvoi> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, PhotoEnvoi::id,
                ByteBufCodecs.VAR_INT, PhotoEnvoi::numero,
                ByteBufCodecs.VAR_INT, PhotoEnvoi::total,
                ByteBufCodecs.BYTE_ARRAY, PhotoEnvoi::donnees,
                PhotoEnvoi::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Serveur -> joueur : un morceau de la capture d'écran du souvenir id. */
    public record Photo(String id, int numero, int total, byte[] donnees) implements CustomPacketPayload {
        public static final Type<Photo> TYPE = type("photo");
        public static final StreamCodec<ByteBuf, Photo> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Photo::id,
                ByteBufCodecs.VAR_INT, Photo::numero,
                ByteBufCodecs.VAR_INT, Photo::total,
                ByteBufCodecs.BYTE_ARRAY, Photo::donnees,
                Photo::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
