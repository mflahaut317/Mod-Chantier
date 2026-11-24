package fr.maxence.chantier.client;

import fr.maxence.chantier.Chantier;
import fr.maxence.chantier.ConfigRecue;
import fr.maxence.chantier.EtatPayload;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;
import fr.maxence.chantier.EtatRecu;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/** Affiche la liste du chantier en haut à gauche de l'écran. Chargé uniquement côté joueur. */
@Mod(value = Chantier.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Chantier.MODID, value = Dist.CLIENT)
public class ChantierClient {
    private static final int LIGNES_MAX = 6;
    private static final int FOND = 0x90000000;
    private static final int OR = 0xFFFFD24A;
    private static final int BLANC = 0xFFFFFFFF;
    private static final int VERT = 0xFF55FF55;
    private static final int GRIS = 0xFFAAAAAA;

    public ChantierClient() {
        // Permet à /chantier (côté serveur) d'ouvrir l'écran.
        ConfigRecue.ouvrirEcran = () -> Minecraft.getInstance().execute(
                () -> Minecraft.getInstance().gui.setScreen(new ChantierScreen()));
    }

    @SubscribeEvent
    static void onCalques(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Identifier.fromNamespaceAndPath(Chantier.MODID, "liste"), ChantierClient::dessiner);
    }

    @SubscribeEvent
    static void onTouches(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) {
        Touches.enregistrer(event);
    }

    /** Pendant le placement en visant : clic droit pose l'hologramme, clic gauche le tourne. */
    @SubscribeEvent
    static void onClic(net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
        if (!Hologramme.suivre) return;
        if (event.isUseItem()) Hologramme.poser();
        else if (event.isAttack()) Hologramme.tourner();
        else return;
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    @SubscribeEvent
    static void onTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Touches.traiter(mc);
        Hologramme.tick();
    }

    @SubscribeEvent
    static void onDeconnexion(ClientPlayerNetworkEvent.LoggingOut event) {
        BlocsFantomes.vider();
        Hologramme.deconnexion();
        fr.maxence.chantier.HoloRecu.vider();
        EtatRecu.vider();
        ConfigRecue.vider();
    }

    /** Ajoute le bouton « Chantier » au milieu du bord gauche de l'inventaire. */
    @SubscribeEvent
    static void onEcranOuvert(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen ecran)) return;
        event.addListener(Button.builder(Component.literal("Chantier"),
                        b -> Minecraft.getInstance().gui.setScreen(new ChantierScreen()))
                .bounds(4, ecran.height / 2 - 10, 56, 20)
                .build());
    }

    private record Affichage(ItemStack stack, int pourcent, boolean fini) {}

    static void dessiner(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Block attendu = Hologramme.blocVise();
        if (attendu != null && mc.player != null) {
            // Sous le viseur : le bloc à poser ici
            ItemStack pile = new ItemStack(attendu.asItem());
            String nom = (Hologramme.viseFaux ? "À remplacer par : " : "") + attendu.getName().getString();
            int l = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
            int largeur = 22 + mc.font.width(nom);
            int x = l / 2 - largeur / 2, y = h / 2 + 12;
            g.fill(x - 3, y - 2, x + largeur + 3, y + 18, 0xB0000000);
            if (!pile.isEmpty()) g.item(pile, x, y);
            g.text(mc.font, nom, x + 20, y + 5, Hologramme.viseFaux ? 0xFFFF7070 : 0xFFFFFFFF);
        }
        if (Hologramme.edition && Hologramme.plan != null && mc.player != null) Touches.dessinerAide(g, mc);
        if (Hologramme.suivre && mc.player != null) {
            String aide = "Clic droit : poser ici · Clic gauche : tourner · " + Touches.VISER.getKey().getDisplayName().getString() + " : annuler";
            int l = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
            int x = l / 2 - mc.font.width(aide) / 2, y = h / 2 + 16;
            g.fill(x - 4, y - 3, x + mc.font.width(aide) + 4, y + 11, 0xA0000000);
            g.text(mc.font, aide, x, y, 0xFFFFD24A);
        }
        EtatPayload etat = EtatRecu.get();
        if (etat == null || mc.player == null) return;
        Font font = mc.font;

        long total = 0, fait = 0;
        List<Affichage> lignes = new ArrayList<>();
        for (EtatPayload.Ligne l : etat.lignes()) {
            total += l.requis();
            fait += Math.min(l.requis(), l.possede());
            Identifier id = Identifier.tryParse(l.item());
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) continue;
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(id));
            boolean fini = l.possede() >= l.requis();
            int pct = l.requis() == 0 ? 100 : (int) Math.min(100, (long) l.possede() * 100 / l.requis());
            lignes.add(new Affichage(stack, pct, fini));
        }
        // Ce qui manque d'abord, ce qui est fini en bas.
        lignes.sort(Comparator.comparing(Affichage::fini));
        int pourcent = total == 0 ? 0 : (int) (fait * 100 / total);

        String entete = etat.titre() + " — " + pourcent + " %";
        int visibles = Math.min(lignes.size(), LIGNES_MAX);
        int caches = lignes.size() - visibles;

        // Ligne compacte : icône + mini-barre + %, le nom complet est dans « Voir la liste complète ».
        final int barreLigne = 48;
        int largeur = Math.max(font.width(entete), 19 + barreLigne + 4 + font.width("100 %"));
        String fleche = flecheCoffre(mc, etat);
        if (fleche != null) largeur = Math.max(largeur, font.width(fleche));
        int hauteur = 12 + 5 + visibles * 17 + (caches > 0 ? 10 : 0) + (fleche != null ? 11 : 0);

        // Centré verticalement sur le bord gauche, pour ne pas passer sous une minicarte en haut à gauche.
        int x = 4;
        int y = Math.max(4, (mc.getWindow().getGuiScaledHeight() - hauteur) / 2);
        g.fill(x - 3, y - 3, x + largeur + 3, y + hauteur + 2, FOND);
        g.text(font, entete, x, y, OR);

        // Barre d'avancement
        int barreY = y + 11;
        g.fill(x, barreY, x + largeur, barreY + 3, 0xFF333333);
        g.fill(x, barreY, x + largeur * pourcent / 100, barreY + 3, pourcent >= 100 ? VERT : OR);

        int ly = barreY + 6;
        if (fleche != null) {
            g.text(font, fleche, x, ly, GRIS);
            ly += 11;
        }
        for (int i = 0; i < visibles; i++) {
            Affichage a = lignes.get(i);
            int couleur = a.fini() ? VERT : OR;
            g.item(a.stack(), x, ly);
            int bx = x + 19, by = ly + 6;
            g.fill(bx, by, bx + barreLigne, by + 4, 0xFF333333);
            g.fill(bx, by, bx + barreLigne * a.pourcent() / 100, by + 4, couleur);
            g.text(font, a.fini() ? "✔" : a.pourcent() + " %", bx + barreLigne + 4, ly + 4, a.fini() ? VERT : BLANC);
            ly += 17;
        }
        if (caches > 0) {
            g.text(font, "+ " + caches + " autres", x, ly, GRIS);
        }
    }

    private static final String[] FLECHES = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    /**
     * « Coffre ↗ 23 m » vers le coffre choisi le plus proche, ou null (pas de coffre, autre dimension, ou à moins de 6 m).
     */
    static String flecheCoffre(Minecraft mc, EtatPayload etat) {
        if (mc.player == null || mc.level == null || etat.coffres().size() < 3) return null;
        if (!mc.level.dimension().identifier().toString().equals(etat.dimension())) return null;
        double px = mc.player.getX(), pz = mc.player.getZ();
        double meilleur = Double.MAX_VALUE, dx = 0, dz = 0;
        List<Integer> c = etat.coffres();
        for (int i = 0; i + 2 < c.size(); i += 3) {
            double ex = c.get(i) + 0.5 - px, ez = c.get(i + 2) + 0.5 - pz;
            double d = ex * ex + ez * ez;
            if (d < meilleur) { meilleur = d; dx = ex; dz = ez; }
        }
        double distance = Math.sqrt(meilleur);
        if (distance < 6) return null;
        // Angle du coffre dans le repère Minecraft (0° = sud, 90° = ouest), relatif à la direction du regard
        double angle = Math.toDegrees(Math.atan2(-dx, dz)) - mc.player.getYRot();
        angle = ((angle % 360) + 360) % 360;
        String f = FLECHES[(int) Math.round(angle / 45.0) % 8];
        return "Coffre " + f + " " + Math.round(distance) + " m";
    }

    /** 150 -> "2 st + 22", 64 -> "1 st", 40 -> "40". */
    static String quantite(int n) {
        if (n < 64) return String.valueOf(n);
        int stacks = n / 64, reste = n % 64;
        return stacks + " st" + (reste > 0 ? " + " + reste : "");
    }
}
