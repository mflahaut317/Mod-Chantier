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
    static void onDeconnexion(ClientPlayerNetworkEvent.LoggingOut event) {
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
        int hauteur = 12 + 5 + visibles * 17 + (caches > 0 ? 10 : 0);

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

    /** 150 -> "2 st + 22", 64 -> "1 st", 40 -> "40". */
    static String quantite(int n) {
        if (n < 64) return String.valueOf(n);
        int stacks = n / 64, reste = n % 64;
        return stacks + " st" + (reste > 0 ? " + " + reste : "");
    }
}
