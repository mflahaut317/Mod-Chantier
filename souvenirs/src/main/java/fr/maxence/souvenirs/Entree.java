package fr.maxence.souvenirs;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Un souvenir du journal.
 * titre / texte : texte Minecraft en JSON (pour que les noms de succès et les messages de mort soient traduits chez chacun).
 * type : exploit, mort, boss, succes, chantier, perso.
 */
public record Entree(String id, long date, String joueur, String type, String icone,
                     String titre, String texte, String lieu, boolean image) {
    public static final StreamCodec<ByteBuf, Entree> CODEC = StreamCodec.of(
            (buf, e) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, e.id());
                ByteBufCodecs.VAR_LONG.encode(buf, e.date());
                ByteBufCodecs.STRING_UTF8.encode(buf, e.joueur());
                ByteBufCodecs.STRING_UTF8.encode(buf, e.type());
                ByteBufCodecs.STRING_UTF8.encode(buf, e.icone());
                ByteBufCodecs.STRING_UTF8.encode(buf, e.titre());
                ByteBufCodecs.STRING_UTF8.encode(buf, e.texte());
                ByteBufCodecs.STRING_UTF8.encode(buf, e.lieu());
                ByteBufCodecs.BOOL.encode(buf, e.image());
            },
            buf -> new Entree(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf)));
}
