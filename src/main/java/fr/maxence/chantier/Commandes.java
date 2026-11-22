package fr.maxence.chantier;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * /chantier lien <lien>        lien Google Sheets (colonne A : bloc, colonne B : quantité)
 * /chantier zone <coin1> <coin2>  zone des coffres du chantier (~ ~ ~ accepté)
 * /chantier actualiser         relit la feuille
 * /chantier titre <texte>      nom affiché en haut de la liste
 * /chantier masquer | afficher cache ou montre la liste pour soi
 * /chantier info               résumé
 * /chantier arreter            efface le chantier
 */
public final class Commandes {
    private Commandes() {}

    static void enregistrer(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("chantier")
                .then(Commands.literal("lien")
                        .then(Commands.argument("lien", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    Projet.get().definirLien(StringArgumentType.getString(ctx, "lien"));
                                    msg(ctx, "Lien enregistré, lecture de la feuille…");
                                    actualiser(ctx);
                                    return 1;
                                })))
                .then(Commands.literal("zone")
                        .then(Commands.argument("coin1", BlockPosArgument.blockPos())
                                .then(Commands.argument("coin2", BlockPosArgument.blockPos())
                                        .executes(Commandes::zone))))
                .then(Commands.literal("actualiser").executes(ctx -> { actualiser(ctx); return 1; }))
                .then(Commands.literal("titre")
                        .then(Commands.argument("titre", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    Projet.get().definirTitre(StringArgumentType.getString(ctx, "titre"));
                                    Projet.get().diffuser(ctx.getSource().getServer());
                                    msg(ctx, "Titre changé.");
                                    return 1;
                                })))
                .then(Commands.literal("masquer").executes(ctx -> masquer(ctx, true)))
                .then(Commands.literal("afficher").executes(ctx -> masquer(ctx, false)))
                .then(Commands.literal("info").executes(ctx -> { msg(ctx, Projet.get().resume()); return 1; }))
                .then(Commands.literal("arreter").executes(ctx -> {
                    Projet.get().arreter();
                    Projet.get().diffuser(ctx.getSource().getServer());
                    msg(ctx, "Chantier effacé.");
                    return 1;
                })));
    }

    private static int zone(CommandContext<CommandSourceStack> ctx) {
        BlockPos a = BlockPosArgument.getBlockPos(ctx, "coin1");
        BlockPos b = BlockPosArgument.getBlockPos(ctx, "coin2");
        if (Math.abs(a.getX() - b.getX()) >= Projet.TAILLE_MAX || Math.abs(a.getZ() - b.getZ()) >= Projet.TAILLE_MAX) {
            ctx.getSource().sendFailure(Component.literal(
                    "Zone trop grande (max " + Projet.TAILLE_MAX + " blocs de côté)."));
            return 0;
        }
        MinecraftServer serveur = ctx.getSource().getServer();
        Projet.get().definirZone(ctx.getSource().getLevel().dimension(), a, b);
        Projet.get().recompter(serveur);
        Projet.get().diffuser(serveur);
        msg(ctx, "Zone enregistrée. Avancement : " + Projet.get().pourcentage() + " %");
        return 1;
    }

    private static void actualiser(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Projet.get().telechargerListe(source.getServer(),
                texte -> source.sendSuccess(() -> Component.literal(texte), false));
    }

    private static int masquer(CommandContext<CommandSourceStack> ctx, boolean masque) {
        ServerPlayer joueur = ctx.getSource().getPlayer();
        if (joueur == null) return 0;
        Projet.get().masquer(joueur.getUUID(), masque);
        Projet.get().envoyerA(joueur);
        msg(ctx, masque ? "Liste masquée." : "Liste affichée.");
        return 1;
    }

    private static void msg(CommandContext<CommandSourceStack> ctx, String texte) {
        ctx.getSource().sendSuccess(() -> Component.literal(texte), false);
    }
}
