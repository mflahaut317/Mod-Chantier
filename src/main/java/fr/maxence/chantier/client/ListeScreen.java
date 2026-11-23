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
public class ListeScreen extends Screen {
    private static final int LARGEUR = 300;
    private static final int HAUTEUR_LIGNE = 18;
    private static final int BLANC = 0xFFFFFFFF;
    private static final int VERT = 0xFF55FF55;
    private static final int GRIS = 0xFFAAAAAA;
    private static final int OR = 0xFFFFD24A;

    private final Screen parent;
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
        super(Component.literal("Liste complète"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        haut = 40;
        bas = height - 36;
        boutonListe = addRenderableWidget(Button.builder(Component.literal("Liste du chantier"), b -> changerMode(false))
                .bounds(width / 2 - 125, 18, 120, 18).build());
        boutonRecolte = addRenderableWidget(Button.builder(Component.literal("À récolter"), b -> changerMode(true))
                .bounds(width / 2 + 5, 18, 120, 18).build());
        boutonListe.active = modeRecolte;
        boutonRecolte.active = !modeRecolte;
        addRenderableWidget(Button.builder(Component.literal("Retour"), b -> onClose())
                .bounds(width / 2 - 50, height - 28, 100, 20).build());
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
            String vide = modeRecolte ? "Rien à récolter : tout est dans les coffres !" : "Aucun chantier en cours.";
            g.text(font, vide, width / 2 - font.width(vide) / 2, height / 2, GRIS);
            return;
        }

        if (modeRecolte) {
            String entete = "Matières premières encore à récolter (" + liste.size() + ")";
            g.text(font, entete, width / 2 - font.width(entete) / 2, 6, OR);
        }
        long total = 0, fait = 0;
        int finis = 0;
        for (Ligne l : liste) {
            total += l.requis();
            fait += Math.min(l.requis(), l.possede());
            if (l.fini()) finis++;
        }
        int pourcent = total == 0 ? 0 : (int) (fait * 100 / total);
        EtatPayload etat = EtatRecu.get();
        String entete = (etat == null ? "Chantier" : etat.titre()) + " — " + pourcent + " % — "
                + finis + "/" + liste.size() + " terminés";
        if (!modeRecolte) g.text(font, entete, width / 2 - font.width(entete) / 2, 6, OR);

        int max = Math.max(0, liste.size() - lignesVisibles());
        defilement = Math.min(defilement, max);
        int y = haut;
        for (int i = defilement; i < liste.size() && y + HAUTEUR_LIGNE <= bas; i++) {
            Ligne l = liste.get(i);
            g.fill(gauche - 4, y - 1, gauche + LARGEUR + 4, y + HAUTEUR_LIGNE - 1, (i % 2 == 0) ? 0x40000000 : 0x20000000);
            g.item(l.stack(), gauche, y);
            g.text(font, l.nom(), gauche + 20, y + 4, (!modeRecolte && l.fini()) ? VERT : BLANC);
            boolean vert = !modeRecolte && l.fini();
            String q = modeRecolte
                    ? ChantierClient.quantite(l.requis())
                    : l.fini()
                    ? ChantierClient.quantite(l.requis()) + " ✔"
                    : ChantierClient.quantite(l.possede()) + " / " + ChantierClient.quantite(l.requis());
            g.text(font, q, gauche + LARGEUR - font.width(q), y + 4, vert ? VERT : (modeRecolte ? BLANC : GRIS));
            y += HAUTEUR_LIGNE;
        }
        if (max > 0) {
            String aide = "Molette pour défiler (" + (defilement + 1) + "–"
                    + Math.min(liste.size(), defilement + lignesVisibles()) + " sur " + liste.size() + ")";
            g.text(font, aide, width / 2 - font.width(aide) / 2, bas + 1, GRIS);
        }
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
