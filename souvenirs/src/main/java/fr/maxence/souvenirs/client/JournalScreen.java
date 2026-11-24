package fr.maxence.souvenirs.client;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import fr.maxence.souvenirs.Entree;
import fr.maxence.souvenirs.Paquets;
import fr.maxence.souvenirs.Recu;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Le journal du serveur, présenté comme un livre ouvert :
 * page de gauche = les souvenirs (du plus récent au plus ancien, par jour), page de droite = le souvenir choisi.
 */
public class JournalScreen extends Screen {
    private static final int CUIR = 0xFF5B3A1E;
    private static final int CUIR_FONCE = 0xFF3A2412;
    private static final int PAGE = 0xFFF3E7C9;
    private static final int ENCRE = 0xFF3B2A1A;
    private static final int ENCRE_DOUCE = 0xFF7A6248;
    private static final int ROUGE = 0xFF9A2A1A;
    private static final int LIGNE = 18;

    private static final String[][] FILTRES = {
            {"tout", "Tout"}, {"exploit", "Exploits"}, {"mort", "Morts"},
            {"boss", "Boss & succès"}, {"chantier", "Chantiers"}, {"perso", "Perso"}};

    private static final SimpleDateFormat JOUR = new SimpleDateFormat("EEEE d MMMM yyyy", Locale.FRENCH);
    private static final SimpleDateFormat HEURE = new SimpleDateFormat("HH:mm", Locale.FRENCH);

    private String filtre = "tout";
    private String choisi;
    private int defilement;
    private int versionVue = -1;
    private boolean confirmerSuppression;
    private String message = "";
    private final Map<String, Component> textes = new HashMap<>();

    // Géométrie du livre
    private int bx, by, bl, bh, gx, gl, dx, dl, haut, bas;

    /** Une ligne de la liste : titre du jour (entree == null) ou souvenir. */
    private record Ligne(String jour, Entree entree) {}

    public JournalScreen() {
        super(Component.literal("Journal du serveur"));
    }

    private List<Entree> visibles() {
        List<Entree> r = new ArrayList<>();
        List<Entree> toutes = Recu.entrees;
        for (int i = toutes.size() - 1; i >= 0; i--) {
            Entree e = toutes.get(i);
            boolean ok = switch (filtre) {
                case "tout" -> true;
                case "boss" -> e.type().equals("boss") || e.type().equals("succes");
                default -> e.type().equals(filtre);
            };
            if (ok) r.add(e);
        }
        return r;
    }

    /** Souvenirs groupés par jour (une ligne de titre à chaque nouveau jour). */
    private List<Ligne> lignes() {
        List<Ligne> r = new ArrayList<>();
        String dernier = "";
        for (Entree e : visibles()) {
            String jour = JOUR.format(new Date(e.date()));
            if (!jour.equals(dernier)) { r.add(new Ligne(jour, null)); dernier = jour; }
            r.add(new Ligne(jour, e));
        }
        return r;
    }

    private Entree entreeChoisie() {
        if (choisi == null) return null;
        for (Entree e : Recu.entrees) if (e.id().equals(choisi)) return e;
        return null;
    }

    private int lignesVisibles() { return Math.max(1, (bas - haut) / LIGNE); }

    /** Bouton invisible sur une ligne de la liste. */
    private static final class Zone extends Button {
        Zone(Button.Builder b) { super(b); }

        @Override
        protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
            if (isHovered) g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x20A0703A);
        }
    }

    @Override
    protected void init() {
        bl = Math.min(460, width - 12);
        bh = Math.min(250, height - 40);
        bx = (width - bl) / 2;
        by = Math.max(4, (height - bh) / 2 - 10);
        gx = bx + 14; gl = bl / 2 - 22;
        dx = bx + bl / 2 + 8; dl = bl / 2 - 22;
        haut = by + 50; bas = by + bh - 12;
        versionVue = Recu.version;
        if (choisi == null && !visibles().isEmpty()) choisi = visibles().get(0).id();

        // Filtres : 2 rangées de 3
        int fl = (gl - 4) / 3;
        for (int i = 0; i < FILTRES.length; i++) {
            String cle = FILTRES[i][0];
            Button b = addRenderableWidget(Button.builder(Component.literal(FILTRES[i][1]), x -> {
                filtre = cle;
                defilement = 0;
                rebuildWidgets();
            }).bounds(gx + (i % 3) * (fl + 2), by + 18 + (i / 3) * 15, fl, 14).build());
            b.active = !cle.equals(filtre);
        }

        // Lignes cliquables
        List<Ligne> liste = lignes();
        defilement = Math.max(0, Math.min(defilement, Math.max(0, liste.size() - lignesVisibles())));
        int y = haut;
        for (int i = defilement; i < liste.size() && y + LIGNE <= bas; i++, y += LIGNE) {
            Entree e = liste.get(i).entree();
            if (e == null) continue;
            addRenderableWidget(Button.builder(Component.literal(""), x -> {
                choisi = e.id();
                confirmerSuppression = false;
                message = "";
                rebuildWidgets();
            }).bounds(gx - 2, y, gl + 4, LIGNE).build(Zone::new));
        }

        // Page de droite : actions sur le souvenir choisi
        Entree e = entreeChoisie();
        if (e != null) {
            int yb = by + bh - 34;
            if (!e.image()) {
                addRenderableWidget(Button.builder(Component.literal("Dernière capture (F2)"), x -> {
                    List<Path> captures = Photos.captures();
                    message = captures.isEmpty() ? "Aucune capture : appuie sur F2 en jeu d'abord." : Photos.envoyer(captures.get(0), e.id());
                }).bounds(dx, yb - 22, dl, 20).build());
            }
            addRenderableWidget(Button.builder(Component.literal(e.image() ? "Changer la capture" : "Choisir une capture"),
                    x -> minecraft.gui.setScreen(new CapturesScreen(this, e.id()))).bounds(dx, yb, dl - 76, 20).build());
            addRenderableWidget(Button.builder(Component.literal(confirmerSuppression ? "Sûr ?" : "Supprimer"), x -> {
                if (confirmerSuppression) {
                    ClientPacketDistributor.sendToServer(new Paquets.Demande("supprimer", e.id()));
                    choisi = null;
                }
                confirmerSuppression = !confirmerSuppression;
                rebuildWidgets();
            }).bounds(dx + dl - 72, yb, 72, 20).build());
        }

        // Sous le livre
        int yl = Math.min(height - 22, by + bh + 4);
        addRenderableWidget(Button.builder(Component.literal("Nouveau souvenir"), x -> minecraft.gui.setScreen(new NouveauScreen(this)))
                .bounds(width / 2 - 125, yl, 120, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Fermer"), x -> onClose())
                .bounds(width / 2 + 5, yl, 120, 20).build());
    }

    @Override
    public void tick() {
        super.tick();
        if (Recu.version != versionVue) rebuildWidgets(); // la liste vient d'arriver ou a changé
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        int avant = defilement;
        defilement = Math.max(0, Math.min(Math.max(0, lignes().size() - lignesVisibles()), defilement - (int) Math.signum(vertical) * 2));
        if (avant != defilement) rebuildWidgets();
        return true;
    }

    /** Le livre est dessiné sous les boutons. */
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xA0000000);
        g.fill(bx - 1, by - 1, bx + bl + 1, by + bh + 1, CUIR_FONCE);
        g.fill(bx, by, bx + bl, by + bh, CUIR);
        int milieu = bx + bl / 2;
        g.fill(bx + 6, by + 5, milieu - 1, by + bh - 5, PAGE);
        g.fill(milieu + 1, by + 5, bx + bl - 6, by + bh - 5, PAGE);
        for (int i = 0; i < 6; i++) { // ombre de la reliure
            int a = (6 - i) * 0x0C;
            g.fill(milieu - 1 - i, by + 5, milieu - i, by + bh - 5, a << 24);
            g.fill(milieu + 1 + i, by + 5, milieu + 2 + i, by + bh - 5, a << 24);
        }
        dessinerGauche(g);
        dessinerDroite(g);
    }

    private void dessinerGauche(GuiGraphicsExtractor g) {
        texte(g, "Journal du serveur", gx, by + 8, ENCRE);
        List<Ligne> liste = lignes();
        if (liste.isEmpty()) {
            texte(g, "Rien ici pour l'instant :", gx, haut + 4, ENCRE_DOUCE);
            texte(g, "le journal se remplit tout seul.", gx, haut + 14, ENCRE_DOUCE);
            return;
        }
        int y = haut;
        for (int i = defilement; i < liste.size() && y + LIGNE <= bas; i++, y += LIGNE) {
            Ligne l = liste.get(i);
            if (l.entree() == null) {
                String jour = l.jour().substring(0, 1).toUpperCase() + l.jour().substring(1);
                texte(g, font.plainSubstrByWidth(jour, gl), gx, y + 6, ENCRE_DOUCE);
                g.fill(gx, y + 16, gx + gl, y + 17, 0x40000000 | (ENCRE_DOUCE & 0xFFFFFF));
                continue;
            }
            Entree e = l.entree();
            if (e.id().equals(choisi)) g.fill(gx - 2, y, gx + gl + 2, y + LIGNE, 0x40C08040);
            g.item(icone(e), gx, y + 1);
            String heure = HEURE.format(new Date(e.date()));
            texte(g, heure, gx + gl - font.width(heure), y + 5, ENCRE_DOUCE);
            String titre = composant(e.titre()).getString();
            texte(g, font.plainSubstrByWidth(titre, gl - 22 - font.width(heure) - 4), gx + 19, y + 5,
                    e.type().equals("mort") ? ROUGE : ENCRE);
        }
        if (liste.size() > lignesVisibles()) {
            String pos = (defilement + 1) + "–" + Math.min(liste.size(), defilement + lignesVisibles()) + " / " + liste.size();
            texte(g, pos, gx + gl - font.width(pos), by + 8, ENCRE_DOUCE);
        }
    }

    private void dessinerDroite(GuiGraphicsExtractor g) {
        Entree e = entreeChoisie();
        if (e == null) {
            texte(g, "Choisis un souvenir à gauche.", dx, by + 12, ENCRE_DOUCE);
            return;
        }
        // Icône en grand + titre
        g.pose().pushMatrix();
        g.pose().translate(dx, by + 8);
        g.pose().scale(2f, 2f);
        g.item(icone(e), 0, 0);
        g.pose().popMatrix();
        int y = by + 10;
        for (String l : couper(font, composant(e.titre()).getString(), dl - 38, 2)) { texte(g, l, dx + 38, y, ENCRE); y += 10; }
        y = by + 44;
        texte(g, font.plainSubstrByWidth("par " + e.joueur() + " · " + JOUR.format(new Date(e.date())) + " à "
                + HEURE.format(new Date(e.date())), dl), dx, y, ENCRE_DOUCE);
        y += 10;
        if (!e.lieu().isEmpty()) { texte(g, e.lieu(), dx, y, ENCRE_DOUCE); y += 10; }
        y += 4;
        for (String l : couper(font, composant(e.texte()).getString(), dl, 3)) { texte(g, l, dx, y, ENCRE); y += 10; }

        // Capture d'écran
        int ph = by + bh - 36 - (e.image() ? 0 : 22) - y - 4;
        if (ph > 20) {
            if (e.image()) {
                Photos.Texture t = Photos.texture(e.id());
                if (t == null) {
                    texte(g, "Chargement de la capture…", dx, y + 4, ENCRE_DOUCE);
                } else {
                    float echelle = Math.min(dl / (float) t.largeur(), ph / (float) t.hauteur());
                    int w = Math.round(t.largeur() * echelle), h = Math.round(t.hauteur() * echelle);
                    int x0 = dx + (dl - w) / 2;
                    g.fill(x0 - 2, y + 2, x0 + w + 2, y + h + 6, 0xFFFFFFFF); // cadre photo
                    g.blit(RenderPipelines.GUI_TEXTURED, t.id(), x0, y + 4, 0f, 0f, w, h, t.largeur(), t.hauteur(), t.largeur(), t.hauteur());
                }
            } else {
                g.fill(dx, y + 2, dx + dl, y + ph, 0x18000000);
                String aide = "Pas encore de capture";
                texte(g, aide, dx + dl / 2 - font.width(aide) / 2, y + ph / 2 - 8, ENCRE_DOUCE);
                String aide2 = "(ou glisse une image ici)";
                texte(g, aide2, dx + dl / 2 - font.width(aide2) / 2, y + ph / 2 + 2, ENCRE_DOUCE);
            }
        }
        if (!message.isEmpty()) texte(g, font.plainSubstrByWidth(message, dl), dx, by + bh - 10 - 2, ROUGE);
    }

    /** Glisser une image sur le livre : elle devient la capture du souvenir choisi. */
    @Override
    public void onFilesDrop(List<Path> fichiers) {
        Entree e = entreeChoisie();
        if (e == null || fichiers.isEmpty()) { message = "Choisis d'abord un souvenir."; return; }
        message = Photos.envoyer(fichiers.get(0), e.id());
    }

    // ---------- Outils ----------

    private ItemStack icone(Entree e) {
        Identifier id = Identifier.tryParse(e.icone());
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return new ItemStack(Items.WRITABLE_BOOK);
        return new ItemStack(BuiltInRegistries.ITEM.getValue(id));
    }

    /** JSON -> texte Minecraft (traduit dans la langue du joueur). */
    private Component composant(String json) {
        return textes.computeIfAbsent(json, k -> {
            try {
                com.mojang.serialization.DynamicOps<com.google.gson.JsonElement> ops = minecraft.level != null
                        ? minecraft.level.registryAccess().createSerializationContext(JsonOps.INSTANCE) : JsonOps.INSTANCE;
                return ComponentSerialization.CODEC.parse(ops, JsonParser.parseString(k)).result().orElse(Component.literal(k));
            } catch (RuntimeException ex) {
                return Component.literal(k);
            }
        });
    }

    private void texte(GuiGraphicsExtractor g, String s, int x, int y, int couleur) {
        g.text(font, s, x, y, couleur, false);
    }

    static List<String> couper(Font font, String texte, int largeur, int maxLignes) {
        List<String> lignes = new ArrayList<>();
        StringBuilder c = new StringBuilder();
        for (String mot : texte.strip().split("\\s+")) {
            if (mot.isEmpty()) continue;
            String essai = c.isEmpty() ? mot : c + " " + mot;
            if (font.width(essai) <= largeur) { c.setLength(0); c.append(essai); continue; }
            if (!c.isEmpty()) lignes.add(c.toString());
            c.setLength(0);
            c.append(font.width(mot) <= largeur ? mot : font.plainSubstrByWidth(mot, largeur));
        }
        if (!c.isEmpty()) lignes.add(c.toString());
        if (lignes.size() > maxLignes) {
            List<String> r = new ArrayList<>(lignes.subList(0, maxLignes));
            r.set(maxLignes - 1, font.plainSubstrByWidth(r.get(maxLignes - 1), largeur - font.width("…")) + "…");
            return r;
        }
        return lignes;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
