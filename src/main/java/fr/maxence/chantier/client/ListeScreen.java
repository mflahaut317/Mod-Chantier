package fr.maxence.chantier.client;

import fr.maxence.chantier.EtatPayload;
import fr.maxence.chantier.EtatRecu;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Liste complète des matériaux, défilable à la molette. Ouverte depuis l'écran Chantier. */
public class ListeScreen extends CadreScreen {
    private static final int LARGEUR = 300;
    private static final int HAUTEUR_LIGNE = 18;
    private int defilement = 0;
    /** false : liste du chantier ; true : matières premières à récolter. */
    private boolean modeRecolte = false;
    private Button boutonListe;
    private Button boutonRecolte;
    private int haut;
    private int bas;

    private record Ligne(ItemStack stack, String nom, int possede, int requis) {
        boolean fini() { return possede >= requis; }
    }

    public ListeScreen(Screen parent) {
        super("Liste du chantier", parent);
    }

    @Override
    protected void init() {
        cadre(LARGEUR + 24, 1000);
        haut = py + 34;
        bas = py + ph - 34;
        EtatPayload etat = EtatRecu.get();
        if (etat != null && !etat.titre().isEmpty()) setTitre(etat.titre());
        boutonListe = addRenderableWidget(Button.builder(Component.literal("Liste du chantier"), b -> changerMode(false))
                .bounds(width / 2 - 125, py + 8, 120, 18).build());
        boutonRecolte = addRenderableWidget(Button.builder(Component.literal("À récolter"), b -> changerMode(true))
                .bounds(width / 2 + 5, py + 8, 120, 18).build());
        boutonListe.active = modeRecolte;
        boutonRecolte.active = !modeRecolte;
        addRenderableWidget(Button.builder(Component.literal("Retour"), b -> onClose())
                .bounds(width / 2 - 50, py + ph - 28, 100, 20).build());
    }

    private String titreChantier = "";

    private void setTitre(String t) { titreChantier = t; }

    @Override
    protected String titre() {
        return titreChantier.isEmpty() ? super.titre() : titreChantier;
    }

    private void changerMode(boolean recolte) {
        modeRecolte = recolte;
        defilement = 0;
        boutonListe.active = modeRecolte;
        boutonRecolte.active = !modeRecolte;
    }

    private List<Ligne> lignes() {
        List<Ligne> liste = new ArrayList<>();
        EtatPayload etat = EtatRecu.get();
        if (etat == null) return liste;
        if (modeRecolte) {
            for (EtatPayload.Ligne l : etat.recolte()) {
                Identifier id = Identifier.tryParse(l.item());
                if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) continue;
                ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(id));
                liste.add(new Ligne(stack, stack.getHoverName().getString(), 0, l.requis()));
            }
            liste.sort(Comparator.comparingInt(Ligne::requis).reversed());
            return liste;
        }
        for (EtatPayload.Ligne l : etat.lignes()) {
            Identifier id = Identifier.tryParse(l.item());
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) continue;
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(id));
            liste.add(new Ligne(stack, stack.getHoverName().getString(), l.possede(), l.requis()));
        }
        liste.sort(Comparator.comparing(Ligne::fini));
        return liste;
    }

    private int lignesVisibles() {
        return Math.max(1, (bas - haut) / HAUTEUR_LIGNE);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        int max = Math.max(0, lignes().size() - lignesVisibles());
        defilement = Math.max(0, Math.min(max, defilement - (int) Math.signum(vertical) * 3));
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        List<Ligne> liste = lignes();
        int gauche = (width - LARGEUR) / 2;

        if (liste.isEmpty()) {
            info = "";
            String vide = modeRecolte ? "Rien à récolter : tout est dans les coffres !" : "Aucun chantier en cours.";
            texte(g, font, vide, width / 2 - font.width(vide) / 2, haut + 20, DOUX);
            return;
        }

        long total = 0, fait = 0;
        int finis = 0;
        for (Ligne l : liste) {
            total += l.requis();
            fait += Math.min(l.requis(), l.possede());
            if (l.fini()) finis++;
        }
        int pourcent = total == 0 ? 0 : (int) (fait * 100 / total);
        info = modeRecolte ? liste.size() + " matières à récolter" : pourcent + " % · " + finis + "/" + liste.size() + " terminés";

        int max = Math.max(0, liste.size() - lignesVisibles());
        defilement = Math.min(defilement, max);
        int y = haut;
        creux(g, gauche - 5, haut - 3, LARGEUR + 10, bas - haut + 4, 0xFFB8B8B8);
        for (int i = defilement; i < liste.size() && y + HAUTEUR_LIGNE <= bas; i++) {
            Ligne l = liste.get(i);
            if (i % 2 == 0) g.fill(gauche - 4, y - 1, gauche + LARGEUR + 4, y + HAUTEUR_LIGNE - 1, 0x18000000);
            g.item(l.stack(), gauche, y);
            boolean vert = !modeRecolte && l.fini();
            String q = modeRecolte
                    ? ChantierClient.quantite(l.requis())
                    : l.fini()
                    ? ChantierClient.quantite(l.requis()) + " ✔"
                    : ChantierClient.quantite(l.possede()) + " / " + ChantierClient.quantite(l.requis());
            int largeurQ = font.width(q);
            // Petite barre d'avancement entre le nom et la quantité
            if (!modeRecolte) {
                int bx = gauche + LARGEUR - largeurQ - 50;
                creux(g, bx, y + 5, 42, 6, 0xFF6A6A6A);
                int rempli = (int) (40L * Math.min(l.requis(), l.possede()) / Math.max(1, l.requis()));
                if (rempli > 0) g.fill(bx + 1, y + 6, bx + 1 + rempli, y + 10, vert ? 0xFF55C255 : 0xFFE0A020);
            }
            int place = LARGEUR - 20 - largeurQ - (modeRecolte ? 8 : 58);
            texte(g, font, font.plainSubstrByWidth(l.nom(), place), gauche + 20, y + 4, vert ? VERT : TEXTE);
            texte(g, font, q, gauche + LARGEUR - largeurQ, y + 4, vert ? VERT : TEXTE);
            y += HAUTEUR_LIGNE;
        }
        if (max > 0) {
            String aide = "Molette : " + (defilement + 1) + "–"
                    + Math.min(liste.size(), defilement + lignesVisibles()) + " sur " + liste.size();
            texte(g, font, aide, gauche, py + ph - 22, DOUX);
        }
    }

}
