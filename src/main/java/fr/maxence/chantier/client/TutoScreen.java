package fr.maxence.chantier.client;

import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Tutoriel en étapes : illustration à gauche, explications avec icônes à droite, Retour / Passer / Suivant. */
public class TutoScreen extends CadreScreen {
    /** Une ligne : icône (ou carré de couleur si couleur != 0, ou touche si touche != null) + texte. */
    private record Ligne(Item icone, int couleur, KeyMapping touche, String texte) {
        static Ligne de(Item icone, String texte) { return new Ligne(icone, 0, null, texte); }
        static Ligne couleur(int couleur, String texte) { return new Ligne(null, couleur, null, texte); }
        static Ligne touche(KeyMapping touche, String texte) { return new Ligne(null, 0, touche, texte); }
    }

    private record Etape(String titre, Item image, List<Ligne> lignes) {}

    private static List<Etape> etapes() {
        return List.of(
                new Etape("Bienvenue sur Chantier", Items.BRICKS, List.of(
                        Ligne.de(Items.CHEST, "Suis une construction : ce qu'il faut, et ce que tu as déjà."),
                        Ligne.de(Items.PAPER, "La liste vient d'un Google Sheets ou d'un schéma."),
                        Ligne.de(Items.PLAYER_HEAD, "Seul ou en groupe : chaque groupe a sa propre liste."),
                        Ligne.de(Items.SPYGLASS, "La liste reste affichée à gauche pendant que tu joues."))),
                new Etape("Créer un chantier", Items.NAME_TAG, List.of(
                        Ligne.de(Items.NAME_TAG, "Inventaire, bouton « Chantier », ou la touche H."),
                        Ligne.de(Items.WRITABLE_BOOK, "Donne-lui un nom, puis colle le lien Google Sheets."),
                        Ligne.de(Items.PLAYER_HEAD, "Ajoute tes amis connectés avec leur pseudo."),
                        Ligne.de(Items.BARRIER, "Quitter te retire, Supprimer efface le chantier."))),
                new Etape("Les coffres", Items.CHEST, List.of(
                        Ligne.de(Items.CHEST, "« Choisir les coffres », puis clic droit sur chaque coffre."),
                        Ligne.de(Items.OAK_DOOR, "Portes et leviers marchent toujours pendant ce temps."),
                        Ligne.de(Items.LEATHER_BOOTS, "Accroupi + clic droit sur un autre bloc pour finir."),
                        Ligne.de(Items.COMPASS, "Une flèche t'indique le coffre le plus proche."))),
                new Etape("La liste", Items.BOOK, List.of(
                        Ligne.de(Items.BOOK, "« Liste » montre tout ; ce qui est fini passe à la fin."),
                        Ligne.de(Items.IRON_PICKAXE, "« À récolter » : les matières premières à aller chercher."),
                        Ligne.de(Items.ENDER_CHEST, "Tes coffres et les inventaires des membres comptent."),
                        Ligne.de(Items.EMERALD, "À 100 %, le chantier se termine tout seul."))),
                new Etape("Schémas et galerie", Items.MAP, List.of(
                        Ligne.de(Items.MAP, "Glisse un .litematic, .schem ou .nbt dans la fenêtre."),
                        Ligne.de(Items.ITEM_FRAME, "« Partager » : tout le serveur le voit dans la galerie."),
                        Ligne.de(Items.PAINTING, "Glisse une capture d'écran sur une fiche pour l'illustrer."),
                        Ligne.de(Items.WRITABLE_BOOK, "« Modifier » change l'intitulé et la description."))),
                new Etape("Ce que veulent dire les couleurs", Items.GLASS, List.of(
                        Ligne.couleur(0xFF9EC0E8, "Couleur du bloc : encore à poser."),
                        Ligne.couleur(0xFFE04040, "Rouge : un autre bloc est à la place."),
                        Ligne.de(Items.SLIME_BALL, "Bien posé : le fantôme disparaît."),
                        Ligne.de(Items.SPYGLASS, "Seuls les blocs à moins de 40 blocs s'affichent."))),
                new Etape("Une couche à la fois", Items.SCAFFOLDING, List.of(
                        Ligne.de(Items.LADDER, "Couche - et Couche + : un seul étage affiché."),
                        Ligne.de(Items.COMPASS, "Avancer, Gauche, Monter… suivent ton regard."),
                        Ligne.de(Items.STRUCTURE_BLOCK, "« Tourner d'un quart » pour l'orienter."),
                        Ligne.de(Items.RECOVERY_COMPASS, "L'hologramme reste là, même après déconnexion."))),
                new Etape("Les touches", Items.OAK_SIGN, List.of(
                        Ligne.touche(Touches.OUVRIR, "Ouvrir Chantier."),
                        Ligne.touche(Touches.COUCHE_HAUT, "Hologramme : couche au-dessus."),
                        Ligne.touche(Touches.COUCHE_BAS, "Hologramme : couche en dessous."),
                        Ligne.de(Items.WRITABLE_BOOK, "À changer dans Options, Commandes, Chantier."))));
    }

    private final List<Etape> liste = etapes();

    public TutoScreen(Screen parent) {
        super("Chantier", parent);
    }

    @Override
    protected String titre() { return liste.get(etape).titre(); }

    @Override
    protected void init() {
        etapes = liste.size();
        info = "Étape " + (etape + 1) + " sur " + liste.size();
        cadre(Math.min(460, width - 8), 150);
        int y = Math.min(height - 26, py + ph + 10);
        addRenderableWidget(Button.builder(Component.literal(etape == 0 ? "Fermer" : "Retour"), b -> {
            if (etape == 0) onClose(); else { etape--; rebuildWidgets(); }
        }).bounds(px, y, 110, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Passer"), b -> onClose())
                .bounds(width / 2 - 40, y, 80, 20).build());
        boolean derniere = etape == liste.size() - 1;
        addRenderableWidget(Button.builder(Component.literal(derniere ? "Terminer" : "Suivant"), b -> {
            if (derniere) onClose(); else { etape++; rebuildWidgets(); }
        }).bounds(px + pw - 110, y, 110, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        Etape e = liste.get(etape);

        // Illustration : une petite scène sur fond de ciel
        int ix = px + 12, iy = py + 12, il = Math.min(150, pw / 3), ih = ph - 24;
        g.fill(ix - 1, iy - 1, ix + il + 1, iy + ih + 1, OMBRE);
        g.fillGradient(ix, iy, ix + il, iy + ih, 0xFF6F9AD0, 0xFFA9C6EA);
        g.enableScissor(ix, iy, ix + il, iy + ih);
        illustrer(g, etape, ix, iy, il, ih);
        g.disableScissor();

        // Explications
        int tx = ix + il + 12, tl = px + pw - 12 - tx - 26;
        int y = py + 14;
        for (Ligne l : e.lignes()) {
            if (l.touche() != null) {
                String nom = l.touche().getKey().getDisplayName().getString();
                int lt = Math.max(20, font.width(nom) + 8);
                creux(g, tx, y, lt, 20, 0xFF8B8B8B);
                g.text(font, nom, tx + lt / 2 - font.width(nom) / 2, y + 6, 0xFFFFFFFF);
                texte(g, font, l.texte(), tx + lt + 6, y + 6, TEXTE);
            } else {
                if (l.couleur() != 0) {
                    creux(g, tx, y, 20, 20, CREUX);
                    carre(g, tx + 5, y + 5, l.couleur());
                } else {
                    icone(g, new ItemStack(l.icone()), tx, y);
                }
                List<String> morceaux = GalerieScreen.couper(font, l.texte(), tl, 2);
                int ty = morceaux.size() == 1 ? y + 6 : y + 1;
                for (String m : morceaux) { texte(g, font, m, tx + 26, ty, TEXTE); ty += 10; }
            }
            y += 30;
        }
    }

    // ---------- Illustrations ----------

    private void illustrer(GuiGraphicsExtractor g, int n, int x, int y, int l, int h) {
        switch (n) {
            case 0 -> maison().dessinerCentre(g, x, y, l, h, 11);
            case 1 -> maquetteCreation(g, x, y, l, h);
            case 2 -> coffres().dessinerCentre(g, x, y, l, h, 12);
            case 3 -> maquetteListe(g, x, y, l, h);
            case 4 -> maquetteGalerie(g, x, y, l, h);
            case 5 -> mur().dessinerCentre(g, x, y, l, h, 12);
            case 6 -> couches().dessinerCentre(g, x, y, l, h, 12);
            default -> maquetteTouches(g, x, y, l, h);
        }
    }

    /** Petite maison en cours : murs posés, toit encore en fantôme. */
    private static Scene maison() {
        Scene s = new Scene().ilot(5, 5);
        for (int yy = 0; yy < 2; yy++) for (int xx = 1; xx <= 3; xx++) for (int zz = 1; zz <= 3; zz++) {
            boolean bord = xx == 1 || xx == 3 || zz == 1 || zz == 3;
            if (!bord) continue;
            boolean coin = (xx == 1 || xx == 3) && (zz == 1 || zz == 3);
            if (!coin && xx == 2 && zz == 3 && yy < 2) continue; // la porte
            if (coin) s.bloc(xx, yy, zz, Scene.BUCHE_DESSUS, Scene.BUCHE); else s.bloc(xx, yy, zz, Scene.PLANCHES);
        }
        for (int xx = 1; xx <= 3; xx++) for (int zz = 1; zz <= 3; zz++) s.fantome(xx, 2, zz, Scene.PLANCHES);
        return s;
    }

    /** Trois coffres, deux choisis (contour jaune). */
    private static Scene coffres() {
        Scene s = new Scene().ilot(5, 3);
        s.uni(1, 0, 1, 0xFFA0702C, 0xFFFFE070);
        s.uni(2, 0, 1, 0xFFA0702C, 0xFFFFE070);
        s.uni(3, 0, 1, 0xFFA0702C, 0xFF3A2A10);
        s.bloc(4, 0, 2, Scene.PLANCHES);
        return s;
    }

    /** Un mur : blocs posés, fantômes à poser, un bloc faux en rouge. */
    private static Scene mur() {
        Scene s = new Scene().ilot(5, 3);
        for (int yy = 0; yy < 3; yy++) {
            s.bloc(0, yy, 1, Scene.BUCHE_DESSUS, Scene.BUCHE);
            s.bloc(4, yy, 1, Scene.BUCHE_DESSUS, Scene.BUCHE);
        }
        s.bloc(1, 0, 1, Scene.BRIQUES);
        s.faux(2, 0, 1, Scene.PAVES);
        s.bloc(3, 0, 1, Scene.BRIQUES);
        s.bloc(1, 1, 1, Scene.BRIQUES);
        s.fantome(2, 1, 1, Scene.BRIQUES);
        s.fantome(3, 1, 1, Scene.BRIQUES);
        for (int xx = 1; xx <= 3; xx++) s.fantome(xx, 2, 1, Scene.BRIQUES);
        return s;
    }

    /** Une tour : la couche du bas posée, la couche en cours en fantôme, le reste à peine visible. */
    private static Scene couches() {
        Scene s = new Scene().ilot(4, 4);
        for (int xx = 0; xx < 3; xx++) for (int zz = 0; zz < 3; zz++) {
            s.bloc(xx, 0, zz, Scene.BRIQUES);
            s.fantome(xx, 1, zz, Scene.BRIQUES);
            s.vide(xx, 2, zz, 0x50FFFFFF);
            s.vide(xx, 3, zz, 0x50FFFFFF);
        }
        return s;
    }

    private static final Identifier BOUTON = Identifier.withDefaultNamespace("widget/button");

    private void faux_bouton(GuiGraphicsExtractor g, String texte, int x, int y, int l) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, BOUTON, x, y, l, 20);
        g.text(font, texte, x + l / 2 - font.width(texte) / 2, y + 6, 0xFFFFFFFF);
    }

    /** Mini écran « Mon chantier ». */
    private void maquetteCreation(GuiGraphicsExtractor g, int x, int y, int l, int h) {
        int px0 = x + 8, py0 = y + h / 2 - 44, lp = l - 16;
        panneau(g, px0, py0, lp, 88);
        texte(g, font, "Mon chantier", px0 + 8, py0 + 8, TEXTE);
        icone(g, new ItemStack(Items.NAME_TAG), px0 + 8, py0 + 22);
        creux(g, px0 + 32, py0 + 24, lp - 40, 16, 0xFF000000);
        g.text(font, "Ma maison", px0 + 36, py0 + 28, 0xFFE0E0E0);
        icone(g, new ItemStack(Items.PLAYER_HEAD), px0 + 8, py0 + 46);
        texte(g, font, "Alex, Steve", px0 + 34, py0 + 52, DOUX);
        faux_bouton(g, "Créer", px0 + lp - 58, py0 + 66, 50);
    }

    /** Mini liste affichée à gauche de l'écran en jeu. */
    private void maquetteListe(GuiGraphicsExtractor g, int x, int y, int l, int h) {
        int bx = x + 10, by = y + h / 2 - 50, bl = l - 20;
        g.fill(bx, by, bx + bl, by + 100, 0xC0000000);
        g.text(font, "Ma maison — 62 %", bx + 4, by + 4, 0xFFFFD24A);
        g.text(font, "Coffre ↗ 12 m", bx + 4, by + 15, 0xFFAAAAAA);
        Item[] objets = {Items.OAK_PLANKS, Items.COBBLESTONE, Items.OAK_LOG, Items.GLASS};
        int[] pourcent = {100, 80, 45, 10};
        for (int i = 0; i < objets.length; i++) {
            int ly = by + 28 + i * 18;
            g.item(new ItemStack(objets[i]), bx + 4, ly);
            int barre = bl - 60;
            g.fill(bx + 24, ly + 6, bx + 24 + barre, ly + 10, 0xFF333333);
            g.fill(bx + 24, ly + 6, bx + 24 + barre * pourcent[i] / 100, ly + 10, pourcent[i] >= 100 ? 0xFF55FF55 : 0xFFFFD24A);
            String t = pourcent[i] + " %";
            g.text(font, t, bx + bl - 4 - font.width(t), ly + 4, pourcent[i] >= 100 ? 0xFF55FF55 : 0xFFFFFFFF);
        }
    }

    /** Deux cartes de la galerie avec leur aperçu. */
    private void maquetteGalerie(GuiGraphicsExtractor g, int x, int y, int l, int h) {
        String[] noms = {"Maison", "Tour"};
        String[] auteurs = {"par Steve", "par Alex"};
        for (int i = 0; i < 2; i++) {
            int cx = x + 8, cy = y + h / 2 - 46 + i * 48, cl = l - 16;
            creux(g, cx, cy, cl, 44, 0xFFD4D4D4);
            g.fill(cx + 3, cy + 3, cx + 41, cy + 41, OMBRE);
            g.fillGradient(cx + 4, cy + 4, cx + 40, cy + 40, 0xFF6F9AD0, 0xFFA9C6EA);
            g.enableScissor(cx + 4, cy + 4, cx + 40, cy + 40);
            (i == 0 ? maison() : couches()).dessinerCentre(g, cx + 4, cy + 4, 36, 36, 5);
            g.disableScissor();
            texte(g, font, noms[i], cx + 46, cy + 8, TEXTE);
            texte(g, font, auteurs[i], cx + 46, cy + 19, DOUX);
            faux_bouton(g, "Voir", cx + cl - 34, cy + 22, 30);
        }
    }

    /** Les touches, en grand. */
    private void maquetteTouches(GuiGraphicsExtractor g, int x, int y, int l, int h) {
        KeyMapping[] touches = {Touches.OUVRIR, Touches.COUCHE_HAUT, Touches.COUCHE_BAS};
        for (int i = 0; i < touches.length; i++) {
            String nom = touches[i].getKey().getDisplayName().getString();
            int lt = Math.max(40, font.width(nom) + 16);
            faux_bouton(g, nom, x + l / 2 - lt / 2, y + h / 2 - 40 + i * 28, lt);
        }
    }
}
