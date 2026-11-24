package fr.maxence.chantier.client;

import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
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
                        Ligne.de(Items.NAME_TAG, "Inventaire, bouton « Chantier » (ou la touche du menu)."),
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
                        Ligne.de(Items.LIME_DYE, "Bien posé : le fantôme disparaît."),
                        Ligne.de(Items.SPYGLASS, "Seuls les blocs à moins de 40 blocs s'affichent."))),
                new Etape("Une couche à la fois", Items.SCAFFOLDING, List.of(
                        Ligne.de(Items.LADDER, "Couche - et Couche + : un seul étage affiché."),
                        Ligne.de(Items.COMPASS, "Avancer, Gauche, Monter… suivent ton regard."),
                        Ligne.de(Items.STRUCTURE_BLOCK, "« Tourner d'un quart » pour l'orienter."),
                        Ligne.de(Items.RECOVERY_COMPASS, "L'hologramme reste là, même après déconnexion."))),
                new Etape("Les touches", Items.OAK_SIGN, List.of(
                        Ligne.touche(ChantierClient.OUVRIR, "Ouvrir Chantier."),
                        Ligne.touche(ChantierClient.COUCHE_HAUT, "Hologramme : couche au-dessus."),
                        Ligne.touche(ChantierClient.COUCHE_BAS, "Hologramme : couche en dessous."),
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

        // Illustration : un gros objet sur fond de ciel
        int ix = px + 12, iy = py + 12, il = Math.min(130, pw / 3), ih = ph - 24;
        g.fill(ix - 1, iy - 1, ix + il + 1, iy + ih + 1, OMBRE);
        g.fillGradient(ix, iy, ix + il, iy + ih, 0xFF6F9AD0, 0xFFA9C6EA);
        g.fill(ix, iy + ih - 16, ix + il, iy + ih, 0xFF5D9B3A);       // herbe
        g.fill(ix, iy + ih - 10, ix + il, iy + ih, 0xFF8B5E3C);       // terre
        g.pose().pushMatrix();
        g.pose().translate(ix + il / 2f - 32, iy + ih / 2f - 40);
        g.pose().scale(4f, 4f);
        g.item(new ItemStack(e.image()), 0, 0);
        g.pose().popMatrix();

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
}
