package fr.maxence.souvenirs;

import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;

/** Les moments notés automatiquement : premiers exploits, succès rares, morts, boss. */
final class Evenements {
    private Evenements() {}

    /** Premiers exploits (une fois par joueur, comme tous les succès). */
    private static final Set<String> EXPLOITS = Set.of(
            "minecraft:story/mine_diamond", "minecraft:story/enter_the_nether", "minecraft:story/follow_ender_eye",
            "minecraft:story/enter_the_end", "minecraft:nether/obtain_ancient_debris", "minecraft:nether/netherite_armor",
            "minecraft:end/elytra", "minecraft:story/cure_zombie_villager");

    /** Déjà couverts par les boss : pas de doublon. */
    private static final Set<String> IGNORES = Set.of("minecraft:end/kill_dragon", "minecraft:nether/summon_wither");

    /** Boss -> icône. */
    private static final Map<String, String> BOSS = Map.of(
            "ender_dragon", "minecraft:dragon_head",
            "wither", "minecraft:nether_star",
            "warden", "minecraft:sculk_shrieker",
            "elder_guardian", "minecraft:prismarine_shard");

    private static final String[] EPITAPHES = {
            "Paix à son inventaire.", "Il reviendra plus fort. Peut-être.", "On n'oubliera jamais ce moment.",
            "C'était pourtant bien parti.", "Une minute de silence.", "Le respawn, c'est la vie.",
            "Ses affaires l'attendent encore là-bas.", "La prudence, ce sera pour la prochaine fois."};
    private static final Random HASARD = new Random();

    static void onSucces(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer joueur)) return;
        AdvancementHolder succes = event.getAdvancement();
        String id = succes.id().toString();
        DisplayInfo affichage = succes.value().display().orElse(null);
        if (affichage == null || IGNORES.contains(id) || !affichage.announceToChat()) return;
        boolean exploit = EXPLOITS.contains(id);
        boolean rare = affichage.type() == AdvancementType.CHALLENGE || affichage.type() == AdvancementType.GOAL;
        if (!exploit && !rare) return;
        String nom = joueur.getGameProfile().name();
        String icone = BuiltInRegistries.ITEM.getKey(affichage.icon().item().value()).toString();
        Journal.ajouter(joueur.level().getServer(), nom, exploit ? "exploit" : "succes", icone,
                Component.literal(nom + " : ").append(affichage.title()), affichage.description(), Journal.lieu(joueur));
    }

    static void onMort(LivingDeathEvent event) {
        Entity mort = event.getEntity();
        if (mort instanceof ServerPlayer joueur) {
            String nom = joueur.getGameProfile().name();
            Component message = joueur.getCombatTracker().getDeathMessage();
            String epitaphe = EPITAPHES[HASARD.nextInt(EPITAPHES.length)];
            Journal.ajouter(joueur.level().getServer(), nom, "mort", "minecraft:skeleton_skull",
                    Component.literal("☠ " + nom + " nous a quittés"),
                    message.copy().append(Component.literal(". " + epitaphe)), Journal.lieu(joueur));
            return;
        }
        String type = EntityType.getKey(mort.getType()).getPath();
        String icone = BOSS.get(type);
        if (icone == null) return;
        Entity tueur = event.getSource().getEntity();
        if (!(tueur instanceof ServerPlayer joueur)) return;
        String nom = joueur.getGameProfile().name();
        Journal.ajouter(joueur.level().getServer(), nom, "boss", icone,
                Component.literal("⚔ " + nom + " a vaincu : ").append(mort.getType().getDescription()),
                Component.literal("Un combat qui restera dans les mémoires du serveur."), Journal.lieu(joueur));
    }
}
