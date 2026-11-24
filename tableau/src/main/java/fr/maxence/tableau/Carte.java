package fr.maxence.tableau;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Carte vue du dessus (1 pixel = 1 bloc, couleurs des cartes du jeu), dessinée petit à petit autour des joueurs :
 * seuls les chunks déjà chargés sont lus, jamais de chargement forcé. Tuiles de 512 x 512 gardées en PNG
 * dans config/tableau/carte/<dimension>/<x>_<z>.png.
 */
final class Carte {
    private Carte() {}

    static final int TUILE = 512;
    private static final int RAYON_CHUNKS = 6;
    private static final int MAX_PAR_PASSAGE = 40;
    private static final long REDESSIN_MS = 60_000;

    /** dimension|tx|tz -> pixels ARGB (0 = inconnu). */
    private static final Map<String, int[]> TUILES = new ConcurrentHashMap<>();
    private static final Set<String> MODIFIEES = ConcurrentHashMap.newKeySet();
    private static final Map<String, Long> DERNIER = new HashMap<>();

    private static Path dossier(String dim) { return FMLPaths.CONFIGDIR.get().resolve("tableau").resolve("carte").resolve(dim); }

    private static String cle(String dim, int tx, int tz) { return dim + "|" + tx + "|" + tz; }

    /** Appelé chaque seconde sur le thread du jeu : redessine quelques chunks chargés autour des joueurs. */
    static void passage(MinecraftServer serveur) {
        int faits = 0;
        long maintenant = System.currentTimeMillis();
        for (ServerPlayer p : serveur.getPlayerList().getPlayers()) {
            ServerLevel l = (ServerLevel) p.level();
            String dim = l.dimension().identifier().getPath();
            int pcx = p.getBlockX() >> 4, pcz = p.getBlockZ() >> 4;
            for (int dx = -RAYON_CHUNKS; dx <= RAYON_CHUNKS && faits < MAX_PAR_PASSAGE; dx++) {
                for (int dz = -RAYON_CHUNKS; dz <= RAYON_CHUNKS && faits < MAX_PAR_PASSAGE; dz++) {
                    int cx = pcx + dx, cz = pcz + dz;
                    String c = dim + "|" + cx + "|" + cz;
                    Long avant = DERNIER.get(c);
                    if (avant != null && maintenant - avant < REDESSIN_MS) continue;
                    LevelChunk chunk = l.getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null) continue;
                    dessiner(l, dim, chunk, cx, cz);
                    DERNIER.put(c, maintenant);
                    faits++;
                }
            }
        }
    }

    private static void dessiner(ServerLevel l, String dim, LevelChunk chunk, int cx, int cz) {
        int x0 = cx << 4, z0 = cz << 4;
        int tx = Math.floorDiv(x0, TUILE), tz = Math.floorDiv(z0, TUILE);
        int[] tuile = tuile(dim, tx, tz);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean nether = dim.equals("the_nether");
        for (int x = 0; x < 16; x++) {
            int hautNord = hauteur(chunk, x, -1, nether);
            for (int z = 0; z < 16; z++) {
                int h = hauteur(chunk, x, z, nether);
                int couleur = 0;
                for (int y = h; y > h - 4 && couleur == 0; y--) {
                    pos.set(x0 + x, y, z0 + z);
                    BlockState etat = chunk.getBlockState(pos);
                    if (etat.isAir()) continue;
                    couleur = etat.getMapColor(l, pos).col;
                }
                // Relief comme sur les cartes du jeu : plus clair si ça monte vers le sud, plus sombre si ça descend
                int lumiere = hautNord == Integer.MIN_VALUE || h == hautNord ? 220 : h > hautNord ? 255 : 180;
                hautNord = h;
                int px = Math.floorMod(x0 + x, TUILE), pz = Math.floorMod(z0 + z, TUILE);
                tuile[px + pz * TUILE] = couleur == 0 ? 0 : 0xFF000000 | assombrir(couleur, lumiere);
            }
        }
        MODIFIEES.add(cle(dim, tx, tz));
    }

    /** Hauteur du bloc le plus haut (dans le Nether : sous le plafond de bedrock). */
    private static int hauteur(LevelChunk chunk, int x, int z, boolean nether) {
        if (z < 0) return Integer.MIN_VALUE;
        if (!nether) return chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        boolean air = false;
        for (int y = 120; y > 0; y--) {
            p.set(x0 + x, y, z0 + z);
            boolean vide = chunk.getBlockState(p).isAir();
            if (vide) air = true;
            else if (air) return y;
        }
        return 0;
    }

    private static int assombrir(int rgb, int f) {
        int r = ((rgb >> 16) & 255) * f / 255, v = ((rgb >> 8) & 255) * f / 255, b = (rgb & 255) * f / 255;
        return (r << 16) | (v << 8) | b;
    }

    private static int[] tuile(String dim, int tx, int tz) {
        return TUILES.computeIfAbsent(cle(dim, tx, tz), k -> {
            int[] px = new int[TUILE * TUILE];
            Path f = dossier(dim).resolve(tx + "_" + tz + ".png");
            if (Files.exists(f)) {
                try {
                    BufferedImage img = ImageIO.read(f.toFile());
                    if (img != null) img.getRGB(0, 0, TUILE, TUILE, px, 0, TUILE);
                } catch (IOException | RuntimeException ignored) { }
            }
            return px;
        });
    }

    /** Enregistre les tuiles modifiées (appelé régulièrement et à l'arrêt). */
    static void sauver() {
        for (String c : new ArrayList<>(MODIFIEES)) {
            MODIFIEES.remove(c);
            int[] px = TUILES.get(c);
            if (px == null) continue;
            String[] p = c.split("\\|");
            try {
                Files.createDirectories(dossier(p[0]));
                Files.write(dossier(p[0]).resolve(p[1] + "_" + p[2] + ".png"), png(px));
            } catch (IOException e) {
                Tableau.LOGGER.warn("[Tableau] Carte non enregistrée", e);
            }
        }
    }

    private static byte[] png(int[] px) throws IOException {
        BufferedImage img = new BufferedImage(TUILE, TUILE, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, TUILE, TUILE, px, 0, TUILE);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    /** Image PNG d'une tuile (null si elle n'existe pas). */
    static byte[] imageTuile(String dim, int tx, int tz) throws IOException {
        int[] px = TUILES.get(cle(dim, tx, tz));
        if (px != null) return png(px);
        Path f = dossier(dim).resolve(tx + "_" + tz + ".png");
        return Files.exists(f) ? Files.readAllBytes(f) : null;
    }

    /** Liste des tuiles connues d'une dimension, en JSON : [[x,z],…]. */
    static String liste(String dim) {
        Set<String> toutes = new HashSet<>();
        for (String c : TUILES.keySet()) {
            String[] p = c.split("\\|");
            if (p[0].equals(dim)) toutes.add(p[1] + "," + p[2]);
        }
        Path d = dossier(dim);
        if (Files.isDirectory(d)) {
            try (var s = Files.list(d)) {
                s.forEach(f -> {
                    String n = f.getFileName().toString();
                    if (n.endsWith(".png")) toutes.add(n.substring(0, n.length() - 4).replace('_', ','));
                });
            } catch (IOException ignored) { }
        }
        List<String> r = new ArrayList<>();
        for (String t : toutes) r.add("[" + t + "]");
        return "[" + String.join(",", r) + "]";
    }
}
