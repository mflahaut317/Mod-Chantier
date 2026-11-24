package fr.maxence.durabilite;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Durabilité : usure des outils et de l'armure.
 * 1. À droite de la barre d'objets : objet en main, seconde main et armure, avec le nombre d'utilisations restantes.
 * 2. Sur chaque case d'inventaire : le nombre restant en petit, dès que l'objet est abîmé.
 * 3. Alerte (son + message) quand un objet équipé passe sous 10 %, puis sous 3 %.
 * Mod uniquement côté joueur : rien à installer sur le serveur.
 */
@Mod(value = Durabilite.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Durabilite.MODID, value = Dist.CLIENT)
public class Durabilite {
    public static final String MODID = "durabilite";

    private static final EquipmentSlot[] EMPLACEMENTS = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
    private static final double[] SEUILS = { 0.10, 0.03 };
    private static final int DUREE_ALERTE = 60; // ticks (3 s)

    /** Dernier état connu de chaque emplacement : objet et usure. */
    private record Etat(Item objet, int usure) { }
    private static final Map<EquipmentSlot, Etat> ETATS = new HashMap<>();
    private static String alerte = "";
    private static int alerteRestante = 0;

    // ---------- Calculs ----------

    static int restant(ItemStack pile) {
        return pile.getMaxDamage() - pile.getDamageValue();
    }

    static double fraction(ItemStack pile) {
        int max = pile.getMaxDamage();
        return max <= 0 ? 1 : (double) restant(pile) / max;
    }

    /** Vert > 50 %, jaune > 25 %, orange > 10 %, rouge en dessous. */
    static int couleur(ItemStack pile) {
        double f = fraction(pile);
        if (f > 0.50) return 0xFF55FF55;
        if (f > 0.25) return 0xFFFFFF55;
        if (f > 0.10) return 0xFFFFAA00;
        return 0xFFFF5555;
    }

    static String court(int n) {
        if (n >= 10_000) return (n / 1000) + "k";
        if (n >= 1_000) return String.format("%.1fk", n / 1000.0).replace(',', '.');
        return Integer.toString(n);
    }

    // ---------- 2. Nombre sur les cases ----------

    @SubscribeEvent
    static void onDecorations(RegisterItemDecorationsEvent event) {
        for (Item objet : BuiltInRegistries.ITEM) {
            boolean usable;
            try {
                usable = new ItemStack(objet).isDamageableItem();
            } catch (RuntimeException e) {
                usable = false;
            }
            if (usable) event.register(objet, Durabilite::decorer);
        }
    }

    private static boolean decorer(GuiGraphicsExtractor g, Font font, ItemStack pile, int x, int y) {
        if (!pile.isDamageableItem() || !pile.isDamaged()) return false;
        String texte = court(restant(pile));
        g.pose().pushMatrix();
        g.pose().translate(x + 1, y + 1);
        g.pose().scale(0.5f, 0.5f);
        g.text(font, texte, 0, 0, couleur(pile), true);
        g.pose().popMatrix();
        return false;
    }

    // ---------- 1. Panneau à côté de la barre d'objets ----------

    @SubscribeEvent
    static void onCalques(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, Identifier.fromNamespaceAndPath(MODID, "usure"), Durabilite::dessiner);
    }

    private static void dessiner(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer joueur = mc.player;
        if (joueur == null || joueur.isSpectator()) return;
        Font font = mc.font;
        int largeur = mc.getWindow().getGuiScaledWidth();
        int hauteur = mc.getWindow().getGuiScaledHeight();

        // Colonne à droite de la barre d'objets, de bas en haut
        int x = largeur / 2 + 91 + 8;
        int y = hauteur - 19;
        for (EquipmentSlot emplacement : EMPLACEMENTS) {
            ItemStack pile = joueur.getItemBySlot(emplacement);
            if (pile.isEmpty() || !pile.isDamageableItem()) continue;
            g.item(pile, x, y);
            g.text(font, court(restant(pile)), x + 18, y + 4, couleur(pile), true);
            y -= 17;
        }

        // Alerte au-dessus de la barre d'objets
        if (alerteRestante > 0) {
            int w = font.width(alerte);
            int ax = largeur / 2 - w / 2;
            int ay = hauteur - 72;
            g.fill(ax - 4, ay - 3, ax + w + 4, ay + 11, 0xA0000000);
            g.text(font, alerte, ax, ay, (alerteRestante / 5) % 2 == 0 ? 0xFFFF5555 : 0xFFFFFFFF, true);
        }
    }

    // ---------- 3. Alerte ----------

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        if (alerteRestante > 0) alerteRestante--;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer joueur = mc.player;
        if (joueur == null) {
            ETATS.clear();
            return;
        }
        for (EquipmentSlot emplacement : EMPLACEMENTS) {
            ItemStack pile = joueur.getItemBySlot(emplacement);
            if (pile.isEmpty() || !pile.isDamageableItem()) {
                ETATS.remove(emplacement);
                continue;
            }
            Etat avant = ETATS.put(emplacement, new Etat(pile.getItem(), pile.getDamageValue()));
            // Nouvel objet en main : on mémorise sans alerter
            if (avant == null || avant.objet() != pile.getItem() || pile.getDamageValue() <= avant.usure()) continue;
            int max = pile.getMaxDamage();
            double avantF = (double) (max - avant.usure()) / max;
            double maintenant = fraction(pile);
            for (double seuil : SEUILS) {
                if (avantF >= seuil && maintenant < seuil) {
                    alerte = "⚠ " + pile.getHoverName().getString() + " : plus que " + restant(pile) + " utilisations !";
                    alerteRestante = DUREE_ALERTE;
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, 0.6f));
                    break;
                }
            }
        }
    }
}
