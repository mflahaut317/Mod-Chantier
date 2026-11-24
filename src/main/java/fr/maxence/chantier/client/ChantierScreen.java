package fr.maxence.chantier.client;

import fr.maxence.chantier.ActionPayload;
import fr.maxence.chantier.ConfigPayload;
import fr.maxence.chantier.ConfigRecue;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Écran Chantier. Sans chantier : on en crée un. Avec un chantier : lien, titre, coffres, membres, affichage.
 * Ouvert par le bouton « Chantier » de l'inventaire ou la commande /chantier.
 */
public class ChantierScreen extends CadreScreen {
    private static final int LARGEUR = 290;
    private static final int CHAMP = 66; // décalage des champs (icône + étiquette à gauche)

    private EditBox champTitre;
    private EditBox champLien;
    private EditBox champPseudo;
    private Button boutonMasquer;
    private Button boutonSelection;
    private boolean confirmerArret = false;
    private ConfigPayload dernierAffiche = null;
    /** Mode affiché : null = pas encore de réponse du serveur. */
    private Boolean modeChantier = null;
    /** Message affiché après un glisser-déposer (remplacé par la réponse du serveur). */
    private String messageLocal = "";

    private int gauche;
    private int haut;

    public ChantierScreen() {
        super("Mon chantier", null);
    }

    private static void envoyer(String action, String valeur) {
        ClientPacketDistributor.sendToServer(new ActionPayload(action, valeur));
    }

    private static boolean dansChantier() {
        ConfigPayload c = ConfigRecue.get();
        return c != null && c.dansChantier();
    }

    @Override
    protected void init() {
        modeChantier = dansChantier();
        cadre(LARGEUR + 24, modeChantier ? 210 : 128);
        gauche = px + 12;
        haut = py + 12;
        ConfigPayload c = ConfigRecue.get();
        info = c != null && modeChantier ? c.pourcent() + " %" : "";
        champPseudo = null;
        boutonMasquer = null;
        boutonSelection = null;
        if (modeChantier) initChantier(); else initCreation();

        int bas = py + ph - 30;
        addRenderableWidget(Button.builder(Component.literal("Fermer"), b -> onClose())
                .bounds(modeChantier ? gauche + 246 : gauche + LARGEUR - 92, bas, modeChantier ? 44 : 92, 20).build());
        addRenderableWidget(Button.builder(Component.literal("?"), b -> minecraft.gui.setScreen(new TutoScreen(this)))
                .bounds(px + pw - 20, hautTitre(), 20, 20).build());

        dernierAffiche = null;
        remplirDepuisConfig();
        envoyer("config", ""); // demande les réglages à jour au serveur
    }

    /** Pas encore de chantier : un titre et « Créer ». */
    private void initCreation() {
        int x = gauche + CHAMP;
        champTitre = new EditBox(font, x, haut + 24, 154, 18, Component.literal("Titre"));
        champTitre.setMaxLength(60);
        champTitre.setHint(Component.literal("Ex. Underground Farm"));
        addRenderableWidget(champTitre);
        addRenderableWidget(Button.builder(Component.literal("Créer"),
                b -> envoyer("creer", champTitre.getValue())).bounds(x + 159, haut + 23, 65, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Comment ça marche ?"),
                b -> minecraft.gui.setScreen(new TutoScreen(this))).bounds(gauche, py + ph - 30, 130, 20).build());
        champLien = null;
    }

    private void initChantier() {
        int x = gauche + CHAMP;

        champTitre = new EditBox(font, x, haut, 154, 18, Component.literal("Titre"));
        champTitre.setMaxLength(60);
        addRenderableWidget(champTitre);
        addRenderableWidget(Button.builder(Component.literal("OK"),
                b -> envoyer("titre", champTitre.getValue())).bounds(x + 159, haut - 1, 65, 20).build());

        champLien = null; // le lien Google Sheets est dans « Importer une liste »

        boutonSelection = addRenderableWidget(Button.builder(Component.literal("Choisir les coffres"), b -> {
            if (enSelection()) {
                envoyer("selection_off", "");
            } else {
                envoyer("selection_on", "");
                onClose(); // ferme l'écran pour pouvoir cliquer sur les coffres
            }
        }).bounds(x, haut + 25, 154, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Vider"),
                b -> envoyer("vider_coffres", "")).bounds(x + 159, haut + 25, 65, 20).build());

        champPseudo = new EditBox(font, x, haut + 64, 154, 18, Component.literal("Pseudo"));
        champPseudo.setMaxLength(16);
        champPseudo.setHint(Component.literal("Pseudo d'un joueur connecté"));
        addRenderableWidget(champPseudo);
        addRenderableWidget(Button.builder(Component.literal("Ajouter"), b -> {
            envoyer("ajouter", champPseudo.getValue());
            champPseudo.setValue("");
        }).bounds(x + 159, haut + 63, 65, 20).build());

        int ya = haut + 102;
        addRenderableWidget(Button.builder(Component.literal("Relire"),
                b -> envoyer("actualiser", "")).bounds(gauche, ya, 70, 20).build());
        boutonMasquer = addRenderableWidget(Button.builder(Component.literal("Masquer"),
                b -> envoyer(estMasque() ? "afficher" : "masquer", "")).bounds(gauche + 73, ya, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Quitter"),
                b -> envoyer("quitter", "")).bounds(gauche + 146, ya, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Supprimer"), b -> {
            if (confirmerArret) {
                envoyer("arreter", "");
                confirmerArret = false;
                b.setMessage(Component.literal("Supprimer"));
            } else {
                confirmerArret = true;
                b.setMessage(Component.literal("Sûr ?"));
            }
        }).bounds(gauche + 219, ya, 71, 20).build());

        int bas = py + ph - 30;
        addRenderableWidget(Button.builder(Component.literal("Liste"),
                b -> minecraft.gui.setScreen(new ListeScreen(this)))
                .bounds(gauche, bas, 52, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Importer une liste"),
                b -> minecraft.gui.setScreen(new SourceScreen(this)))
                .bounds(gauche + 56, bas, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Sur le terrain"),
                b -> minecraft.gui.setScreen(new HoloScreen(this)))
                .bounds(gauche + 160, bas, 82, 20).build());
    }

    private boolean enSelection() {
        ConfigPayload c = ConfigRecue.get();
        return c != null && c.selection();
    }

    private boolean estMasque() {
        ConfigPayload c = ConfigRecue.get();
        return c != null && c.masque();
    }

    /** Recopie les réglages reçus dans les champs (sans écraser ce que le joueur est en train de taper). */
    private void remplirDepuisConfig() {
        ConfigPayload c = ConfigRecue.get();
        if (c == null || c == dernierAffiche) return;
        dernierAffiche = c;
        messageLocal = "";
        if (!modeChantier) return;
        if (!champTitre.isFocused()) champTitre.setValue(c.titre());
        if (champLien != null && !champLien.isFocused()) champLien.setValue(c.lien());
        if (boutonMasquer != null) boutonMasquer.setMessage(Component.literal(c.masque() ? "Afficher" : "Masquer"));
        if (boutonSelection != null) {
            boutonSelection.setMessage(Component.literal(c.selection() ? "Terminer la sélection" : "Choisir les coffres"));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!ReglagesClient.tutoVu()) { // première ouverture : le tutoriel d'abord
            ReglagesClient.marquerTutoVu();
            minecraft.gui.setScreen(new TutoScreen(this));
            return;
        }
        // Chantier créé, quitté ou supprimé : on reconstruit l'écran dans le bon mode.
        if (modeChantier != null && modeChantier != dansChantier()) {
            rebuildWidgets();
            return;
        }
        remplirDepuisConfig();
    }

    /** Icône + étiquette à gauche d'une ligne. */
    private void etiquette(GuiGraphicsExtractor g, ItemStack icone, String texte, int y) {
        icone(g, icone, gauche, y - 1);
        texte(g, font, texte, gauche + 24, y + 5, TEXTE);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        ConfigPayload c = ConfigRecue.get();

        if (!modeChantier) {
            texte(g, font, "Tu n'as pas encore de chantier. Donne-lui un nom :", gauche, haut + 4, TEXTE);
            etiquette(g, new ItemStack(Items.NAME_TAG), "Titre", haut + 24);
            texte(g, font, "Tu pourras ensuite ajouter qui tu veux (ou rester seul).", gauche, haut + 52, DOUX);
            afficherMessage(g, c, haut + 68);
            return;
        }

        etiquette(g, new ItemStack(Items.NAME_TAG), "Titre", haut);
        etiquette(g, new ItemStack(Items.CHEST), "Coffres", haut + 26);
        etiquette(g, new ItemStack(Items.PLAYER_HEAD), "Membres", haut + 64);
        g.fill(gauche, haut + 96, gauche + LARGEUR, haut + 97, CREUX);
        if (c == null) {
            texte(g, font, "En attente du serveur…", gauche, haut + 132, DOUX);
            return;
        }
        String infoCoffres = c.coffres() == 0
                ? "Aucun coffre : seuls les inventaires comptent."
                : c.coffres() + " coffres choisis (+ inventaires des membres).";
        texte(g, font, font.plainSubstrByWidth(infoCoffres, LARGEUR - CHAMP), gauche + CHAMP, haut + 49, DOUX);
        texte(g, font, font.plainSubstrByWidth(c.membres(), LARGEUR - CHAMP), gauche + CHAMP, haut + 86, DOUX);

        // Avancement : texte + barre
        int y = haut + 130;
        String etat = c.materiaux() + " matériaux · " + c.pourcent() + " %";
        texte(g, font, etat, gauche, y, c.pourcent() >= 100 ? VERT : TEXTE);
        creux(g, gauche, y + 11, LARGEUR, 8, 0xFF5A5A5A);
        int rempli = (LARGEUR - 2) * Math.max(0, Math.min(100, c.pourcent())) / 100;
        if (rempli > 0) g.fill(gauche + 1, y + 12, gauche + 1 + rempli, y + 18, c.pourcent() >= 100 ? 0xFF55C255 : 0xFFE0A020);
        afficherMessage(g, c, y + 24);
    }

    private void afficherMessage(GuiGraphicsExtractor g, ConfigPayload c, int y) {
        String texte = !messageLocal.isEmpty() ? messageLocal : c == null ? "" : c.message();
        if (texte.isEmpty()) return;
        texte(g, font, font.plainSubstrByWidth(texte, LARGEUR), gauche, y, OR);
    }

    /** Glisser un .litematic sur l'écran Chantier l'importe directement. */
    @Override
    public void onFilesDrop(java.util.List<java.nio.file.Path> fichiers) {
        if (!modeChantier) return;
        messageLocal = SchemaScreen.deposer(fichiers);
    }

}
