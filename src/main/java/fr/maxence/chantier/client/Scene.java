package fr.maxence.chantier.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;

/**
 * Petites scènes isométriques avec les vraies textures de blocs, pour illustrer le tutoriel.
 * On ajoute des cubes puis dessiner() les trace de l'arrière vers l'avant.
 */
final class Scene {
    /** Un cube : textures (dessus, côtés) ou couleur unie, teinte, transparence, contour. */
    private record Cube(int x, int y, int z, Identifier dessus, Identifier cote, int couleur, int teinte,
                        int alpha, int contour) {}

    private final List<Cube> cubes = new ArrayList<>();
    private List<Cube> tries; // ordre de dessin, calculé une fois

    static Identifier tex(String nom) { return Identifier.withDefaultNamespace("textures/block/" + nom + ".png"); }

    static final Identifier HERBE_DESSUS = tex("grass_block_top");
    static final Identifier HERBE_COTE = tex("grass_block_side");
    static final Identifier TERRE = tex("dirt");
    static final Identifier PLANCHES = tex("oak_planks");
    static final Identifier BUCHE = tex("oak_log");
    static final Identifier BUCHE_DESSUS = tex("oak_log_top");
    static final Identifier PAVES = tex("cobblestone");
    static final Identifier BRIQUES = tex("stone_bricks");
    static final Identifier PIERRE = tex("stone");

    static final int VERT_HERBE = 0xFF7CBD6B;
    static final int BLEU_FANTOME = 0xFF8FB8F0;
    static final int ROUGE = 0xFFFF4040;

    /** Bloc plein et normal. */
    Scene bloc(int x, int y, int z, Identifier dessus, Identifier cote) {
        cubes.add(new Cube(x, y, z, dessus, cote, 0, 0xFFFFFFFF, 255, 0));
        return this;
    }

    Scene bloc(int x, int y, int z, Identifier texture) { return bloc(x, y, z, texture, texture); }

    /** Bloc d'herbe (dessus teinté en vert). */
    Scene herbe(int x, int y, int z) {
        cubes.add(new Cube(x, y, z, HERBE_DESSUS, HERBE_COTE, 0, VERT_HERBE, 255, 0));
        return this;
    }

    /** Fantôme bleuté transparent avec contour : bloc encore à poser. */
    Scene fantome(int x, int y, int z, Identifier texture) {
        cubes.add(new Cube(x, y, z, texture, texture, 0, BLEU_FANTOME, 120, 0xFF5A8FE0));
        return this;
    }

    /** Bloc mal posé : texture normale avec contour rouge. */
    Scene faux(int x, int y, int z, Identifier texture) {
        cubes.add(new Cube(x, y, z, texture, texture, 0, 0xFFFFB0B0, 255, ROUGE));
        return this;
    }

    /** Cube de couleur unie (coffre, etc.). */
    Scene uni(int x, int y, int z, int couleur, int contour) {
        cubes.add(new Cube(x, y, z, null, null, couleur, 0xFFFFFFFF, 255, contour));
        return this;
    }

    /** Fantôme coloré sans texture (couche à venir). */
    Scene vide(int x, int y, int z, int contour) {
        cubes.add(new Cube(x, y, z, null, null, 0xFFB8D4F8, 0xFFFFFFFF, 40, contour));
        return this;
    }

    /** Îlot d'herbe de l x p, avec une couche de terre dessous. */
    Scene ilot(int l, int p) {
        for (int x = 0; x < l; x++) for (int z = 0; z < p; z++) {
            bloc(x, -2, z, TERRE);
            herbe(x, -1, z);
        }
        return this;
    }

    // ---------- Scène depuis un vrai schéma ----------

    /** Cubes visibles d'une grille (les cases entourées de blocs sont sautées), vue tournée de quarts de tour. */
    static Scene depuis(Volume.Grille g, int rotation) {
        Scene s = new Scene();
        int n = 0;
        for (int y = 0; y < g.sy(); y++) for (int z = 0; z < g.sz(); z++) for (int x = 0; x < g.sx(); x++) {
            int v = g.bloc(x, y, z);
            if (v == 0 || g.palette()[v] == null) continue;
            int rx, rz;
            switch (rotation & 3) {
                case 1 -> { rx = g.sz() - 1 - z; rz = x; }
                case 2 -> { rx = g.sx() - 1 - x; rz = g.sz() - 1 - z; }
                case 3 -> { rx = z; rz = g.sx() - 1 - x; }
                default -> { rx = x; rz = z; }
            }
            // On ne voit que le dessus, la face sud et la face est : caché si ces trois voisins sont pleins
            if (plein(g, rotation, rx, y + 1, rz) && plein(g, rotation, rx + 1, y, rz) && plein(g, rotation, rx, y, rz + 1)) continue;
            s.blocReel(rx, y, rz, g.palette()[v]);
            if (++n >= 12_000) return s;
        }
        return s;
    }

    /** Case pleine, en coordonnées tournées (inverse de la rotation de depuis()). */
    private static boolean plein(Volume.Grille g, int rotation, int rx, int y, int rz) {
        int x, z;
        switch (rotation & 3) {
            case 1 -> { x = rz; z = g.sz() - 1 - rx; }
            case 2 -> { x = g.sx() - 1 - rx; z = g.sz() - 1 - rz; }
            case 3 -> { x = g.sx() - 1 - rz; z = rx; }
            default -> { x = rx; z = rz; }
        }
        return g.bloc(x, y, z) != 0;
    }

    /** Bloc du jeu avec sa texture si on la trouve, sinon sa couleur de carte. */
    Scene blocReel(int x, int y, int z, net.minecraft.world.level.block.Block b) {
        Identifier[] t = textures(b);
        String nom = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).getPath();
        int teinte = nom.equals("grass_block") || nom.endsWith("leaves") || nom.equals("vine") ? VERT_HERBE : 0xFFFFFFFF;
        if (t != null) {
            cubes.add(new Cube(x, y, z, t[0], t[1], 0, teinte, 255, 0));
        } else {
            int c = b.defaultMapColor().col;
            cubes.add(new Cube(x, y, z, null, null, 0xFF000000 | (c == 0 ? 0xC8DCFF : c), 0xFFFFFFFF, 255, 0));
        }
        return this;
    }

    private static final java.util.Map<net.minecraft.world.level.block.Block, Identifier[]> TEXTURES = new java.util.HashMap<>();
    private static final java.util.Map<Identifier, Boolean> EXISTE = new java.util.HashMap<>();
    private static final String[] BOIS = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry",
            "bamboo", "crimson", "warped", "pale_oak"};
    private static final String[] SUFFIXES = {"_stairs", "_slab", "_wall_hanging_sign", "_hanging_sign", "_wall_sign",
            "_sign", "_fence_gate", "_fence", "_pressure_plate", "_button", "_wall"};

    private static boolean existe(Identifier id) {
        return EXISTE.computeIfAbsent(id, k -> net.minecraft.client.Minecraft.getInstance().getResourceManager().getResource(k).isPresent());
    }

    /** {dessus, côté} ou null. Devine le nom de la texture à partir du nom du bloc. */
    private static Identifier[] textures(net.minecraft.world.level.block.Block b) {
        if (TEXTURES.containsKey(b)) return TEXTURES.get(b);
        Identifier cle = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b);
        String ns = cle.getNamespace(), nom = cle.getPath();
        String base = nom.replace("_wood", "_log");
        for (String suffixe : SUFFIXES) {
            if (base.endsWith(suffixe)) { base = base.substring(0, base.length() - suffixe.length()); break; }
        }
        if (base.endsWith("brick")) base += "s";
        for (String bois : BOIS) if (base.equals(bois)) base = bois + "_planks";
        if (base.equals("smooth_sandstone")) base = "sandstone_top";
        if (base.equals("smooth_quartz") || base.equals("quartz")) base = "quartz_block_side";
        if (base.equals("smooth_stone_slab")) base = "smooth_stone";
        if (nom.equals("grass_block")) {
            Identifier[] herbe = {HERBE_DESSUS, HERBE_COTE};
            TEXTURES.put(b, herbe);
            return herbe;
        }
        Identifier cote = premier(ns, base, base + "_side", base + "_front", nom);
        Identifier dessus = premier(ns, base + "_top", nom + "_top", base, nom);
        Identifier[] r = cote == null && dessus == null ? null
                : new Identifier[]{dessus != null ? dessus : cote, cote != null ? cote : dessus};
        TEXTURES.put(b, r);
        return r;
    }

    private static Identifier premier(String ns, String... noms) {
        for (String n : noms) {
            Identifier id = Identifier.fromNamespaceAndPath(ns, "textures/block/" + n + ".png");
            if (existe(id)) return id;
        }
        return null;
    }

    /** Position à l'écran du sommet du dessus du cube (x, y, z). a = demi-largeur d'un cube. */
    private static float ex(float ox, float a, Cube c) { return ox + (c.x() - c.z()) * a; }

    private static float ey(float oy, float a, Cube c) { return oy + (c.x() + c.z()) * a / 2f - c.y() * a; }

    /** Dessine la scène : (ox, oy) = sommet du bloc 0,0,0 ; a = demi-largeur d'un cube en pixels. */
    void dessiner(GuiGraphicsExtractor g, float ox, float oy, float a) {
        if (tries == null || tries.size() != cubes.size()) {
            tries = new ArrayList<>(cubes);
            tries.sort(Comparator.comparingInt((Cube c) -> c.x() + c.z()).thenComparingInt(Cube::y));
        }
        float k = a / 16f;
        for (Cube c : tries) {
            float sx = ex(ox, a, c), sy = ey(oy, a, c);
            // Côté gauche (face sud), côté droit (face est), puis dessus
            face(g, c, new Matrix3x2f(k, k / 2, 0, k, sx - a, sy + a / 2), c.cote(), 0.72f);
            face(g, c, new Matrix3x2f(k, -k / 2, 0, k, sx, sy + a), c.cote(), 0.55f);
            face(g, c, new Matrix3x2f(k, k / 2, -k, k / 2, sx, sy), c.dessus(), 1.0f);
        }
    }

    /** Dessine la scène centrée dans une boîte, à la plus grande taille possible (aMax au plus). */
    void dessinerCentre(GuiGraphicsExtractor g, int x, int y, int l, int h, float aMax) {
        if (cubes.isEmpty()) return;
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (Cube c : cubes) { // étendue pour a = 1, origine 0,0
            float sx = ex(0, 1, c), sy = ey(0, 1, c);
            minX = Math.min(minX, sx - 1); maxX = Math.max(maxX, sx + 1);
            minY = Math.min(minY, sy); maxY = Math.max(maxY, sy + 2);
        }
        float a = Math.min(aMax, Math.min((l - 8) / (maxX - minX), (h - 8) / (maxY - minY)));
        float ox = x + l / 2f - (minX + maxX) / 2f * a;
        float oy = y + h / 2f - (minY + maxY) / 2f * a;
        dessiner(g, ox, oy, a);
    }

    private static void face(GuiGraphicsExtractor g, Cube c, Matrix3x2f m, Identifier texture, float lumiere) {
        g.pose().pushMatrix();
        g.pose().mul(m);
        if (texture != null) {
            int teinte = (c.alpha() << 24) | ombre(c.teinte(), lumiere);
            // Les côtés de l'herbe ne sont pas teintés
            if (texture == HERBE_COTE) teinte = (c.alpha() << 24) | ombre(0xFFFFFFFF, lumiere);
            g.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0f, 0f, 16, 16, 16, 16, teinte);
        } else {
            g.fill(0, 0, 16, 16, (c.alpha() << 24) | ombre(c.couleur(), lumiere));
        }
        if (c.contour() != 0) {
            int t = c.contour();
            g.fill(0, 0, 16, 1, t);
            g.fill(0, 15, 16, 16, t);
            g.fill(0, 0, 1, 16, t);
            g.fill(15, 0, 16, 16, t);
        }
        g.pose().popMatrix();
    }

    private static int ombre(int rgb, float f) {
        int r = (int) (((rgb >> 16) & 255) * f), v = (int) (((rgb >> 8) & 255) * f), b = (int) ((rgb & 255) * f);
        return (r << 16) | (v << 8) | b;
    }
}
