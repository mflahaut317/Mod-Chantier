package fr.maxence.chantier;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/** /chantier : ouvre l'écran Chantier (tout le reste se fait dans l'interface). */
public final class Commandes {
    private Commandes() {}

    static void enregistrer(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("chantier")
                .executes(ctx -> {
                    ServerPlayer joueur = ctx.getSource().getPlayer();
                    if (joueur == null) return 0;
                    Actions.repondre(joueur, null, true);
                    return 1;
                })
                // /chantier terminer : sort du mode « choisir les coffres » (lien cliquable dans le chat)
                .then(Commands.literal("terminer").executes(ctx -> {
                    ServerPlayer joueur = ctx.getSource().getPlayer();
                    if (joueur == null) return 0;
                    Projet projet = Chantiers.de(joueur.getUUID());
                    if (projet != null) Actions.terminerSelection(joueur, projet);
                    return 1;
                }))
                // Console du serveur seulement (pas les joueurs) : lister et supprimer n'importe quel chantier
                .then(Commands.literal("liste").executes(ctx -> {
                    if (ctx.getSource().getPlayer() != null) return 0;
                    java.util.List<Projet> tous = Chantiers.tous();
                    if (tous.isEmpty()) ctx.getSource().sendSystemMessage(Component.literal("Aucun chantier."));
                    for (int i = 0; i < tous.size(); i++) {
                        Projet p = tous.get(i);
                        ctx.getSource().sendSystemMessage(Component.literal((i + 1) + ". « " + p.titre() + " » — "
                                + p.nomsMembres() + " — " + p.nombreMateriaux() + " matériaux, " + p.pourcentage() + " %"));
                    }
                    return 1;
                }))
                .then(Commands.literal("supprimer")
                        .then(Commands.argument("numero", IntegerArgumentType.integer(1)).executes(ctx -> {
                            if (ctx.getSource().getPlayer() != null) return 0;
                            java.util.List<Projet> tous = Chantiers.tous();
                            int n = IntegerArgumentType.getInteger(ctx, "numero");
                            if (n > tous.size()) {
                                ctx.getSource().sendSystemMessage(Component.literal("Pas de chantier n° " + n + " (voir chantier liste)."));
                                return 0;
                            }
                            Projet p = tous.get(n - 1);
                            Chantiers.supprimer(p, ctx.getSource().getServer());
                            ctx.getSource().sendSystemMessage(Component.literal("Chantier « " + p.titre() + " » supprimé."));
                            return 1;
                        }))));
    }
}
