package fr.maxence.chantier.client;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Hologramme du schéma sur le terrain : un cube transparent de la couleur du bloc là où il faut le poser,
 * rouge si un autre bloc gêne, rien quand le bon bloc est posé. Uniquement côté joueur.
 * Pour rester léger : seuls les blocs à moins de RAYON blocs sont dessinés, au plus DESSIN_MAX par tick.
 */
final class Hologramme {
    private Hologramme() {}

    private static final int RAYON = 40;
    private static final int DESSIN_MAX = 6000;

    static LecteurLitematic.Plan plan;
    static Path fichier;
    static String nom = "";
    static BlockPos origine;
    static int rotation;          // quarts de tour
    static int couche = -1;       // -1 = toutes
    static boolean visible = true;
    static String dimension = "";
    static int poses, total;      // blocs bien posés / blocs dans les chunks chargés
    /** Dernier schéma importé dans le chantier (pour proposer de l'afficher). */
    static Path dernierImporte;

    /** Mode « en visant » : l'hologramme suit le viseur jusqu'au clic droit. */
    static boolean suivre;
    /** Dessiné par-dessus tout (visible à travers les blocs) : utile avec les shaders. */
    static boolean parDessus;
    /** Affichage en vrais blocs miniatures (compatible shaders) plutôt qu'en cubes transparents. */
    static boolean modeBlocs = true;

    /** Bloc fantôme visé par le joueur (-1 : aucun) et ce qui se trouve à sa place. */
    static int vise = -1;
    static boolean viseFaux;
    /** Position monde -> n° du bloc, recalculé quand l'hologramme bouge. */
    private static final java.util.HashMap<Long, Integer> INDEX = new java.util.HashMap<>();
    private static String cleIndex = "";

    /** Hologramme du chantier, partagé avec tout le groupe (reçu du serveur). */
    static boolean partage;
    private static int versionPartage = -1;
    private static int dernierChangement = -1;

    private static boolean restaure;
    private static int compteur;

    /** Charge un schéma et le pose là où se tient le joueur. @return null si ça a marché, sinon l'erreur */
    static String charger(Path f) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return "Entre dans le monde d'abord.";
        try {
            LecteurLitematic.Plan p = LecteurLitematic.lirePlan(f);
            if (p.n == 0) return "Ce schéma ne contient aucun bloc.";
            plan = p;
            fichier = f;
            nom = f.getFileName().toString().replaceAll("(?i)\\.(litematic|schem|nbt)$", "");
            origine = mc.player.blockPosition();
            rotation = 0;
            couche = -1;
            visible = true;
            dimension = mc.level.dimension().identifier().toString();
            poses = 0;
            total = 0;
            compteur = 19; // statistiques dès le prochain tick
            suivre = true;  // il apparaît devant le joueur et suit son viseur
            return null;
        } catch (IOException | RuntimeException e) {
            return "Lecture impossible : " + e.getMessage();
        }
    }

    /** Déconnexion : le hologramme du groupe sera renvoyé par le serveur à la prochaine connexion. */
    static void deconnexion() {
        if (partage) { partage = false; versionPartage = -1; retirerLocal(); }
        restaure = false;
        dernierChangement = -1;
    }

    /** Retire le hologramme ; s'il est partagé, il disparaît pour tout le groupe. */
    static void retirer() {
        if (partage) net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
                new fr.maxence.chantier.ActionPayload("holo_retirer", ""));
        partage = false;
        retirerLocal();
    }

    private static void retirerLocal() {
        suivre = false;
        BlocsFantomes.vider();
        plan = null;
        fichier = null;
        try { Files.deleteIfExists(fichierReglages()); } catch (IOException ignored) { }
    }

    static int largeur() { return rotation % 2 == 0 ? plan.sx : plan.sz; }

    static int profondeur() { return rotation % 2 == 0 ? plan.sz : plan.sx; }

    /** Position dans le monde du bloc n° i, avec la rotation. */
    private static BlockPos monde(int i, BlockPos.MutableBlockPos sortie) {
        int x = plan.pos[i * 3], y = plan.pos[i * 3 + 1], z = plan.pos[i * 3 + 2];
        int rx, rz;
        switch (rotation) {
            case 1 -> { rx = plan.sz - 1 - z; rz = x; }
            case 2 -> { rx = plan.sx - 1 - x; rz = plan.sz - 1 - z; }
            case 3 -> { rx = z; rz = plan.sx - 1 - x; }
            default -> { rx = x; rz = z; }
        }
        return sortie.set(origine.getX() + rx, origine.getY() + y, origine.getZ() + rz);
    }

    // ---------- Réglages ----------

    /** Commence le placement en visant (l'hologramme suit le viseur). */
    static void commencerSuivi() {
        if (plan == null) return;
        suivre = true;
        visible = true;
    }

    /** Clic droit pendant le suivi : l'hologramme reste là. */
    static void poser() {
        suivre = false;
        apresChangement();
        envoyerPosition();
    }

    /** Hologramme partagé : prévient le serveur (et donc le groupe) qu'il a bougé. */
    private static void envoyerPosition() {
        if (!partage || origine == null) return;
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new fr.maxence.chantier.ActionPayload("holo_position",
                origine.getX() + ";" + origine.getY() + ";" + origine.getZ() + ";" + rotation + ";" + dimension));
    }

    /** Envoie le hologramme au serveur pour tout le groupe. @return message pour le joueur */
    static String partagerAvecGroupe() {
        if (plan == null || origine == null) return "Aucun hologramme à partager.";
        byte[] tout = Volume.encoderComplet(plan);
        if (tout.length == 0) return "Schéma trop varié pour être partagé (plus de 255 blocs différents).";
        int morceau = 30_000;
        int total = (tout.length + morceau - 1) / morceau;
        for (int i = 0; i < total; i++) {
            byte[] part = java.util.Arrays.copyOfRange(tout, i * morceau, Math.min(tout.length, (i + 1) * morceau));
            net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new fr.maxence.chantier.HoloEnvoiPayload(
                    nom, origine.getX(), origine.getY(), origine.getZ(), rotation, dimension, i, total, part));
        }
        return "Envoi au groupe…";
    }

    /** Applique ce que le serveur a envoyé : nouveau hologramme du groupe, déplacement ou retrait. */
    private static void synchroniser() {
        int c = fr.maxence.chantier.HoloRecu.changement;
        if (c == dernierChangement) return;
        dernierChangement = c;
        if (fr.maxence.chantier.HoloRecu.retire) {
            if (partage) { partage = false; versionPartage = -1; retirerLocal(); }
            return;
        }
        byte[] donnees = fr.maxence.chantier.HoloRecu.donnees;
        int v = fr.maxence.chantier.HoloRecu.version;
        if (donnees != null && v != versionPartage) {
            Volume.Grille g = Volume.decoder(donnees);
            if (g == null) return;
            plan = Volume.versPlan(g);
            fichier = null;
            nom = fr.maxence.chantier.HoloRecu.nom;
            couche = -1;
            suivre = false;
            visible = true;
            versionPartage = v;
            partage = true;
            try { Files.deleteIfExists(fichierReglages()); } catch (IOException ignored) { }
        }
        if (partage && !suivre) {
            origine = new BlockPos(fr.maxence.chantier.HoloRecu.x, fr.maxence.chantier.HoloRecu.y, fr.maxence.chantier.HoloRecu.z);
            rotation = fr.maxence.chantier.HoloRecu.rotation;
            dimension = fr.maxence.chantier.HoloRecu.dimension;
            compteur = 19;
        }
    }

    /** Pendant le suivi : centre l'hologramme sur le bloc visé (jusqu'à 64 blocs), posé dessus. */
    private static void suivreViseur(Minecraft mc) {
        net.minecraft.world.phys.HitResult vise = mc.player.pick(64, 1f, false);
        BlockPos cible;
        if (vise instanceof net.minecraft.world.phys.BlockHitResult bloc && vise.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            cible = bloc.getBlockPos().relative(bloc.getDirection());
        } else {
            cible = BlockPos.containing(mc.player.getEyePosition().add(mc.player.getLookAngle().scale(12)));
        }
        origine = new BlockPos(cible.getX() - largeur() / 2, cible.getY(), cible.getZ() - profondeur() / 2);
        dimension = mc.level.dimension().identifier().toString();
    }

    static void placerIci() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        origine = mc.player.blockPosition();
        dimension = mc.level.dimension().identifier().toString();
        apresChangement();
        envoyerPosition();
    }

    /** Déplace par rapport à la direction du regard (avant > 0 = vers où on regarde). */
    static void deplacer(int avant, int droite, int haut) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || origine == null) return;
        Direction face = mc.player.getDirection();
        Direction cote = face.getClockWise();
        origine = origine.offset(face.getStepX() * avant + cote.getStepX() * droite, haut,
                face.getStepZ() * avant + cote.getStepZ() * droite);
        apresChangement();
        envoyerPosition();
    }

    /** Quart de tour dans le sens des aiguilles d'une montre, autour du centre du schéma. */
    static void tourner() {
        if (plan == null || origine == null) return;
        int cx = origine.getX() + largeur() / 2, cz = origine.getZ() + profondeur() / 2;
        rotation = (rotation + 1) % 4;
        origine = new BlockPos(cx - largeur() / 2, origine.getY(), cz - profondeur() / 2);
        apresChangement();
        envoyerPosition();
    }

    static void changerCouche(int pas) {
        if (plan == null) return;
        if (couche < 0) couche = pas > 0 ? 0 : plan.sy - 1;
        else couche = Math.max(0, Math.min(plan.sy - 1, couche + pas));
        apresChangement();
    }

    static void toutesLesCouches() { couche = -1; apresChangement(); }

    static void basculerVisible() { visible = !visible; apresChangement(); }

    private static void apresChangement() {
        compteur = 19;
        sauver();
    }

    // ---------- Dessin ----------

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (!restaure) { restaure = true; restaurer(); }
        synchroniser();
        if (plan != null && suivre) suivreViseur(mc);
        vise = -1;
        boolean blocs = modeBlocs && BlocsFantomes.disponible();
        if (plan == null || origine == null || !visible || !mc.level.dimension().identifier().toString().equals(dimension)) {
            BlocsFantomes.vider();
            return;
        }
        if (!suivre) chercherVise(mc);
        if (blocs && !suivre) {
            // Vrais blocs miniatures, mis à jour 4 fois par seconde
            if (compteur % 5 == 0) {
                BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
                BlocsFantomes.mettreAJour(plan, plan.n + "|" + origine.asLong() + "|" + rotation,
                        i -> {
                            if (couche >= 0 && plan.pos[i * 3 + 1] != couche) return false;
                            monde(i, q);
                            return mc.level.isLoaded(q) && mc.level.getBlockState(q).getBlock() != plan.blocs[i];
                        },
                        i -> {
                            BlockState a = mc.level.getBlockState(monde(i, q));
                            return !(a.isAir() || a.canBeReplaced());
                        },
                        i -> { monde(i, q); return new int[]{q.getX(), q.getY(), q.getZ()}; });
            }
        } else {
            BlocsFantomes.vider();
        }

        boolean stats = ++compteur % 20 == 0;
        int bons = 0, vus = 0, dessines = 0;
        int px = mc.player.getBlockX(), py = mc.player.getBlockY(), pz = mc.player.getBlockZ();
        int rayon2 = RAYON * RAYON;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        try (Gizmos.TemporaryCollection ignored = mc.collectPerTickGizmos()) {
            for (int i = 0; i < plan.n; i++) {
                boolean bonneCouche = couche < 0 || plan.pos[i * 3 + 1] == couche;
                if (!bonneCouche && !stats) continue;
                monde(i, pos);
                int dx = pos.getX() - px, dy = pos.getY() - py, dz = pos.getZ() - pz;
                boolean proche = dx * dx + dy * dy + dz * dz <= rayon2;
                if (!proche && !stats) continue;
                if (!mc.level.isLoaded(pos)) continue;
                BlockState actuel = mc.level.getBlockState(pos);
                Block attendu = plan.blocs[i];
                boolean bon = actuel.getBlock() == attendu;
                if (stats) { vus++; if (bon) bons++; }
                if (bon || !bonneCouche || !proche || dessines >= DESSIN_MAX) continue;
                if (blocs && !suivre) {
                    // Mode blocs : seul le bloc visé garde un cadre blanc
                    if (i == vise) Gizmos.cuboid(new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1),
                            GizmoStyle.stroke(0xFFFFFFFF, 3.0f));
                    continue;
                }

                boolean libre = actuel.isAir() || actuel.canBeReplaced();
                int x = pos.getX(), y = pos.getY(), z = pos.getZ();
                if (libre) {
                    int rgb = attendu.defaultMapColor().col;
                    if (rgb == 0) rgb = 0xC8DCFF; // verre et blocs sans couleur : bleu très clair
                    double m = 0.1;
                    net.minecraft.gizmos.GizmoProperties p = Gizmos.cuboid(new AABB(x + m, y + m, z + m, x + 1 - m, y + 1 - m, z + 1 - m),
                            GizmoStyle.strokeAndFill(0xC0000000 | rgb, 1.0f, 0x60000000 | rgb));
                    if (parDessus) p.setAlwaysOnTop();
                    if (i == vise) Gizmos.cuboid(new AABB(x, y, z, x + 1, y + 1, z + 1), GizmoStyle.stroke(0xFFFFFFFF, 3.0f));
                } else {
                    // Un autre bloc est à la place : en rouge, un peu plus gros pour qu'on le voie
                    net.minecraft.gizmos.GizmoProperties p = Gizmos.cuboid(new AABB(x - 0.01, y - 0.01, z - 0.01, x + 1.01, y + 1.01, z + 1.01),
                            GizmoStyle.strokeAndFill(0xFFFF3030, 2.0f, 0x50FF3030));
                    if (parDessus) p.setAlwaysOnTop();
                }
                dessines++;
            }
        }
        if (stats) { poses = bons; total = vus; }
    }

    /** Suit le regard (jusqu'à 24 blocs) et note le premier bloc fantôme pas encore bien posé. */
    private static void chercherVise(Minecraft mc) {
        String cle = plan.n + "|" + origine.asLong() + "|" + rotation;
        if (!cle.equals(cleIndex)) {
            INDEX.clear();
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int i = 0; i < plan.n; i++) INDEX.put(monde(i, p).asLong(), i);
            cleIndex = cle;
        }
        net.minecraft.world.phys.Vec3 oeil = mc.player.getEyePosition();
        net.minecraft.world.phys.Vec3 dir = mc.player.getLookAngle();
        long dernier = Long.MIN_VALUE;
        for (double t = 0.2; t <= 24; t += 0.1) {
            BlockPos bp = BlockPos.containing(oeil.add(dir.scale(t)));
            long cle2 = bp.asLong();
            if (cle2 == dernier) continue;
            dernier = cle2;
            BlockState actuel = mc.level.getBlockState(bp);
            Integer i = INDEX.get(cle2);
            if (i != null && (couche < 0 || plan.pos[i * 3 + 1] == couche) && actuel.getBlock() != plan.blocs[i]) {
                vise = i;
                viseFaux = !(actuel.isAir() || actuel.canBeReplaced());
                return;
            }
            if (!actuel.isAir() && !actuel.canBeReplaced()) return; // un vrai bloc cache la suite
        }
    }

    /** Bloc attendu à l'endroit visé (null si on ne vise aucun fantôme). */
    static Block blocVise() { return plan != null && vise >= 0 && vise < plan.n ? plan.blocs[vise] : null; }

    // ---------- Sauvegarde (pour retrouver l'hologramme à la prochaine connexion) ----------

    private static Path fichierReglages() {
        return FMLPaths.CONFIGDIR.get().resolve("chantier-hologramme.properties");
    }

    private static void sauver() {
        if (plan == null || fichier == null || origine == null || partage) return;
        Properties p = new Properties();
        p.setProperty("fichier", fichier.toAbsolutePath().toString());
        p.setProperty("x", Integer.toString(origine.getX()));
        p.setProperty("y", Integer.toString(origine.getY()));
        p.setProperty("z", Integer.toString(origine.getZ()));
        p.setProperty("rotation", Integer.toString(rotation));
        p.setProperty("couche", Integer.toString(couche));
        p.setProperty("visible", Boolean.toString(visible));
        p.setProperty("dimension", dimension);
        try (Writer w = Files.newBufferedWriter(fichierReglages(), StandardCharsets.UTF_8)) {
            p.store(w, "Hologramme du mod Chantier");
        } catch (IOException ignored) { }
    }

    private static void restaurer() {
        Path f = fichierReglages();
        if (plan != null || !Files.exists(f)) return;
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            p.load(r);
            Path schema = Path.of(p.getProperty("fichier", ""));
            if (!Files.exists(schema)) return;
            LecteurLitematic.Plan lu = LecteurLitematic.lirePlan(schema);
            if (lu.n == 0) return;
            plan = lu;
            fichier = schema;
            nom = schema.getFileName().toString().replaceAll("(?i)\\.(litematic|schem|nbt)$", "");
            origine = new BlockPos(Integer.parseInt(p.getProperty("x", "0")), Integer.parseInt(p.getProperty("y", "0")),
                    Integer.parseInt(p.getProperty("z", "0")));
            rotation = Math.floorMod(Integer.parseInt(p.getProperty("rotation", "0")), 4);
            couche = Integer.parseInt(p.getProperty("couche", "-1"));
            if (couche >= plan.sy) couche = -1;
            visible = Boolean.parseBoolean(p.getProperty("visible", "true"));
            dimension = p.getProperty("dimension", "");
            compteur = 19;
        } catch (IOException | RuntimeException ignored) {
            // Réglages illisibles : pas d'hologramme
        }
    }
}
