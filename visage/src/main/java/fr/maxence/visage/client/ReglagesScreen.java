package fr.maxence.visage.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Réglages de la caméra (touche N) : choix de la caméra, rotation, miroir, zoom, avec aperçu en direct. */
final class ReglagesScreen extends net.minecraft.client.gui.screens.Screen {
    private static final int APERCU = 96;
    private static final float[] ZOOMS = { 1f, 1.5f, 2f };

    private Button allumer, camera, rotation, miroir, zoom;
    private int px, py;

    ReglagesScreen() {
        super(Component.literal("Ma caméra"));
    }

    @Override
    protected void init() {
        Camera.lister();
        int largeurBouton = 170;
        int total = APERCU + 12 + largeurBouton;
        px = width / 2 - total / 2;
        py = height / 2 - 70;
        int bx = px + APERCU + 12, by = py;

        allumer = addRenderableWidget(Button.builder(Component.empty(), b -> {
            boolean avant = Camera.allumee();
            Camera.basculer();
            if (avant) VisageClient.eteindre();
            majTextes();
        }).bounds(bx, by, largeurBouton, 20).build());
        camera = addRenderableWidget(Button.builder(Component.empty(), b -> {
            List<String> liste = new ArrayList<>();
            liste.add("");
            liste.addAll(Camera.noms());
            int i = liste.indexOf(Reglages.camera);
            Reglages.camera = liste.get((i + 1) % liste.size());
            Camera.changerDeCamera();
            majTextes();
        }).bounds(bx, by + 24, largeurBouton, 20).build());
        rotation = addRenderableWidget(Button.builder(Component.empty(), b -> {
            Reglages.rotation = (Reglages.rotation + 1) % 4;
            majTextes();
        }).bounds(bx, by + 48, largeurBouton, 20).build());
        miroir = addRenderableWidget(Button.builder(Component.empty(), b -> {
            Reglages.miroir = !Reglages.miroir;
            majTextes();
        }).bounds(bx, by + 72, largeurBouton, 20).build());
        zoom = addRenderableWidget(Button.builder(Component.empty(), b -> {
            int i = 0;
            for (int k = 0; k < ZOOMS.length; k++) if (Math.abs(ZOOMS[k] - Reglages.zoom) < 0.01f) i = k;
            Reglages.zoom = ZOOMS[(i + 1) % ZOOMS.length];
            majTextes();
        }).bounds(bx, by + 96, largeurBouton, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Terminé"), b -> onClose())
                .bounds(width / 2 - 60, by + 128, 120, 20).build());
        majTextes();
    }

    private void majTextes() {
        allumer.setMessage(Component.literal(Camera.allumee() ? "Éteindre la caméra" : "Allumer la caméra"));
        String nom = Reglages.camera.isEmpty() ? "par défaut" : Reglages.camera;
        if (nom.length() > 20) nom = nom.substring(0, 19) + "…";
        camera.setMessage(Component.literal("Caméra : " + nom));
        rotation.setMessage(Component.literal("Rotation : " + Reglages.rotation * 90 + "°"));
        miroir.setMessage(Component.literal("Miroir : " + (Reglages.miroir ? "oui" : "non")));
        zoom.setMessage(Component.literal("Zoom : ×" + (Reglages.zoom == 1.5f ? "1,5" : Integer.toString(Math.round(Reglages.zoom)))));
    }

    @Override
    public void tick() {
        // Le texte du bouton suit l'état réel (caméra introuvable, éteinte ailleurs…)
        majTextes();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        g.text(font, "Ma caméra", width / 2 - font.width("Ma caméra") / 2, py - 18, 0xFFFFFFFF, true);
        g.fill(px - 1, py - 1, px + APERCU + 1, py + APERCU + 1, Camera.allumee() ? 0xFFFF3B3B : 0xFF555555);
        Identifier moi = minecraft.player == null ? null : Textures.active(minecraft.player.getId());
        if (moi != null && Camera.allumee()) {
            g.blit(RenderPipelines.GUI_TEXTURED, moi, px, py, 0f, 0f, APERCU, APERCU,
                    Camera.TAILLE, Camera.TAILLE, Camera.TAILLE, Camera.TAILLE);
        } else {
            g.fill(px, py, px + APERCU, py + APERCU, 0xFF000000);
            String t = Camera.allumee() ? "Démarrage…" : "Éteinte";
            g.text(font, t, px + APERCU / 2 - font.width(t) / 2, py + APERCU / 2 - 4, 0xFFAAAAAA, true);
        }
        g.text(font, "C'est ce que voient tes potes.", px, py + APERCU + 6, 0xFFAAAAAA, true);
    }

    @Override
    public void removed() {
        Reglages.sauver();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
