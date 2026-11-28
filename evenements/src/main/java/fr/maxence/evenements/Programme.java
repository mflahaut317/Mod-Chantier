package fr.maxence.evenements;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Programme des soirées : écrit en jeu (/event planifier), gardé dans le monde, et publié sur GitHub
 * (site/evenements.json) pour s'afficher dans le launcher et sur le site.
 *
 * Publication : fichier « evenements/github.properties » dans le dossier du monde, avec
 *   token=github_pat_…        (jeton « fine-grained » limité au dépôt, droit Contents en écriture)
 *   depot=mflahaut317/pixel-potes
 *   chemin=site/evenements.json
 */
final class Programme {
    private Programme() {}

    static final ZoneId FUSEAU = ZoneId.of("Europe/Paris");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    record Soiree(String date, String titre, String description) {}

    private static Path fichier(MinecraftServer serveur) {
        return serveur.getWorldPath(LevelResource.ROOT).resolve("evenements").resolve("programme.json");
    }

    static List<Soiree> lire(MinecraftServer serveur) {
        List<Soiree> liste = new ArrayList<>();
        try {
            Path f = fichier(serveur);
            if (!Files.exists(f)) return liste;
            for (var e : JsonParser.parseString(Files.readString(f)).getAsJsonArray()) {
                JsonObject o = e.getAsJsonObject();
                liste.add(new Soiree(o.get("date").getAsString(), o.get("titre").getAsString(),
                        o.has("description") ? o.get("description").getAsString() : ""));
            }
        } catch (Exception e) {
            Evenements.LOGGER.warn("Programme des événements illisible", e);
        }
        return liste;
    }

    private static String json(List<Soiree> liste) {
        JsonArray tableau = new JsonArray();
        for (Soiree s : liste) {
            JsonObject o = new JsonObject();
            o.addProperty("date", s.date());
            o.addProperty("titre", s.titre());
            if (!s.description().isBlank()) o.addProperty("description", s.description());
            o.addProperty("duree_h", 3);
            tableau.add(o);
        }
        return GSON.toJson(tableau) + "\n";
    }

    private static void ecrire(MinecraftServer serveur, List<Soiree> liste) throws IOException {
        liste.sort(Comparator.comparing(s -> ZonedDateTime.parse(s.date())));
        Path f = fichier(serveur);
        Files.createDirectories(f.getParent());
        Files.writeString(f, json(liste));
    }

    /** Soirées pas encore passées (on garde 3 h après le début). */
    static List<Soiree> aVenir(MinecraftServer serveur) {
        ZonedDateTime limite = ZonedDateTime.now(FUSEAU).minusHours(3);
        List<Soiree> liste = new ArrayList<>();
        for (Soiree s : lire(serveur)) if (ZonedDateTime.parse(s.date()).isAfter(limite)) liste.add(s);
        return liste;
    }

    // ---------- Dates : « 05/12 21:00 », « 5/12/2026 21h », « samedi 21h30 »… ----------

    private static final Pattern DATE = Pattern.compile("(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?");
    private static final Pattern HEURE = Pattern.compile("(\\d{1,2})(?:[h:](\\d{2})?)?");
    private static final String[] JOURS = {"lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche"};

    /** Renvoie la date, ou null si le jour ou l'heure ne se lisent pas. */
    static ZonedDateTime lireDate(String jour, String heure) {
        LocalDate aujourdHui = LocalDate.now(FUSEAU);
        LocalDate d = null;
        String j = jour.toLowerCase(Locale.ROOT);
        if (j.equals("aujourd'hui") || j.equals("ce-soir") || j.equals("cesoir")) d = aujourdHui;
        else if (j.equals("demain")) d = aujourdHui.plusDays(1);
        for (int i = 0; i < JOURS.length && d == null; i++) {
            if (JOURS[i].equals(j)) {
                d = aujourdHui;
                while (d.getDayOfWeek().getValue() != i + 1) d = d.plusDays(1);
            }
        }
        Matcher m = DATE.matcher(jour);
        if (d == null && m.matches()) {
            int jj = Integer.parseInt(m.group(1)), mm = Integer.parseInt(m.group(2));
            int aa = m.group(3) == null ? aujourdHui.getYear() : Integer.parseInt(m.group(3));
            if (aa < 100) aa += 2000;
            try {
                d = LocalDate.of(aa, mm, jj);
            } catch (Exception e) {
                return null;
            }
            if (m.group(3) == null && d.isBefore(aujourdHui)) d = d.plusYears(1);
        }
        Matcher h = HEURE.matcher(heure.toLowerCase(Locale.ROOT));
        if (d == null || !h.matches()) return null;
        int hh = Integer.parseInt(h.group(1)), mi = h.group(2) == null ? 0 : Integer.parseInt(h.group(2));
        if (hh > 23 || mi > 59) return null;
        return ZonedDateTime.of(d, LocalTime.of(hh, mi), FUSEAU);
    }

    static String lisible(String iso) {
        return ZonedDateTime.parse(iso).format(DateTimeFormatter.ofPattern("EEEE d MMMM 'à' HH'h'mm", Locale.FRENCH));
    }

    // ---------- Actions ----------

    static Soiree ajouter(MinecraftServer serveur, ZonedDateTime date, String titre, String description) throws IOException {
        List<Soiree> liste = aVenir(serveur);
        Soiree s = new Soiree(date.withNano(0).toOffsetDateTime().toString(), titre, description);
        liste.add(s);
        ecrire(serveur, liste);
        return s;
    }

    /** Retire la soirée n° (1 = la prochaine). Renvoie la soirée retirée, ou null. */
    static Soiree retirer(MinecraftServer serveur, int numero) throws IOException {
        List<Soiree> liste = aVenir(serveur);
        if (numero < 1 || numero > liste.size()) return null;
        Soiree s = liste.remove(numero - 1);
        ecrire(serveur, liste);
        return s;
    }

    // ---------- Publication sur GitHub (en tâche de fond) ----------

    static void publier(MinecraftServer serveur, Consumer<String> resultat) {
        Properties p = new Properties();
        Path conf = serveur.getWorldPath(LevelResource.ROOT).resolve("evenements").resolve("github.properties");
        try (var in = Files.newBufferedReader(conf)) {
            p.load(in);
        } catch (IOException e) {
            resultat.accept("§eProgramme enregistré, mais pas publié : fichier evenements/github.properties absent (voir l'aide-mémoire).");
            return;
        }
        String token = p.getProperty("token", "").trim();
        String depot = p.getProperty("depot", "mflahaut317/pixel-potes").trim();
        String chemin = p.getProperty("chemin", "site/evenements.json").trim();
        if (token.isEmpty()) {
            resultat.accept("§eProgramme enregistré, mais pas publié : jeton GitHub manquant.");
            return;
        }
        String contenu = json(aVenir(serveur));
        String url = "https://api.github.com/repos/" + depot + "/contents/" + chemin;
        Thread.ofVirtual().start(() -> {
            try {
                // L'API demande l'empreinte (sha) du fichier actuel pour le remplacer
                HttpResponse<String> lu = HTTP.send(requete(url, token).GET().build(), HttpResponse.BodyHandlers.ofString());
                String sha = lu.statusCode() == 200 ? JsonParser.parseString(lu.body()).getAsJsonObject().get("sha").getAsString() : null;
                JsonObject corps = new JsonObject();
                corps.addProperty("message", "Événements : programme mis à jour depuis le jeu");
                corps.addProperty("content", Base64.getEncoder().encodeToString(contenu.getBytes(StandardCharsets.UTF_8)));
                if (sha != null) corps.addProperty("sha", sha);
                HttpResponse<String> ecrit = HTTP.send(requete(url, token)
                        .PUT(HttpRequest.BodyPublishers.ofString(corps.toString())).build(), HttpResponse.BodyHandlers.ofString());
                if (ecrit.statusCode() / 100 == 2) {
                    serveur.execute(() -> resultat.accept("§aPublié : le launcher et le site l'afficheront d'ici 5 minutes."));
                } else {
                    Evenements.LOGGER.warn("Publication du programme refusée par GitHub ({}) : {}", ecrit.statusCode(), ecrit.body());
                    serveur.execute(() -> resultat.accept("§cGitHub a refusé la publication (code " + ecrit.statusCode() + "). Le jeton est peut-être expiré."));
                }
            } catch (Exception e) {
                Evenements.LOGGER.warn("Publication du programme impossible", e);
                serveur.execute(() -> resultat.accept("§cPublication impossible (pas d'Internet ?). Le programme est quand même enregistré."));
            }
        });
    }

    private static HttpRequest.Builder requete(String url, String token) {
        return HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28");
    }

    static void dire(ServerPlayer joueur, MinecraftServer serveur, String texte) {
        if (joueur != null) joueur.sendSystemMessage(net.minecraft.network.chat.Component.literal(texte));
        else serveur.sendSystemMessage(net.minecraft.network.chat.Component.literal(texte));
    }
}
