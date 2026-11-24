package fr.maxence.chantier;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/** /chantier : ouvre l'écran Chantier (tout le reste se fait dans l'interface). */
public final class Commandes {
    private Commandes() {}

    static void enregistrer(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("chantier").executes(ctx -> {
            ServerPlayer joueur = ctx.getSource().getPlayer();
            if (joueur == null) return 0;
            Actions.repondre(joueur, null, true);
            return 1;
        }));
    }
}
