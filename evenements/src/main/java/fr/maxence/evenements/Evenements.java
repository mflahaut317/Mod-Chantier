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
 * Organisateurs : /event preparer, /event ouvrir [message], /event fermer, /event spawn, /event construire,
 *                 /event planifier <jour> <heure> <titre | description>, /event annuler <n>, /event programme,
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
    /** Joueurs invités à aider à préparer (peuvent entrer avant l'ouverture et construire). Vidé à la fermeture. */
    private static final java.util.Set<String> AIDES = new java.util.HashSet<>();

    static boolean estAide(ServerPlayer j) {
        return AIDES.contains(j.getScoreboardName().toLowerCase(Locale.ROOT));
    }

    static boolean peutPreparer(ServerPlayer j) {
        return Etat.estOrganisateur(j) || estAide(j);
    }
    private static String annonce = "";

    public Evenements(net.neoforged.bus.api.IEventBus modBus) {
        modBus.addListener(Evenements::reseau);
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
        // Ni animaux ni monstres qui apparaissent tout seuls (chevaux de la plaine…) : seuls ceux posés à la main restent
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent e) -> {
            if (e.getLevel().getLevel().dimension() != ARENE) return;
            var raison = e.getSpawnType();
            if (raison == net.minecraft.world.entity.EntitySpawnReason.NATURAL
                    || raison == net.minecraft.world.entity.EntitySpawnReason.CHUNK_GENERATION) {
                e.setSpawnCancelled(true);
            }
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

    // ---------- Écran « Événement » (réseau) ----------

    /** « optional » : un joueur sans le mod peut quand même se connecter. */
    private static void reseau(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1").optional();
        r.playToClient(Paquets.Etat.TYPE, Paquets.Etat.CODEC, (p, c) -> Recu.etat(p));
        r.playToServer(Paquets.Action.TYPE, Paquets.Action.CODEC, (p, c) -> {
            if (c.player() instanceof ServerPlayer j) action(j, p.commande());
        });
    }

    static void envoyerEtat(ServerPlayer j, boolean ouvrirEcran) {
        MinecraftServer s = j.level().getServer();
        java.util.List<String> noms = new java.util.ArrayList<>();
        for (ServerPlayer p : Etat.participants(s)) noms.add(p.getScoreboardName());
        boolean dedans = j.level().dimension() == ARENE && Etat.enEvenement(s, j);
        String programme = new com.google.gson.Gson().toJson(Programme.aVenir(s));
        java.util.List<String> enLigne = new java.util.ArrayList<>();
        for (ServerPlayer p : s.getPlayerList().getPlayers()) if (p != j) enLigne.add(p.getScoreboardName());
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(j, new Paquets.Etat(ouvert, Etat.estOrganisateur(j), estAide(j),
                dedans, dedans && j.isCreative(), CacheCache.enCours(), noms, enLigne, programme, ouvrirEcran));
    }

    /** Un bouton de l'écran : on exécute la commande correspondante au nom du joueur (mêmes vérifications). */
    private static void action(ServerPlayer j, String commande) {
        MinecraftServer s = j.level().getServer();
        if (commande.startsWith("event ") || commande.startsWith("cachecache ")) {
            s.getCommands().performPrefixedCommand(j.createCommandSourceStack(), commande);
        }
        envoyerEtat(j, false);
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
                .executes(ctx -> {
                    ServerPlayer j = joueur(ctx);
                    if (j != null) envoyerEtat(j, true);
                    return 1;
                })
                .then(Commands.literal("menu").executes(ctx -> {
                    ServerPlayer j = joueur(ctx);
                    if (j != null) envoyerEtat(j, true);
                    return 1;
                }))
                .then(Commands.literal("rejoindre").executes(ctx -> {
                    ServerPlayer j = joueur(ctx);
                    if (j == null) return 0;
                    MinecraftServer s = ctx.getSource().getServer();
                    if (!ouvert && !peutPreparer(j) && !Etat.enEvenement(s, j)) {
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
                    AIDES.clear();
                    for (ServerPlayer j : Etat.participants(s)) quitter(s, j);
                    s.getPlayerList().broadcastSystemMessage(Component.literal("§6§l[Événement] §r§7L'événement est terminé, merci d'être venus !"), false);
                    return 1;
                }))
                // Préparer : l'organisateur entre seul et construit, l'événement reste fermé aux autres
                .then(Commands.literal("preparer").executes(ctx -> {
                    if (!organisateur(ctx)) return 0;
                    ServerPlayer j = joueur(ctx);
                    if (j == null) return 0;
                    MinecraftServer s = ctx.getSource().getServer();
                    rejoindre(s, j);
                    Outils.commande(s, "gamemode creative " + j.getScoreboardName());
                    dire(ctx, "§aMode préparation : tu es seul (l'événement est " + (ouvert ? "§cOUVERT§a" : "fermé") + "), en créatif."
                            + " §7/event ouvrir quand tout est prêt.");
                    return 1;
                }))
                // Programme affiché dans le launcher et sur le site
                .then(Commands.literal("planifier")
                        .then(Commands.argument("jour", StringArgumentType.word())
                                .then(Commands.argument("heure", StringArgumentType.word())
                                        .then(Commands.argument("texte", StringArgumentType.greedyString())
                                                .executes(Evenements::planifier)))))
                .then(Commands.literal("annuler")
                        .then(Commands.argument("numero", IntegerArgumentType.integer(1)).executes(ctx -> {
                            if (!organisateur(ctx)) return 0;
                            MinecraftServer s = ctx.getSource().getServer();
                            try {
                                Programme.Soiree retiree = Programme.retirer(s, IntegerArgumentType.getInteger(ctx, "numero"));
                                if (retiree == null) {
                                    dire(ctx, "§cPas d'événement à ce numéro (voir /event programme).");
                                    return 0;
                                }
                                dire(ctx, "§7« " + retiree.titre() + " » retiré du programme.");
                            } catch (Exception e) {
                                dire(ctx, "§cImpossible d'enregistrer le programme.");
                                return 0;
                            }
                            ServerPlayer j = joueur(ctx);
                            Programme.publier(s, t -> Programme.dire(j, s, t));
                            return 1;
                        })))
                .then(Commands.literal("programme").executes(ctx -> {
                    var liste = Programme.aVenir(ctx.getSource().getServer());
                    if (liste.isEmpty()) dire(ctx, "§7Aucun événement prévu.");
                    for (int i = 0; i < liste.size(); i++) {
                        var e = liste.get(i);
                        dire(ctx, "§6" + (i + 1) + ". §e" + e.titre() + " §7— " + Programme.lisible(e.date())
                                + (e.description().isBlank() ? "" : " — " + e.description()));
                    }
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
                    ServerPlayer j = joueur(ctx);
                    if (j != null && !peutPreparer(j)) {
                        dire(ctx, "§cRéservé aux organisateurs et à leurs invités.");
                        return 0;
                    }
                    if (j == null || j.level().dimension() != ARENE) {
                        dire(ctx, "§cVa d'abord dans le monde d'événement (/event rejoindre).");
                        return 0;
                    }
                    boolean creatif = j.isCreative();
                    Outils.commande(ctx.getSource().getServer(), "gamemode " + (creatif ? "adventure " : "creative ") + j.getScoreboardName());
                    dire(ctx, creatif ? "§7Mode construction désactivé." : "§aMode construction : tu peux bâtir la carte. Refais /event construire pour arrêter.");
                    return 1;
                }))
                // Inviter quelqu'un à aider à préparer (entre avant l'ouverture, peut construire)
                .then(Commands.literal("inviter")
                        .then(Commands.argument("pseudo", StringArgumentType.word()).executes(ctx -> {
                            if (!organisateur(ctx)) return 0;
                            String nom = StringArgumentType.getString(ctx, "pseudo");
                            ServerPlayer invite = ctx.getSource().getServer().getPlayerList().getPlayerByName(nom);
                            if (invite == null) {
                                dire(ctx, "§c" + nom + " n'est pas connecté.");
                                return 0;
                            }
                            AIDES.add(invite.getScoreboardName().toLowerCase(Locale.ROOT));
                            invite.sendSystemMessage(Component.literal("§6§l[Événement] §r§e" + (joueur(ctx) == null ? "L'organisateur"
                                    : joueur(ctx).getScoreboardName()) + " §7t'invite à préparer l'événement avec lui. ")
                                    .append(Component.literal("§a§l[Rejoindre la préparation]").withStyle(st -> st.withClickEvent(
                                            new ClickEvent.RunCommand("/event rejoindre")))));
                            dire(ctx, "§a" + invite.getScoreboardName() + " est invité à préparer (il peut entrer et construire).");
                            return 1;
                        })))
                // Nommer ou retirer un organisateur (console, ou un organisateur en jeu)
                .then(Commands.literal("organisateur")
                        .then(Commands.argument("pseudo", StringArgumentType.word()).executes(ctx -> {
                            if (!organisateur(ctx)) return 0;
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

    private static int planifier(CommandContext<CommandSourceStack> ctx) {
        if (!organisateur(ctx)) return 0;
        MinecraftServer s = ctx.getSource().getServer();
        var date = Programme.lireDate(StringArgumentType.getString(ctx, "jour"), StringArgumentType.getString(ctx, "heure"));
        if (date == null) {
            dire(ctx, "§cDate illisible. Exemples : /event planifier 05/12 21:00 Soirée cache-cache | Venez nombreux !"
                    + " — ou samedi 21h, demain 20h30.");
            return 0;
        }
        // « Titre | description »
        String texte = StringArgumentType.getString(ctx, "texte");
        int barre = texte.indexOf('|');
        String titre = (barre < 0 ? texte : texte.substring(0, barre)).trim();
        String description = barre < 0 ? "" : texte.substring(barre + 1).trim();
        try {
            Programme.ajouter(s, date, titre, description);
        } catch (Exception e) {
            dire(ctx, "§cImpossible d'enregistrer le programme.");
            return 0;
        }
        dire(ctx, "§a« " + titre + " » prévu " + Programme.lisible(date.toOffsetDateTime().toString()) + ". §7Publication…");
        ServerPlayer j = joueur(ctx);
        Programme.publier(s, t -> Programme.dire(j, s, t));
        return 1;
    }

    private static int ouvrir(CommandContext<CommandSourceStack> ctx, String message) {
        if (!organisateur(ctx)) return 0;
        // Sans message : le titre de l'événement prévu maintenant (s'il y en a un)
        if (message.isBlank()) {
            var prochains = Programme.aVenir(ctx.getSource().getServer());
            if (!prochains.isEmpty() && java.time.ZonedDateTime.parse(prochains.get(0).date())
                    .isBefore(java.time.ZonedDateTime.now(Programme.FUSEAU).plusHours(1))) {
                message = prochains.get(0).titre();
            }
        }
        ouvert = true;
        annonce = message;
        ctx.getSource().getServer().getPlayerList().broadcastSystemMessage(invitation(), false);
        return 1;
    }

    private static int lancer(CommandContext<CommandSourceStack> ctx, String chercheur, int secondes, int minutes) {
        if (!organisateur(ctx)) return 0;
        if ("hasard".equalsIgnoreCase(chercheur)) chercheur = null; // depuis l'écran : chercheur au hasard
        String erreur = CacheCache.lancer(ctx.getSource().getServer(), chercheur, secondes, minutes);
        if (erreur != null) {
            dire(ctx, "§c" + erreur);
            return 0;
        }
        return 1;
    }
}
