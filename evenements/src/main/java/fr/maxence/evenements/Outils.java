package fr.maxence.evenements;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

/** Petits raccourcis : commandes vanilla lancées par le serveur, titres à l'écran, messages. */
final class Outils {
    private Outils() {}

    /** Lance une commande vanilla en tant que serveur, sans rien écrire dans le chat. */
    static void commande(MinecraftServer serveur, String commande) {
        serveur.getCommands().performPrefixedCommand(serveur.createCommandSourceStack().withSuppressedOutput(), commande);
    }

    /** Texte entre guillemets pour une commande (les guillemets du texte sont échappés). */
    static String texte(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    static void titre(ServerPlayer joueur, String titre, String sousTitre) {
        joueur.connection.send(new ClientboundSetTitlesAnimationPacket(5, 50, 15));
        joueur.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(sousTitre)));
        joueur.connection.send(new ClientboundSetTitleTextPacket(Component.literal(titre)));
    }

    static void titre(Collection<ServerPlayer> joueurs, String titre, String sousTitre) {
        for (ServerPlayer j : joueurs) titre(j, titre, sousTitre);
    }

    static void son(MinecraftServer serveur, ServerPlayer joueur, String son) {
        commande(serveur, "playsound " + son + " master " + joueur.getScoreboardName() + " ~ ~ ~ 1 1");
    }

    static void message(Collection<ServerPlayer> joueurs, String texte) {
        for (ServerPlayer j : joueurs) j.sendSystemMessage(Component.literal(texte));
    }
}
