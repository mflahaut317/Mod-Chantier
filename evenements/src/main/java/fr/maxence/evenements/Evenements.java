package fr.maxence.evenements;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.Locale;

/**
 * Soirées événements : un monde à part (evenements:arene) où l'on part jouer sans risquer ses affaires de survie.
 * Côté serveur uniquement : les joueurs n'ont rien à installer.
 *
 * Joueurs : /event rejoindre, /event quitter
 * Organisateurs : /event ouvrir [message], /event fermer, /event spawn, /event construire,
 *                 /cachecache lancer [chercheur] [secondes] [minutes], /cachecache stop
 * Console : /event organisateur <pseudo>
 */
@Mod(Evenements.MODID)
public class Evenements {
    public static final String MODID = "evenements";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final ResourceKey<Level> ARENE =
            ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(MODID, "arene"));
    /** Étiquette posée sur les joueurs en événement (pour viser « tous les joueurs de l'événement » dans les commandes). */
    static final String TAG = "evenement";

    private static boolean ouvert;
    private static String annonce = "";

    public Evenements() {
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> commandes(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> Etat.charger(e.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> CacheCache.arreter(e.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> CacheCache.tick(e.getServer()));

        // Dans le monde d'événement personne ne prend de dégâts (pas de mort, pas d'objets perdus)
        NeoForge.EVENT_BUS.addListener((LivingIncomingDamageEvent e) -> {
            if (e.getEntity().level().dimension() == ARENE && e.getEntity() instanceof ServerPlayer) e.setCanceled(true);
        });
        // Un coup entre joueurs dans le monde d'événement = action de jeu (trouvé au cache-cache…)
        NeoForge.EVENT_BUS.addListener((AttackEntityEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer attaquant && e.getTarget() instanceof ServerPlayer cible
                    && attaquant.level().dimension() == ARENE) {
                CacheCache.touche(attaquant.level().getServer(), attaquant, cible);
                e.setCanceled(true);
            }
        });
        // Pas de monstres dans le monde d'événement
        NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent e) -> {
            if (e.getLevel().dimension() == ARENE && e.getEntity() instanceof Enemy) e.setCanceled(true);
        });
        // Mort quand même (/kill…) : on revient dans le monde d'événement, pas chez soi
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer j && Etat.enEvenement(j.level().getServer(), j)) {
                versApparition(j.level().getServer(), j);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer j) CacheCache.retirer(j.level().getServer(), j);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer j && ouvert && !Etat.enEvenement(j.level().getServer(), j)) {
                j.sendSystemMessage(invitation());
            }
        });
    }

    // ---------- Actions ----------

    static void versApparition(MinecraftServer serveur, ServerPlayer joueur) {
        Outils.commande(serveur, String.format(Locale.ROOT, "execute in %s:arene run tp %s %.3f %.3f %.3f %.1f 0",
                MODID, joueur.getScoreboardName(), Etat.spawnX, Etat.spawnY, Etat.spawnZ, Etat.spawnYaw));
    }

    private static void rejoindre(MinecraftServer serveur, ServerPlayer joueur) {
        String nom = joueur.getScoreboardName();
        if (!Etat.enEvenement(serveur, joueur)) {
            if (!Etat.mettreDeCote(serveur, joueur)) return;
        }
        versApparition(serveur, joueur);
        Outils.commande(serveur, "gamemode adventure " + nom);
        Outils.commande(serveur, "tag " + nom + " add " + TAG);
        Outils.commande(serveur, "effect clear " + nom);
        Outils.commande(serveur, "effect give " + nom + " minecraft:saturation infinite 0 true");
        Outils.titre(joueur, "§6Événement", annonce.isEmpty() ? "§7Bienvenue !" : "§7" + annonce);
        joueur.sendSystemMessage(Component.literal("§6[Événement] §7Tes affaires de survie sont à l'abri. ")
                .append(Component.literal("§e[Revenir en survie]").withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/event quitter")))));
        for (ServerPlayer j : serveur.getPlayerList().getPlayers()) {
            if (j != joueur && j.level().dimension() == ARENE) j.sendSystemMessage(Component.literal("§6[Événement] §e" + nom + " §7est arrivé."));
        }
    }

    private static void quitter(MinecraftServer serveur, ServerPlayer joueur) {
        String nom = joueur.getScoreboardName();
        CacheCache.retirer(serveur, joueur);
        Outils.commande(serveur, "effect clear " + nom);
        Outils.commande(serveur, "tag " + nom + " remove " + TAG);
        Outils.commande(serveur, "gamemode survival " + nom);
        Etat.rendre(serveur, joueur);
        joueur.sendSystemMessage(Component.literal("§6[Événement] §7De retour en survie, avec toutes tes affaires."));
    }

    private static Component invitation() {
        return Component.literal("§6§l[Événement] §r§e" + (annonce.isEmpty() ? "Un événement commence !" : annonce) + " ")
                .append(Component.literal("§a§l[Rejoindre]").withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/event rejoindre"))));
    }

    // ---------- Commandes ----------

    private static ServerPlayer joueur(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getPlayer();
    }

    /** Organisateur (liste gérée depuis la console) ou la console elle-même. */
    private static boolean organisateur(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer j = joueur(ctx);
        if (j == null) return true;
        if (Etat.estOrganisateur(j)) return true;
        j.sendSystemMessage(Component.literal("§cRéservé aux organisateurs."));
        return false;
    }

    private static void dire(CommandContext<CommandSourceStack> ctx, String texte) {
        ctx.getSource().sendSystemMessage(Component.literal(texte));
    }

    private static void commandes(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("event")
                .then(Commands.literal("rejoindre").executes(ctx -> {
                    ServerPlayer j = joueur(ctx);
                    if (j == null) return 0;
                    MinecraftServer s = ctx.getSource().getServer();
                    if (!ouvert && !Etat.estOrganisateur(j) && !Etat.enEvenement(s, j)) {
                        dire(ctx, "§7Aucun événement en cours pour le moment.");
                        return 0;
                    }
                    if (CacheCache.enCours() && !Etat.enEvenement(s, j)) dire(ctx, "§7Une partie est en cours : tu joueras à la prochaine.");
                    rejoindre(s, j);
                    return 1;
                }))
                .then(Commands.literal("quitter").executes(ctx -> {
                    ServerPlayer j = joueur(ctx);
                    if (j == null) return 0;
                    MinecraftServer s = ctx.getSource().getServer();
                    if (!Etat.enEvenement(s, j)) {
                        dire(ctx, "§7Tu n'es pas en événement.");
                        return 0;
                    }
                    if (j.level().dimension() != ARENE) {
                        // Ses affaires sont encore de côté : on le ramène d'abord, pour ne rien écraser
                        versApparition(s, j);
                        dire(ctx, "§7Retour au monde d'événement… refais /event quitter dans un instant.");
                        return 0;
                    }
                    quitter(s, j);
                    return 1;
                }))
                .then(Commands.literal("ouvrir")
                        .executes(ctx -> ouvrir(ctx, ""))
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(ctx -> ouvrir(ctx, StringArgumentType.getString(ctx, "message")))))
                .then(Commands.literal("fermer").executes(ctx -> {
                    if (!organisateur(ctx)) return 0;
                    MinecraftServer s = ctx.getSource().getServer();
                    CacheCache.arreter(s);
                    ouvert = false;
                    for (ServerPlayer j : Etat.participants(s)) quitter(s, j);
                    s.getPlayerList().broadcastSystemMessage(Component.literal("§6§l[Événement] §r§7L'événement est terminé, merci d'être venus !"), false);
                    return 1;
                }))
                .then(Commands.literal("spawn").executes(ctx -> {
                    if (!organisateur(ctx)) return 0;
                    ServerPlayer j = joueur(ctx);
                    if (j == null || j.level().dimension() != ARENE) {
                        dire(ctx, "§cVa d'abord dans le monde d'événement (/event rejoindre).");
                        return 0;
                    }
                    Etat.spawnX = j.getX();
                    Etat.spawnY = j.getY();
                    Etat.spawnZ = j.getZ();
                    Etat.spawnYaw = j.getYRot();
                    Etat.sauver(ctx.getSource().getServer());
                    dire(ctx, "§aPoint d'apparition de l'événement placé ici.");
                    return 1;
                }))
                .then(Commands.literal("construire").executes(ctx -> {
                    if (!organisateur(ctx)) return 0;
                    ServerPlayer j = joueur(ctx);
                    if (j == null || j.level().dimension() != ARENE) {
                        dire(ctx, "§cVa d'abord dans le monde d'événement (/event rejoindre).");
                        return 0;
                    }
                    boolean creatif = j.isCreative();
                    Outils.commande(ctx.getSource().getServer(), "gamemode " + (creatif ? "adventure " : "creative ") + j.getScoreboardName());
                    dire(ctx, creatif ? "§7Mode construction désactivé." : "§aMode construction : tu peux bâtir la carte. Refais /event construire pour arrêter.");
                    return 1;
                }))
                // Console seulement : nommer ou retirer un organisateur
                .then(Commands.literal("organisateur")
                        .then(Commands.argument("pseudo", StringArgumentType.word()).executes(ctx -> {
                            if (joueur(ctx) != null) {
                                dire(ctx, "§cÀ taper dans la console du serveur.");
                                return 0;
                            }
                            String p = StringArgumentType.getString(ctx, "pseudo").toLowerCase(Locale.ROOT);
                            boolean ajoute = Etat.ORGANISATEURS.add(p);
                            if (!ajoute) Etat.ORGANISATEURS.remove(p);
                            Etat.sauver(ctx.getSource().getServer());
                            dire(ctx, p + (ajoute ? " est maintenant organisateur." : " n'est plus organisateur.")
                                    + " Organisateurs : " + String.join(", ", Etat.ORGANISATEURS));
                            return 1;
                        }))));

        d.register(Commands.literal("cachecache")
                .then(Commands.literal("lancer")
                        .executes(ctx -> lancer(ctx, null, 30, 5))
                        .then(Commands.argument("chercheur", StringArgumentType.word())
                                .executes(ctx -> lancer(ctx, StringArgumentType.getString(ctx, "chercheur"), 30, 5))
                                .then(Commands.argument("secondes", IntegerArgumentType.integer(5, 300))
                                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 60))
                                                .executes(ctx -> lancer(ctx, StringArgumentType.getString(ctx, "chercheur"),
                                                        IntegerArgumentType.getInteger(ctx, "secondes"),
                                                        IntegerArgumentType.getInteger(ctx, "minutes")))))))
                .then(Commands.literal("stop").executes(ctx -> {
                    if (!organisateur(ctx)) return 0;
                    CacheCache.terminer(ctx.getSource().getServer(), false);
                    return 1;
                })));
    }

    private static int ouvrir(CommandContext<CommandSourceStack> ctx, String message) {
        if (!organisateur(ctx)) return 0;
        ouvert = true;
        annonce = message;
        ctx.getSource().getServer().getPlayerList().broadcastSystemMessage(invitation(), false);
        return 1;
    }

    private static int lancer(CommandContext<CommandSourceStack> ctx, String chercheur, int secondes, int minutes) {
        if (!organisateur(ctx)) return 0;
        String erreur = CacheCache.lancer(ctx.getSource().getServer(), chercheur, secondes, minutes);
        if (erreur != null) {
            dire(ctx, "§c" + erreur);
            return 0;
        }
        return 1;
    }
}
