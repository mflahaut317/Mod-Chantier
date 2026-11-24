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
    /** Mode réglage sans menu : on joue normalement et on ajuste au clavier (flèches, R, Entrée…). */
    static boolean edition;
    /** Dessiné par-dessus tout (visible à travers les blocs) : utile avec les shaders. */
    static boolean parDessus;
    /** Affichage en vrais blocs miniatures (compatible shaders) plutôt qu'en cubes transparents. */
    static boolean modeBlocs = true;

    /** Bloc fantôme visé par le joueur (-1 : aucun) et ce qui se trouve à sa place. */
    static int vise = -1;
    static boolean viseFaux;
    /** Index compact du plan (construit une fois par schéma). */
    private static IndexPlan index;
    /** Statistiques calculées par tranches (pas tout d'un coup) : position, compteurs en cours. */
    private static int statPos, statBons, statVus;

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
            avantSuivi = null; // annuler le retire
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
        avantSuivi = origine;
        rotationAvantSuivi = rotation;
        suivre = true;
        visible = true;
    }

    private static BlockPos avantSuivi;
    private static int rotationAvantSuivi;

    /** Annule le placement en visant : l'hologramme revient où il était (ou disparaît s'il venait d'être chargé). */
    static void annulerSuivi() {
        if (!suivre) return;
        suivre = false;
        if (avantSuivi == null) { retirerLocal(); return; }
        origine = avantSuivi;
        rotation = rotationAvantSuivi;
        apresChangement();
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
        statPos = 0; statBons = 0; statVus = 0;
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
        if (index == null || index.plan() != plan) { index = new IndexPlan(plan); statPos = 0; statBons = 0; statVus = 0; }
        compteur++;
        if (!suivre) chercherVise(mc);
        statistiquesParTranche(mc);

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int px = mc.player.getBlockX(), py = mc.player.getBlockY(), pz = mc.player.getBlockZ();

        if (blocs && !suivre) {
            // Vrais blocs miniatures, mis à jour 4 fois par seconde
            if (compteur % 5 == 0) {
                java.util.Set<Integer> voulus = new java.util.HashSet<>();
                int r2 = BlocsFantomes.RAYON * BlocsFantomes.RAYON;
                proches(mc, BlocsFantomes.RAYON, i -> {
                    if (voulus.size() >= BlocsFantomes.MAX || (couche >= 0 && plan.pos[i * 3 + 1] != couche)) return;
                    monde(i, pos);
                    int dx = pos.getX() - px, dy = pos.getY() - py, dz = pos.getZ() - pz;
                    if (dx * dx + dy * dy + dz * dz > r2 || !mc.level.isLoaded(pos)) return;
                    if (mc.level.getBlockState(pos).getBlock() != plan.blocs[i]) voulus.add(i);
                });
                BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
                BlocsFantomes.mettreAJour(plan, plan.n + "|" + origine.asLong() + "|" + rotation, voulus,
                        i -> {
                            BlockState a = mc.level.getBlockState(monde(i, q));
                            return !(a.isAir() || a.canBeReplaced());
                        },
                        i -> monde(i, q));
            }
        } else {
            BlocsFantomes.vider();
        }

        int rayon2 = RAYON * RAYON;
        int[] dessines = {0};
        try (Gizmos.TemporaryCollection ignored = mc.collectPerTickGizmos()) {
            if (blocs && !suivre) {
                // Mode blocs : seul le bloc visé garde un cadre blanc
                if (vise >= 0) {
                    monde(vise, pos);
                    Gizmos.cuboid(new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1),
                            GizmoStyle.stroke(0xFFFFFFFF, 3.0f));
                }
                return;
            }
            java.util.function.IntConsumer dessiner = i -> {
                if (dessines[0] >= DESSIN_MAX || (couche >= 0 && plan.pos[i * 3 + 1] != couche)) return;
                monde(i, pos);
                int x = pos.getX(), y = pos.getY(), z = pos.getZ();
                int dx = x - px, dy = y - py, dz = z - pz;
                if (dx * dx + dy * dy + dz * dz > rayon2 || !mc.level.isLoaded(pos)) return;
                BlockState actuel = mc.level.getBlockState(pos);
                Block attendu = plan.blocs[i];
                if (actuel.getBlock() == attendu) return;
                boolean libre = actuel.isAir() || actuel.canBeReplaced();
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
                dessines[0]++;
            };
            if (suivre) {
                for (int i = 0; i < plan.n && dessines[0] < DESSIN_MAX; i++) dessiner.accept(i); // pendant la visée, tout bouge
            } else {
                proches(mc, RAYON, dessiner);
            }
        }
    }

    /**
     * Blocs posés / blocs visibles, calculés par tranches de 20 000 blocs par tick :
     * même un énorme schéma ne bloque jamais le jeu, le compteur se met à jour en continu.
     */
    private static void statistiquesParTranche(Minecraft mc) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int fin = Math.min(plan.n, statPos + 20_000);
        for (int i = statPos; i < fin; i++) {
            monde(i, p);
            if (!mc.level.isLoaded(p)) continue;
            statVus++;
            if (mc.level.getBlockState(p).getBlock() == plan.blocs[i]) statBons++;
        }
        statPos = fin;
        if (statPos >= plan.n) {
            poses = statBons;
            total = statVus;
            statPos = 0; statBons = 0; statVus = 0;
        }
    }

    /** Suit le regard (jusqu'à 24 blocs) et note le premier bloc fantôme pas encore bien posé. */
    private static void chercherVise(Minecraft mc) {
        net.minecraft.world.phys.Vec3 oeil = mc.player.getEyePosition();
        net.minecraft.world.phys.Vec3 dir = mc.player.getLookAngle();
        long dernier = Long.MIN_VALUE;
        int[] l = new int[3];
        for (double t = 0.2; t <= 24; t += 0.1) {
            BlockPos bp = BlockPos.containing(oeil.add(dir.scale(t)));
            long cle2 = bp.asLong();
            if (cle2 == dernier) continue;
            dernier = cle2;
            BlockState actuel = mc.level.getBlockState(bp);
            versSchema(bp.getX(), bp.getY(), bp.getZ(), l);
            int i = index.trouver(l[0], l[1], l[2]);
            if (i >= 0 && (couche < 0 || plan.pos[i * 3 + 1] == couche) && actuel.getBlock() != plan.blocs[i]) {
                vise = i;
                viseFaux = !(actuel.isAir() || actuel.canBeReplaced());
                return;
            }
            if (!actuel.isAir() && !actuel.canBeReplaced()) return; // un vrai bloc cache la suite
        }
    }

    /** Position du monde -> position dans le schéma (inverse de monde()). */
    private static void versSchema(int wx, int wy, int wz, int[] sortie) {
        int rx = wx - origine.getX(), rz = wz - origine.getZ();
        int x, z;
        switch (rotation) {
            case 1 -> { x = rz; z = plan.sz - 1 - rx; }
            case 2 -> { x = plan.sx - 1 - rx; z = plan.sz - 1 - rz; }
            case 3 -> { x = plan.sx - 1 - rz; z = rx; }
            default -> { x = rx; z = rz; }
        }
        sortie[0] = x; sortie[1] = wy - origine.getY(); sortie[2] = z;
    }

    /** Blocs du schéma à moins de rayon blocs du joueur (seulement les zones proches sont parcourues). */
    private static void proches(Minecraft mc, int rayon, java.util.function.IntConsumer action) {
        int[] l = new int[3];
        versSchema(mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ(), l);
        index.dansBoite(l[0] - rayon, l[1] - rayon, l[2] - rayon, l[0] + rayon, l[1] + rayon, l[2] + rayon, action);
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
        } catch (IOException | RuntimeException ignored) {
            // Réglages illisibles : pas d'hologramme
        }
    }
}
