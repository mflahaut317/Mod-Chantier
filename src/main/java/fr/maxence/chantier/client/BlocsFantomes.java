package fr.maxence.chantier.client;

import com.mojang.math.Transformation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Hologramme « blocs » (compatible shaders) : chaque bloc à poser est un vrai bloc miniature
 * (entité d'affichage créée seulement chez le joueur, jamais envoyée au serveur).
 * Les shaders l'éclairent comme un bloc normal. Un bloc faux à remplacer a un contour rouge.
 */
final class BlocsFantomes {
    private BlocsFantomes() {}

    static final int RAYON = 24;
    static final int MAX = 1500;
    private static final float TAILLE = 0.6f;

    private static boolean pret;
    private static boolean disponible = true;
    private static Method setBlockState, setTransformation, setGlowColorOverride, setShadowRadius;
    private static EntityType<?> type;

    /** n° du bloc dans le plan -> entité affichée. */
    private static final Map<Integer, Display.BlockDisplay> ACTIFS = new HashMap<>();
    private static final Map<Integer, Boolean> ROUGES = new HashMap<>();
    private static int prochainId = -1_000_000;
    private static String cle = "";

    /** false si le jeu ne permet pas ce mode (on revient alors aux cubes). */
    static boolean disponible() {
        preparer();
        return disponible;
    }

    private static void preparer() {
        if (pret) return;
        pret = true;
        try {
            setBlockState = Display.BlockDisplay.class.getDeclaredMethod("setBlockState", net.minecraft.world.level.block.state.BlockState.class);
            setTransformation = Display.class.getDeclaredMethod("setTransformation", Transformation.class);
            setGlowColorOverride = Display.class.getDeclaredMethod("setGlowColorOverride", int.class);
            setShadowRadius = Display.class.getDeclaredMethod("setShadowRadius", float.class);
            for (Method m : new Method[]{setBlockState, setTransformation, setGlowColorOverride, setShadowRadius}) m.setAccessible(true);
            type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("block_display"));
            if (type == null) disponible = false;
        } catch (ReflectiveOperationException | RuntimeException e) {
            disponible = false;
        }
    }

    /** Retire toutes les entités affichées. */
    static void vider() {
        ClientLevel monde = Minecraft.getInstance().level;
        if (monde != null) for (Entity e : ACTIFS.values()) monde.removeEntity(e.getId(), Entity.RemovalReason.DISCARDED);
        ACTIFS.clear();
        ROUGES.clear();
        cle = "";
    }

    /**
     * Affiche exactement les blocs « voulus » (choisis par Hologramme autour du joueur) ; faux(i) : un autre bloc gêne.
     * position(i) n'est appelé que pour les nouveaux blocs. Appelé quelques fois par seconde.
     */
    static void mettreAJour(LecteurLitematic.Plan plan, String cleHologramme, Set<Integer> voulus,
                            java.util.function.IntPredicate faux,
                            java.util.function.IntFunction<net.minecraft.core.BlockPos> position) {
        preparer();
        Minecraft mc = Minecraft.getInstance();
        ClientLevel monde = mc.level;
        if (!disponible || monde == null || mc.player == null) return;
        if (!cleHologramme.equals(cle)) { vider(); cle = cleHologramme; } // déplacé ou tourné : on refait tout

        // Retirer ceux qui ne servent plus (bien posés, trop loin, autre couche)
        ACTIFS.entrySet().removeIf(e -> {
            if (voulus.contains(e.getKey())) return false;
            monde.removeEntity(e.getValue().getId(), Entity.RemovalReason.DISCARDED);
            ROUGES.remove(e.getKey());
            return true;
        });
        // Ajouter les nouveaux, et mettre à jour le contour rouge
        for (int i : voulus) {
            boolean rouge = faux.test(i);
            Display.BlockDisplay d = ACTIFS.get(i);
            if (d == null) {
                d = creer(monde, plan.blocs[i], position.apply(i));
                if (d == null) return;
                ACTIFS.put(i, d);
                ROUGES.put(i, !rouge); // force la mise à jour juste en dessous
            }
            if (ROUGES.get(i) != rouge) {
                d.setGlowingTag(rouge);
                ROUGES.put(i, rouge);
            }
        }
    }

    private static Display.BlockDisplay creer(ClientLevel monde, Block bloc, net.minecraft.core.BlockPos p) {
        try {
            Display.BlockDisplay d = new Display.BlockDisplay(type, monde);
            d.setId(prochainId--);
            float marge = (1 - TAILLE) / 2;
            setBlockState.invoke(d, bloc.defaultBlockState());
            setTransformation.invoke(d, new Transformation(new Vector3f(marge, marge, marge), new Quaternionf(),
                    new Vector3f(TAILLE, TAILLE, TAILLE), new Quaternionf()));
            setGlowColorOverride.invoke(d, 0xFF3030);
            setShadowRadius.invoke(d, 0f);
            d.setPos(p.getX(), p.getY(), p.getZ());
            monde.addEntity(d);
            return d;
        } catch (ReflectiveOperationException | RuntimeException e) {
            disponible = false;
            return null;
        }
    }
}
