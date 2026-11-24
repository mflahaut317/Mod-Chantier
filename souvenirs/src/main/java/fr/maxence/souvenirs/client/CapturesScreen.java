package fr.maxence.souvenirs.client;

import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Choix d'une capture d'écran (dossier screenshots du jeu, la plus récente en premier). */
public class CapturesScreen extends Screen {
    private static final SimpleDateFormat DATE = new SimpleDateFormat("d MMM yyyy à HH:mm", Locale.FRENCH);
    private static final int LARGEUR = 300;
    private static final int LIGNE = 22;

    private final Screen parent;
    private final String idSouvenir;
    private List<Path> captures = List.of();
    private int defilement;

    public CapturesScreen(Screen parent, String idSouvenir) {
        super(Component.literal("Choisir une capture"));
        this.parent = parent;
        this.idSouvenir = idSouvenir;
    }

    private int visibles() { return Math.max(1, (height - 80) / LIGNE); }

    @Override
    protected void init() {
        captures = Photos.captures();
        int x = (width - LARGEUR) / 2, y = 40;
        for (int i = defilement; i < captures.size() && i < defilement + visibles(); i++, y += LIGNE) {
            Path p = captures.get(i);
            String nom = DATE.format(new Date(Photos.date(p))) + "  ·  " + p.getFileName();
            addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(nom, LARGEUR - 10)), b -> {
                Photos.envoyer(p, idSouvenir);
                onClose();
            }).bounds(x, y, LARGEUR, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Retour"), b -> onClose())
                .bounds(width / 2 - 50, height - 28, 100, 20).build());
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        int avant = defilement;
        defilement = Math.max(0, Math.min(Math.max(0, captures.size() - visibles()), defilement - (int) Math.signum(v) * 2));
        if (avant != defilement) rebuildWidgets();
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        String titre = "Choisir une capture (" + captures.size() + ")";
        g.text(font, titre, width / 2 - font.width(titre) / 2, 12, 0xFFFFD24A);
        String aide = "F2 en jeu prend une capture · tu peux aussi glisser une image sur le journal";
        g.text(font, font.plainSubstrByWidth(aide, width - 20), width / 2 - Math.min(font.width(aide), width - 20) / 2, 24, 0xFFAAAAAA);
        if (captures.isEmpty()) {
            String vide = "Aucune capture dans le dossier screenshots.";
            g.text(font, vide, width / 2 - font.width(vide) / 2, height / 2, 0xFFAAAAAA);
        }
    }

    @Override
    public void onClose() { minecraft.gui.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
