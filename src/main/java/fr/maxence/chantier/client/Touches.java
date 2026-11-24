package fr.maxence.chantier.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Touches du mod (modifiables dans Options > Commandes > Chantier) : ouverture, couches,
 * et mode réglage du hologramme sans menu, avec son panneau d'aide à droite de l'écran.
 */
final class Touches {
    private Touches() {}

    static final net.minecraft.client.KeyMapping.Category CATEGORIE =
            new net.minecraft.client.KeyMapping.Category(Identifier.fromNamespaceAndPath(fr.maxence.chantier.Chantier.MODID, "chantier"));
    static final net.minecraft.client.KeyMapping OUVRIR = new net.minecraft.client.KeyMapping("key.chantier.ouvrir", 72, CATEGORIE);       // H (K = shaders, U = waypoints Xaero)
    static final net.minecraft.client.KeyMapping COUCHE_HAUT = new net.minecraft.client.KeyMapping("key.chantier.couche_haut", 266, CATEGORIE); // Page haut
    static final net.minecraft.client.KeyMapping COUCHE_BAS = new net.minecraft.client.KeyMapping("key.chantier.couche_bas", 267, CATEGORIE);   // Page bas
    static final net.minecraft.client.KeyMapping EDITION = new net.minecraft.client.KeyMapping("key.chantier.edition", 73, CATEGORIE);      // I
    static final net.minecraft.client.KeyMapping AVANCER = new net.minecraft.client.KeyMapping("key.chantier.avancer", 265, CATEGORIE);     // flèche haut
    static final net.minecraft.client.KeyMapping RECULER = new net.minecraft.client.KeyMapping("key.chantier.reculer", 264, CATEGORIE);     // flèche bas
    static final net.minecraft.client.KeyMapping GAUCHE = new net.minecraft.client.KeyMapping("key.chantier.gauche", 263, CATEGORIE);       // flèche gauche
    static final net.minecraft.client.KeyMapping DROITE = new net.minecraft.client.KeyMapping("key.chantier.droite", 262, CATEGORIE);       // flèche droite
    static final net.minecraft.client.KeyMapping TOURNER = new net.minecraft.client.KeyMapping("key.chantier.tourner", 82, CATEGORIE);      // R
    static final net.minecraft.client.KeyMapping VISER = new net.minecraft.client.KeyMapping("key.chantier.viser", 257, CATEGORIE);         // Entrée


    static void enregistrer(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORIE);
        event.register(OUVRIR);
        event.register(COUCHE_HAUT);
        event.register(COUCHE_BAS);
        for (net.minecraft.client.KeyMapping k : new net.minecraft.client.KeyMapping[]{EDITION, AVANCER, RECULER, GAUCHE, DROITE, TOURNER, VISER})
            event.register(k);
    }

    /** Appelé à chaque tick : les touches ne comptent que hors des menus. */
    static void traiter(Minecraft mc) {
        while (OUVRIR.consumeClick()) {
            if (mc.player != null) mc.gui.setScreen(new ChantierScreen()); // les touches ne comptent que hors des menus
        }
        while (COUCHE_HAUT.consumeClick()) Hologramme.changerCouche(1);
        while (COUCHE_BAS.consumeClick()) Hologramme.changerCouche(-1);
        while (EDITION.consumeClick()) {
            if (Hologramme.suivre) { Hologramme.annulerSuivi(); continue; }
            if (Hologramme.plan != null) Hologramme.edition = !Hologramme.edition;
        }
        boolean actif = Hologramme.edition && Hologramme.plan != null;
        boolean maj = mc.hasShiftDown();
        while (AVANCER.consumeClick()) if (actif) { if (maj) Hologramme.deplacer(0, 0, 1); else Hologramme.deplacer(1, 0, 0); }
        while (RECULER.consumeClick()) if (actif) { if (maj) Hologramme.deplacer(0, 0, -1); else Hologramme.deplacer(-1, 0, 0); }
        while (GAUCHE.consumeClick()) if (actif) Hologramme.deplacer(0, -1, 0);
        while (DROITE.consumeClick()) if (actif) Hologramme.deplacer(0, 1, 0);
        while (TOURNER.consumeClick()) if (actif) Hologramme.tourner();
        while (VISER.consumeClick()) {
            if (Hologramme.suivre) Hologramme.annulerSuivi();
            else if (actif) Hologramme.commencerSuivi();
        }
        if (Hologramme.plan == null) Hologramme.edition = false;
    }

    /** Panneau d'aide du mode réglage, à droite de l'écran. */
    static void dessinerAide(GuiGraphicsExtractor g, Minecraft mc) {
        String[][] lignes = {
                {cle(AVANCER) + " " + cle(RECULER), "avancer / reculer"},
                {cle(GAUCHE) + " " + cle(DROITE), "gauche / droite"},
                {"Maj + " + cle(AVANCER) + " " + cle(RECULER), "monter / baisser"},
                {cle(TOURNER), "tourner"},
                {cle(VISER), "placer en visant"},
                {cle(COUCHE_HAUT) + " " + cle(COUCHE_BAS), "couche + / -"},
                {cle(EDITION), "terminer le réglage"}};
        int l = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        int largeur = 0;
        for (String[] li : lignes) largeur = Math.max(largeur, mc.font.width(li[0]) + 8 + mc.font.width(li[1]));
        int pl = largeur + 16, ph = 30 + lignes.length * 12;
        int x = l - pl - 4, y = h / 2 - ph / 2;
        CadreScreen.panneau(g, x, y, pl, ph);
        g.text(mc.font, "Réglage : " + Hologramme.nom, x + 7, y + 7, 0xFF3F3F3F, false);
        String couche = Hologramme.couche < 0 ? "toutes les couches" : "couche " + (Hologramme.couche + 1) + " / " + Hologramme.plan.sy;
        g.text(mc.font, couche, x + 7, y + 17, 0xFF6E6E6E, false);
        int ly = y + 29;
        for (String[] li : lignes) {
            g.text(mc.font, li[0], x + 7, ly, 0xFF1F4FA0, false);
            g.text(mc.font, li[1], x + 7 + mc.font.width(li[0]) + 8, ly, 0xFF3F3F3F, false);
            ly += 12;
        }
    }

    private static String cle(net.minecraft.client.KeyMapping k) {
        return k.getKey().getDisplayName().getString();
    }
}
