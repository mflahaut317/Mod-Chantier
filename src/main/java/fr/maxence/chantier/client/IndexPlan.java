package fr.maxence.chantier.client;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * Index compact d'un plan, construit une seule fois par schéma (pas à chaque déplacement) :
 * - positions triées (12 octets par bloc) pour retrouver « quel bloc est ici ? » ;
 * - blocs rangés par zones de 16 x 16 x 16 pour ne parcourir que ceux autour du joueur.
 * Toutes les coordonnées sont celles du schéma (avant rotation et déplacement).
 */
final class IndexPlan {
    private final LecteurLitematic.Plan plan;
    private final long[] cles;
    private final int[] indices;
    private final Map<Long, int[]> zones = new HashMap<>();

    IndexPlan(LecteurLitematic.Plan plan) {
        this.plan = plan;
        int n = plan.n;
        long[] paires = new long[n];
        for (int i = 0; i < n; i++) paires[i] = (cle(plan.pos[i * 3], plan.pos[i * 3 + 1], plan.pos[i * 3 + 2]) << 24) | i;
        Arrays.sort(paires);
        cles = new long[n];
        indices = new int[n];
        for (int k = 0; k < n; k++) {
            cles[k] = paires[k] >>> 24;
            indices[k] = (int) (paires[k] & 0xFFFFFF);
        }
        // Zones de 16 blocs : on compte puis on remplit
        Map<Long, Integer> tailles = new HashMap<>();
        for (int i = 0; i < n; i++) tailles.merge(zone(plan.pos[i * 3] >> 4, plan.pos[i * 3 + 1] >> 4, plan.pos[i * 3 + 2] >> 4), 1, Integer::sum);
        Map<Long, Integer> remplis = new HashMap<>();
        for (Map.Entry<Long, Integer> e : tailles.entrySet()) zones.put(e.getKey(), new int[e.getValue()]);
        for (int i = 0; i < n; i++) {
            long z = zone(plan.pos[i * 3] >> 4, plan.pos[i * 3 + 1] >> 4, plan.pos[i * 3 + 2] >> 4);
            int k = remplis.merge(z, 1, Integer::sum) - 1;
            zones.get(z)[k] = i;
        }
    }

    LecteurLitematic.Plan plan() { return plan; }

    /** Clé d'une position du schéma (chaque coordonnée tient sur 13 bits, le plan est limité en taille). */
    private static long cle(int x, int y, int z) {
        return ((long) (x & 0x1FFF) << 26) | ((long) (y & 0x1FFF) << 13) | (z & 0x1FFF);
    }

    private static long zone(int zx, int zy, int zz) {
        return ((long) (zx & 0xFFFFF) << 40) | ((long) (zy & 0xFFFFF) << 20) | (zz & 0xFFFFF);
    }

    /** N° du bloc à cette position du schéma, ou -1. */
    int trouver(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= plan.sx || y >= plan.sy || z >= plan.sz) return -1;
        int k = Arrays.binarySearch(cles, cle(x, y, z));
        return k >= 0 ? indices[k] : -1;
    }

    /** Appelle action pour chaque bloc dont la zone touche la boîte [min, max] (coordonnées du schéma). */
    void dansBoite(int x0, int y0, int z0, int x1, int y1, int z1, IntConsumer action) {
        for (int zx = Math.max(0, x0) >> 4; zx <= Math.min(plan.sx - 1, x1) >> 4; zx++)
            for (int zy = Math.max(0, y0) >> 4; zy <= Math.min(plan.sy - 1, y1) >> 4; zy++)
                for (int zz = Math.max(0, z0) >> 4; zz <= Math.min(plan.sz - 1, z1) >> 4; zz++) {
                    int[] liste = zones.get(zone(zx, zy, zz));
                    if (liste != null) for (int i : liste) action.accept(i);
                }
    }
}
