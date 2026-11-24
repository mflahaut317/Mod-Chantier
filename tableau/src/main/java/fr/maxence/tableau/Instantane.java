package fr.maxence.tableau;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLPaths;

/** L'état du serveur en JSON, recalculé toutes les 2 s sur le thread du jeu (le site ne touche jamais au jeu). */
final class Instantane {
    private Instantane() {}

    static volatile String json = "{}";
    static long demarrage;
    private static final Gson GSON = new Gson();
    private static long journalLu;
    private static JsonArray journal = new JsonArray();

    static void mettreAJour(MinecraftServer serveur) {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("heure", System.currentTimeMillis());
            o.addProperty("motd", serveur.getMotd());
            o.addProperty("version", serveur.getServerVersion());
            o.add("perf", performances(serveur));
            o.add("joueurs", joueurs(serveur));
            o.add("monde", monde(serveur));
            o.add("chantiers", chantiers());
            o.add("journal", journal(serveur));
            json = GSON.toJson(o);
        } catch (RuntimeException e) {
            Tableau.LOGGER.warn("[Tableau] État impossible à calculer", e);
        }
    }

    private static JsonObject performances(MinecraftServer serveur) {
        JsonObject p = new JsonObject();
        double mspt = serveur.getAverageTickTimeNanos() / 1_000_000.0;
        p.addProperty("mspt", Math.round(mspt * 10) / 10.0);
        p.addProperty("tps", Math.round(Math.min(20.0, 1000.0 / Math.max(mspt, 1e-3)) * 10) / 10.0);
        Runtime r = Runtime.getRuntime();
        p.addProperty("ramUtilisee", (r.totalMemory() - r.freeMemory()) / 1_048_576);
        p.addProperty("ramMax", r.maxMemory() / 1_048_576);
        p.addProperty("enLigneDepuis", System.currentTimeMillis() - demarrage);
        try {
            var os = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            p.addProperty("cpuServeur", Math.round(os.getProcessCpuLoad() * 1000) / 10.0);
            p.addProperty("cpuMachine", Math.round(os.getCpuLoad() * 1000) / 10.0);
        } catch (RuntimeException | LinkageError ignored) { }
        File monde = serveur.getWorldPath(LevelResource.ROOT).toFile();
        p.addProperty("disqueLibre", monde.getUsableSpace() / 1_073_741_824.0);
        p.addProperty("disqueTotal", monde.getTotalSpace() / 1_073_741_824.0);
        return p;
    }

    private static JsonObject monde(MinecraftServer serveur) {
        JsonObject m = new JsonObject();
        ServerLevel l = serveur.overworld();
        long temps = heureDuMonde(l);
        m.addProperty("jour", temps / 24000 + 1);
        long h = (temps % 24000 + 6000) % 24000; // 0 = 6 h du matin dans le jeu
        m.addProperty("heure", String.format("%02d:%02d", h / 1000, (h % 1000) * 60 / 1000));
        m.addProperty("pluie", l.isRaining());
        m.addProperty("orage", l.isThundering());
        m.addProperty("joueursMax", serveur.getMaxPlayers());
        return m;
    }

    /** Heure du monde : le nom de la méthode a changé selon les versions ; à défaut, le temps de jeu total. */
    private static long heureDuMonde(ServerLevel l) {
        for (String nom : new String[]{"getDayTime", "getDefaultClockTime", "getOverworldClockTime", "dayTime"}) {
            try {
                Object v = l.getClass().getMethod(nom).invoke(l);
                if (v instanceof Long t) return t;
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        return l.getGameTime();
    }

    private static JsonArray joueurs(MinecraftServer serveur) {
        JsonArray a = new JsonArray();
        for (ServerPlayer p : serveur.getPlayerList().getPlayers()) {
            JsonObject j = new JsonObject();
            j.addProperty("nom", p.getGameProfile().name());
            j.addProperty("uuid", p.getUUID().toString());
            j.addProperty("vie", Math.round(p.getHealth() * 10) / 10.0);
            j.addProperty("faim", p.getFoodData().getFoodLevel());
            j.addProperty("niveau", p.experienceLevel);
            j.addProperty("dimension", p.level().dimension().identifier().getPath());
            j.addProperty("x", p.getBlockX());
            j.addProperty("y", p.getBlockY());
            j.addProperty("z", p.getBlockZ());
            a.add(j);
        }
        return a;
    }

    /** Résumé fourni par le mod Chantier (s'il est installé), par réflexion : aucune dépendance. */
    @SuppressWarnings("unchecked")
    private static JsonElement chantiers() {
        try {
            Object r = Class.forName("fr.maxence.chantier.ChantierApi").getMethod("resume").invoke(null);
            return GSON.toJsonTree((List<Map<String, Object>>) r);
        } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
            return new JsonArray();
        }
    }

    /** Les 40 derniers souvenirs du mod Souvenirs (relus seulement quand le fichier change). */
    private static JsonArray journal(MinecraftServer serveur) {
        Path f = FMLPaths.CONFIGDIR.get().resolve("souvenirs").resolve("journal.json");
        try {
            if (!Files.exists(f)) return new JsonArray();
            long modif = Files.getLastModifiedTime(f).toMillis();
            if (modif == journalLu) return journal;
            journalLu = modif;
            JsonArray tout = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("entrees");
            JsonArray r = new JsonArray();
            for (int i = tout.size() - 1; i >= 0 && r.size() < 40; i--) {
                JsonObject e = tout.get(i).getAsJsonObject();
                JsonObject s = new JsonObject();
                s.addProperty("id", e.get("id").getAsString());
                s.addProperty("date", e.get("date").getAsLong());
                s.addProperty("joueur", e.get("joueur").getAsString());
                s.addProperty("type", e.get("type").getAsString());
                s.addProperty("titre", texte(serveur, e.get("titre").getAsString()));
                s.addProperty("texte", texte(serveur, e.get("texte").getAsString()));
                s.addProperty("lieu", e.has("lieu") ? e.get("lieu").getAsString() : "");
                s.addProperty("image", e.has("image") && e.get("image").getAsBoolean());
                r.add(s);
            }
            journal = r;
        } catch (Exception e) {
            Tableau.LOGGER.warn("[Tableau] Journal illisible", e);
        }
        return journal;
    }

    /** Texte Minecraft (JSON) -> texte simple. */
    private static String texte(MinecraftServer serveur, String json) {
        try {
            return ComponentSerialization.CODEC.parse(serveur.registryAccess().createSerializationContext(JsonOps.INSTANCE),
                    JsonParser.parseString(json)).result().map(Component::getString).orElse(json);
        } catch (RuntimeException e) {
            return json;
        }
    }
}
