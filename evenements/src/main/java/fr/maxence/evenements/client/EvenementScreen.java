package fr.maxence.evenements.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.maxence.evenements.Paquets;
import fr.maxence.evenements.Recu;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Écran « Événement » : rejoindre / quitter pour tout le monde ; pour les organisateurs, préparer, ouvrir, fermer,
 * programmer une soirée (calendrier, heure, titre, description) et lancer les jeux.
 * Chaque bouton envoie la commande correspondante au serveur, qui fait les mêmes vérifications qu'en tapant la commande.
 */
public class EvenementScreen extends Screen {
    private static final int L = 340, H = 230;
    private static final int OR = 0xFFF2C94C, VERT = 0xFF5BD16B, ROUGE = 0xFFFF6B6B, GRIS = 0xFFAAAAAA, BLANC = 0xFFFFFFFF;
    private static final DateTimeFormatter LISIBLE = DateTimeFormatter.ofPattern("EEEE d MMMM 'à' HH'h'mm", Locale.FRENCH);

    private enum Onglet { ACCUEIL, PROGRAMME, NOUVEAU, JEUX }

    private Onglet onglet = Onglet.ACCUEIL;
    private int versionVue = -1;
    private int x0, y0;

    // Formulaire « nouvel événement »
    private YearMonth mois = YearMonth.now();
    private LocalDate jour = LocalDate.now();
    private int heure = 21, minute = 0;
    private String titre = "", description = "";

    // Cache-cache
    private int chercheur = 0; // 0 = au hasard, sinon index dans les participants + 1
    private int secondes = 30, minutes = 5;
    private int invite = 0; // joueur choisi pour aider à préparer

    private record Soiree(String date, String titre, String description) {}

    public EvenementScreen() {
        super(Component.literal("Événement"));
    }

    private static Paquets.Etat etat() { return Recu.etat; }

    private boolean organisateur() { return etat() != null && etat().organisateur(); }

    private List<Soiree> programme() {
        List<Soiree> liste = new ArrayList<>();
        if (etat() == null) return liste;
        try {
            for (JsonElement e : JsonParser.parseString(etat().programme()).getAsJsonArray()) {
                JsonObject o = e.getAsJsonObject();
                liste.add(new Soiree(o.get("date").getAsString(), o.get("titre").getAsString(),
                        o.has("description") ? o.get("description").getAsString() : ""));
            }
        } catch (Exception ignored) { }
        return liste;
    }

    private void commande(String c) {
        EvenementsClient.envoyer(c);
    }

    private Button bouton(String texte, int x, int y, int l, Button.OnPress action) {
        return addRenderableWidget(Button.builder(Component.literal(texte), action).bounds(x, y, l, 20).build());
    }

    private void aller(Onglet o) {
        onglet = o;
        rebuildWidgets();
    }

    @Override
    protected void init() {
        x0 = (width - L) / 2;
        y0 = (height - H) / 2;
        versionVue = Recu.version;
        // Onglets
        int n = organisateur() ? 3 : 2;
        int lo = (L - 10) / n;
        Button a = bouton("Accueil", x0 + 5, y0 + 5, lo - 4, b -> aller(Onglet.ACCUEIL));
        Button p = bouton("Programme", x0 + 5 + lo, y0 + 5, lo - 4, b -> aller(Onglet.PROGRAMME));
        a.active = onglet != Onglet.ACCUEIL;
        p.active = onglet != Onglet.PROGRAMME && onglet != Onglet.NOUVEAU;
        if (organisateur()) {
            Button j = bouton("Jeux", x0 + 5 + 2 * lo, y0 + 5, lo - 4, b -> aller(Onglet.JEUX));
            j.active = onglet != Onglet.JEUX;
        }
        if (etat() == null) return;
        switch (onglet) {
            case ACCUEIL -> initAccueil();
            case PROGRAMME -> initProgramme();
            case NOUVEAU -> initNouveau();
            case JEUX -> initJeux();
        }
    }

    // ---------- Accueil ----------

    private void initAccueil() {
        var e = etat();
        int y = y0 + 70;
        if (e.dedans()) bouton("Revenir en survie", x0 + 10, y, 155, b -> commande("event quitter"));
        else {
            Button r = bouton("Rejoindre l'événement", x0 + 10, y, 155, b -> commande("event rejoindre"));
            r.active = e.ouvert() || e.organisateur() || e.aide();
        }
        if (e.aide() && !e.organisateur()) {
            Button c = bouton(e.construction() ? "Arrêter de construire" : "Mode construction", x0 + 175, y, 155,
                    b -> commande("event construire"));
            c.active = e.dedans();
            return;
        }
        if (!e.organisateur()) return;
        bouton("Préparer (seul, créatif)", x0 + 175, y, 155, b -> commande("event preparer"));
        y += 26;
        if (e.ouvert()) bouton("Fermer l'événement", x0 + 10, y, 155, b -> commande("event fermer"));
        else bouton("Ouvrir à tout le monde", x0 + 10, y, 155, b -> commande("event ouvrir"));
        Button c = bouton(e.construction() ? "Arrêter de construire" : "Mode construction", x0 + 175, y, 155,
                b -> commande("event construire"));
        c.active = e.dedans();
        y += 26;
        Button s = bouton("Placer l'arrivée ici", x0 + 10, y, 155, b -> commande("event spawn"));
        s.active = e.dedans();
        // Inviter un joueur connecté à préparer avec toi
        List<String> enLigne = e.enLigne();
        if (invite >= enLigne.size()) invite = 0;
        Button choix = bouton(enLigne.isEmpty() ? "Personne d'autre en ligne" : "Aide : " + enLigne.get(invite) + " ▸",
                x0 + 175, y, 105, b -> { invite = (invite + 1) % Math.max(1, enLigne.size()); rebuildWidgets(); });
        choix.active = enLigne.size() > 1;
        Button inv = bouton("Inviter", x0 + 284, y, 46, b -> commande("event inviter " + enLigne.get(invite)));
        inv.active = !enLigne.isEmpty();
    }

    // ---------- Programme ----------

    private void initProgramme() {
        List<Soiree> liste = programme();
        if (organisateur()) {
            for (int i = 0; i < Math.min(liste.size(), 4); i++) {
                final int numero = i + 1;
                bouton("Retirer", x0 + L - 70, y0 + 40 + i * 38, 60, b -> commande("event annuler " + numero));
            }
            bouton("+ Nouvel événement", x0 + (L - 160) / 2, y0 + H - 28, 160, b -> {
                jour = LocalDate.now();
                mois = YearMonth.now();
                aller(Onglet.NOUVEAU);
            });
        }
    }

    // ---------- Nouvel événement ----------

    private void initNouveau() {
        int cx = x0 + 10, cy = y0 + 32;
        // Calendrier : mois précédent / suivant, puis les jours
        Button prec = bouton("<", cx, cy, 20, b -> { mois = mois.minusMonths(1); rebuildWidgets(); });
        prec.active = mois.isAfter(YearMonth.now());
        bouton(">", cx + 140, cy, 20, b -> { mois = mois.plusMonths(1); rebuildWidgets(); });
        int decalage = mois.atDay(1).getDayOfWeek().getValue() - 1;
        for (int d = 1; d <= mois.lengthOfMonth(); d++) {
            LocalDate date = mois.atDay(d);
            int case_ = decalage + d - 1;
            int bx = cx + (case_ % 7) * 23, by = cy + 36 + (case_ / 7) * 17;
            Button jb = addRenderableWidget(Button.builder(Component.literal(String.valueOf(d)), b -> {
                jour = date;
                rebuildWidgets();
            }).bounds(bx, by, 22, 16).build());
            jb.active = !date.isBefore(LocalDate.now()) && !date.equals(jour);
        }
        // Heure
        int hx = x0 + 185, hy = y0 + 44;
        bouton("-", hx, hy, 20, b -> { heure = (heure + 23) % 24; rebuildWidgets(); });
        bouton("+", hx + 60, hy, 20, b -> { heure = (heure + 1) % 24; rebuildWidgets(); });
        bouton("-", hx + 85, hy, 20, b -> { minute = (minute + 55) % 60; rebuildWidgets(); });
        bouton("+", hx + 125, hy, 20, b -> { minute = (minute + 5) % 60; rebuildWidgets(); });
        // Titre et description
        EditBox t = addRenderableWidget(new EditBox(font, hx, hy + 40, 145, 18, Component.literal("Titre")));
        t.setMaxLength(60);
        t.setHint(Component.literal("Soirée cache-cache"));
        t.setValue(titre);
        t.setResponder(v -> titre = v);
        EditBox ds = addRenderableWidget(new EditBox(font, hx, hy + 80, 145, 18, Component.literal("Description")));
        ds.setMaxLength(200);
        ds.setHint(Component.literal("Venez nombreux !"));
        ds.setValue(description);
        ds.setResponder(v -> description = v);
        Button pub = bouton("Publier", hx, y0 + H - 28, 70, b -> publier());
        pub.active = true;
        bouton("Annuler", hx + 75, y0 + H - 28, 70, b -> aller(Onglet.PROGRAMME));
    }

    private void publier() {
        String t = titre.replace("|", "/").trim();
        if (t.isEmpty()) return;
        String date = jour.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String h = String.format(Locale.ROOT, "%02d:%02d", heure, minute);
        String d = description.replace("\n", " ").trim();
        commande("event planifier " + date + " " + h + " " + t + (d.isEmpty() ? "" : " | " + d));
        titre = "";
        description = "";
        aller(Onglet.PROGRAMME);
    }

    // ---------- Jeux ----------

    private void initJeux() {
        var e = etat();
        int x = x0 + 10, y = y0 + 58;
        List<String> noms = e.participants();
        if (chercheur > noms.size()) chercheur = 0;
        bouton("Chercheur : " + (chercheur == 0 ? "au hasard" : noms.get(chercheur - 1)), x, y, 200,
                b -> { chercheur = (chercheur + 1) % (noms.size() + 1); rebuildWidgets(); });
        y += 26;
        bouton("-", x + 150, y, 20, b -> { secondes = Math.max(5, secondes - 5); rebuildWidgets(); });
        bouton("+", x + 200, y, 20, b -> { secondes = Math.min(300, secondes + 5); rebuildWidgets(); });
        y += 24;
        bouton("-", x + 150, y, 20, b -> { minutes = Math.max(1, minutes - 1); rebuildWidgets(); });
        bouton("+", x + 200, y, 20, b -> { minutes = Math.min(60, minutes + 1); rebuildWidgets(); });
        y += 34;
        Button lancer = bouton("Lancer le cache-cache", x, y, 155, b -> {
            String qui = chercheur == 0 ? "hasard" : noms.get(chercheur - 1);
            commande("cachecache lancer " + qui + " " + secondes + " " + minutes);
        });
        lancer.active = !e.jeuEnCours() && noms.size() >= 2;
        Button stop = bouton("Arrêter la partie", x + 165, y, 155, b -> commande("cachecache stop"));
        stop.active = e.jeuEnCours();
    }

    // ---------- Dessin ----------

    @Override
    public void tick() {
        super.tick();
        // Nouvel état reçu : on redessine les boutons (sauf pendant la saisie d'un événement)
        if (Recu.version != versionVue && onglet != Onglet.NOUVEAU) rebuildWidgets();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(x0, y0, x0 + L, y0 + H, 0xE0101418);
        g.fill(x0, y0, x0 + L, y0 + 1, VERT);
        super.extractRenderState(g, mouseX, mouseY, delta);
        var e = etat();
        if (e == null) {
            g.text(font, "Chargement…", x0 + 10, y0 + 40, GRIS);
            return;
        }
        switch (onglet) {
            case ACCUEIL -> dessinerAccueil(g, e);
            case PROGRAMME -> dessinerProgramme(g);
            case NOUVEAU -> dessinerNouveau(g);
            case JEUX -> dessinerJeux(g, e);
        }
    }

    private void dessinerAccueil(GuiGraphicsExtractor g, Paquets.Etat e) {
        g.text(font, e.ouvert() ? "L'événement est OUVERT" : "Pas d'événement ouvert pour le moment",
                x0 + 10, y0 + 36, e.ouvert() ? VERT : GRIS);
        g.text(font, (e.dedans() ? "Tu es dans le monde d'événement" + (e.construction() ? " (construction)" : "")
                : "Tu es en survie") + (e.aide() && !e.organisateur() ? " — invité à préparer" : ""), x0 + 10, y0 + 50, BLANC);
        int y = y0 + (e.organisateur() ? 152 : 100);
        g.text(font, "Participants (" + e.participants().size() + ") :", x0 + 10, y, OR);
        g.text(font, e.participants().isEmpty() ? "personne" : String.join(", ", e.participants()), x0 + 10, y + 12, GRIS);
        List<Soiree> p = programme();
        if (!p.isEmpty()) {
            g.text(font, "Prochain : " + p.get(0).titre() + " — " + lisible(p.get(0).date()), x0 + 10, y0 + H - 16, OR);
        }
    }

    private void dessinerProgramme(GuiGraphicsExtractor g) {
        List<Soiree> liste = programme();
        if (liste.isEmpty()) g.text(font, "Aucun événement prévu.", x0 + 10, y0 + 40, GRIS);
        for (int i = 0; i < Math.min(liste.size(), 4); i++) {
            Soiree s = liste.get(i);
            int y = y0 + 38 + i * 38;
            g.text(font, s.titre(), x0 + 10, y, OR);
            g.text(font, lisible(s.date()), x0 + 10, y + 11, BLANC);
            if (!s.description().isBlank()) g.text(font, font.plainSubstrByWidth(s.description(), L - 90), x0 + 10, y + 22, GRIS);
        }
        if (!organisateur()) g.text(font, "Les organisateurs programment les soirées.", x0 + 10, y0 + H - 16, GRIS);
    }

    private void dessinerNouveau(GuiGraphicsExtractor g) {
        int cx = x0 + 10, cy = y0 + 32;
        String nomMois = mois.getMonth().getDisplayName(TextStyle.FULL, Locale.FRENCH) + " " + mois.getYear();
        g.text(font, nomMois, cx + 80 - font.width(nomMois) / 2, cy + 6, BLANC);
        String[] j = {"L", "M", "M", "J", "V", "S", "D"};
        for (int i = 0; i < 7; i++) g.text(font, j[i], cx + i * 23 + 8, cy + 24, GRIS);
        // Le jour choisi est encadré en vert
        if (YearMonth.from(jour).equals(mois)) {
            int c = mois.atDay(1).getDayOfWeek().getValue() - 1 + jour.getDayOfMonth() - 1;
            int bx = cx + (c % 7) * 23, by = cy + 36 + (c / 7) * 17;
            g.fill(bx - 2, by - 2, bx + 24, by - 1, VERT);
            g.fill(bx - 2, by + 16, bx + 24, by + 17, VERT);
            g.fill(bx - 2, by - 2, bx - 1, by + 17, VERT);
            g.fill(bx + 23, by - 2, bx + 24, by + 17, VERT);
        }
        int hx = x0 + 185, hy = y0 + 44;
        g.text(font, "Heure", hx, hy - 12, OR);
        g.text(font, String.format(Locale.ROOT, "%02d", heure), hx + 33, hy + 6, BLANC);
        g.text(font, ":", hx + 78, hy + 6, BLANC);
        g.text(font, String.format(Locale.ROOT, "%02d", minute), hx + 108, hy + 6, BLANC);
        g.text(font, "Titre", hx, hy + 28, OR);
        g.text(font, "Description", hx, hy + 68, OR);
        g.text(font, jour.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRENCH))
                + String.format(Locale.ROOT, " à %02dh%02d", heure, minute), hx, hy + 104, VERT);
    }

    private void dessinerJeux(GuiGraphicsExtractor g, Paquets.Etat e) {
        int x = x0 + 10, y = y0 + 36;
        g.text(font, "Cache-cache" + (e.jeuEnCours() ? " — partie en cours" : ""), x, y, e.jeuEnCours() ? ROUGE : OR);
        g.text(font, "Joueurs dans le monde d'événement : " + e.participants().size(), x, y + 10, GRIS);
        g.text(font, "Temps pour se cacher", x, y + 54, BLANC);
        g.text(font, secondes + " s", x + 176, y + 54, BLANC);
        g.text(font, "Durée de la recherche", x, y + 78, BLANC);
        g.text(font, minutes + " min", x + 173, y + 78, BLANC);
        if (e.participants().size() < 2) g.text(font, "Il faut au moins 2 joueurs dans le monde d'événement.", x, y0 + H - 16, GRIS);
    }

    private static String lisible(String iso) {
        try {
            return ZonedDateTime.parse(iso).format(LISIBLE);
        } catch (Exception e) {
            return iso;
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
