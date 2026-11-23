package fr.maxence.chantier;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * État du chantier en cours (un seul à la fois), côté serveur.
 * Sauvegardé dans config/chantier.json.
 */
public final class Projet {
    private static final Projet INSTANCE = new Projet();
    public static Projet get() { return INSTANCE; }


    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** Ce qui est écrit sur disque. */
    private static final class Sauvegarde {
        String titre = "Chantier";
        String lien = null;
        List<String> masques = new ArrayList<>();
        /** Coffres choisis à la main. */
        String dimensionCoffres = null;
        List<int[]> coffres = new ArrayList<>();
        /** true quand toute la liste a été réunie : la liste est masquée jusqu'à une nouvelle liste. */
        boolean termine = false;
    }

    /** Joueurs en mode « choisir les coffres » (non sauvegardé). */
    private final Set<UUID> enSelection = new HashSet<>();

    private Sauvegarde donnees = new Sauvegarde();
    /** Besoins dans l'ordre de la feuille : item -> quantité demandée. */
    private final Map<Item, Integer> besoins = new LinkedHashMap<>();
    /** Dernier comptage total (coffres choisis + inventaires des joueurs connectés). */
    private final Map<Item, Integer> stock = new HashMap<>();
    /** Dernier comptage des coffres choisis (gardé quand ils ne sont pas chargés). */
    private final Map<Item, Integer> stockCoffres = new HashMap<>();
    private final Set<UUID> masques = new HashSet<>();

    private Projet() {}

    // ------------------------------------------------------------------ état

    public boolean aUnLien() { return donnees.lien != null && !donnees.lien.isBlank(); }
    public boolean aDesCoffres() { return donnees.coffres != null && !donnees.coffres.isEmpty(); }
    public boolean estActif() { return !besoins.isEmpty() && !donnees.termine; }
    public boolean estTermine() { return donnees.termine; }
    public int nombreCoffres() { return donnees.coffres == null ? 0 : donnees.coffres.size(); }
    public boolean enSelection(UUID joueur) { return enSelection.contains(joueur); }

    public void basculerSelection(UUID joueur, boolean actif) {
        if (actif) enSelection.add(joueur); else enSelection.remove(joueur);
    }

    public void viderCoffres() {
        stockCoffres.clear();
        donnees.coffres = new ArrayList<>();
        donnees.dimensionCoffres = null;
        stock.clear();
        sauver();
    }

    private int indexCoffre(BlockPos pos) {
        for (int i = 0; i < donnees.coffres.size(); i++) {
            int[] c = donnees.coffres.get(i);
            if (c[0] == pos.getX() && c[1] == pos.getY() && c[2] == pos.getZ()) return i;
        }
        return -1;
    }

    /**
     * Ajoute ou retire le coffre cliqué (et sa moitié si c'est un coffre double).
     * @return message pour le joueur
     */
    public String basculerCoffre(ServerLevel monde, BlockPos pos, MinecraftServer serveur) {
        if (!(monde.getBlockEntity(pos) instanceof Container)) return "Ce bloc n'est pas un conteneur.";
        String dim = monde.dimension().identifier().toString();
        if (donnees.coffres == null) donnees.coffres = new ArrayList<>();
        if (aDesCoffres() && donnees.dimensionCoffres != null && !donnees.dimensionCoffres.equals(dim)) {
            return "Les coffres doivent être dans la même dimension. Vide la sélection d'abord.";
        }
        donnees.dimensionCoffres = dim;

        List<BlockPos> blocs = new ArrayList<>();
        blocs.add(pos);
        BlockState etat = monde.getBlockState(pos);
        if (etat.getBlock() instanceof ChestBlock && etat.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            blocs.add(pos.relative(ChestBlock.getConnectedDirection(etat)));
        }

        boolean retirer = indexCoffre(pos) >= 0;
        for (BlockPos b : blocs) {
            int i = indexCoffre(b);
            if (retirer && i >= 0) donnees.coffres.remove(i);
            if (!retirer && i < 0) donnees.coffres.add(new int[]{b.getX(), b.getY(), b.getZ()});
        }
        if (!aDesCoffres()) donnees.dimensionCoffres = null;
        sauver();
        recompter(serveur);
        diffuser(serveur);
        return (retirer ? "Coffre retiré" : "Coffre ajouté") + " (" + nombreCoffres() + " emplacements, "
                + pourcentage() + " %)";
    }
    public String titre() { return donnees.titre; }

    public void definirTitre(String titre) { donnees.titre = titre; sauver(); }

    public void definirLien(String lien) { donnees.lien = lien.trim(); sauver(); }

    /** Réglages actuels, pour l'écran Chantier du joueur. */
    public ConfigPayload config(UUID joueur, String message, boolean ouvrir) {
        return new ConfigPayload(
                donnees.titre == null ? "" : donnees.titre,
                donnees.lien == null ? "" : donnees.lien,
                besoins.size(), pourcentage(), masques.contains(joueur),
                nombreCoffres(), enSelection.contains(joueur),
                message != null ? message
                        : donnees.termine ? "Chantier terminé ✔ — charge une nouvelle liste pour recommencer." : "",
                ouvrir);
    }

    public void arreter() {
        donnees = new Sauvegarde();
        besoins.clear();
        stock.clear();
        masques.clear();
        sauver();
    }

    public void masquer(UUID joueur, boolean masque) {
        if (masque) masques.add(joueur); else masques.remove(joueur);
        donnees.masques = masques.stream().map(UUID::toString).toList();
        sauver();
    }

    public String resume() {
        StringBuilder sb = new StringBuilder();
        sb.append("Titre : ").append(donnees.titre).append('\n');
        sb.append("Lien : ").append(aUnLien() ? donnees.lien : "aucun").append('\n');
        sb.append("Coffres choisis : ").append(nombreCoffres()).append('
');
        sb.append("Matériaux : ").append(besoins.size()).append(" | Avancement : ").append(pourcentage()).append(" %");
        return sb.toString();
    }


    // ----------------------------------------------------------- Google Sheets

    /**
     * Transforme un lien Google Sheets (partage ou "publier sur le web") en lien d'export CSV.
     */
    static String versLienCsv(String lien) {
        if (lien.contains("output=csv") || lien.contains("format=csv")) return lien;
        Matcher id = Pattern.compile("/spreadsheets/d/([a-zA-Z0-9_-]+)").matcher(lien);
        if (!id.find()) return lien;
        String csv = "https://docs.google.com/spreadsheets/d/" + id.group(1) + "/export?format=csv";
        // Sans gid, Google exporte le premier onglet (gid=0 n'existe pas toujours, ex. une copie).
        Matcher gid = Pattern.compile("[#&?]gid=(\\d+)").matcher(lien);
        return gid.find() ? csv + "&gid=" + gid.group(1) : csv;
    }

    /**
     * Télécharge la feuille en arrière-plan, puis applique le résultat sur le thread du serveur.
     * @param retour message à afficher à celui qui a lancé la commande (peut être null)
     */
    public void telechargerListe(MinecraftServer serveur, Consumer<String> retour) {
        if (!aUnLien()) {
            if (retour != null) retour.accept("Aucun lien. Utilise /chantier lien <lien Google Sheets>");
            return;
        }
        HttpRequest requete;
        try {
            requete = HttpRequest.newBuilder(URI.create(versLienCsv(donnees.lien.trim())))
                    .timeout(Duration.ofSeconds(20)).GET().build();
        } catch (IllegalArgumentException e) {
            if (retour != null) retour.accept("Lien invalide. Colle le lien complet de la feuille Google Sheets.");
            return;
        }
        HTTP.sendAsync(requete, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .whenComplete((reponse, erreur) -> serveur.execute(() -> {
                    String message;
                    if (erreur != null) {
                        message = "Impossible de lire la feuille : " + erreur.getMessage();
                    } else if (reponse.statusCode() != 200 || reponse.body().trim().startsWith("<")) {
                        int code = reponse.statusCode();
                        message = code == 401 || code == 403 || (code == 200)
                                ? "La feuille n'est pas accessible. Partage-la en « Tous les utilisateurs disposant du lien »."
                                : "Google refuse la lecture (code " + code + "). Vérifie le lien, ou mets le lien de l'onglet (avec gid=).";
                    } else {
                        message = appliquerCsv(reponse.body());
                        recompter(serveur);
                        diffuser(serveur);
                    }
                    Chantier.LOGGER.info("[Chantier] {}", message);
                    if (retour != null) retour.accept(message);
                }));
    }

    private static final Pattern QUANTITE = Pattern.compile(
            "^(\\d+)(?:[.,]0+)?\\s*(shulkers?|sb|stacks?|st|s)?\\s*\\+?\\s*(\\d+)?$", Pattern.CASE_INSENSITIVE);

    /** Noms anglais affichés en jeu qui ne correspondent pas directement à l'ID. */
    private static final Map<String, String> ALIAS = Map.ofEntries(
            Map.entry("hay_bale", "hay_block"),
            Map.entry("redstone_dust", "redstone"),
            Map.entry("nether_quartz", "quartz"),
            Map.entry("block_of_quartz", "quartz_block"),
            Map.entry("smooth_quartz_block", "smooth_quartz"),
            Map.entry("bricks_block", "bricks"),
            Map.entry("jack_o'lantern", "jack_o_lantern"),
            Map.entry("block_of_iron", "iron_block"),
            Map.entry("block_of_gold", "gold_block"),
            Map.entry("block_of_diamond", "diamond_block"),
            Map.entry("block_of_emerald", "emerald_block"),
            Map.entry("block_of_copper", "copper_block"),
            Map.entry("block_of_redstone", "redstone_block"),
            Map.entry("block_of_coal", "coal_block"),
            Map.entry("lapis_lazuli_block", "lapis_block"),
            Map.entry("block_of_lapis_lazuli", "lapis_block"),
            Map.entry("block_of_netherite", "netherite_block"),
            Map.entry("block_of_amethyst", "amethyst_block"),
            Map.entry("block_of_bamboo", "bamboo_block"),
            Map.entry("block_of_stripped_bamboo", "stripped_bamboo_block"));

    /**
     * Trouve l'item d'une cellule : ID ("stone_bricks", "create:andesite_casing")
     * ou nom anglais du jeu ("Oak Planks", "Water Bucket"). Renvoie null si inconnu.
     */
    static Item trouverItem(String cellule) {
        String nom = cellule.trim().toLowerCase().replaceAll("\\s+", "_");
        if (nom.isEmpty()) return null;
        // 1. ID exact ("stone_bricks", "create:andesite_casing") ou alias connu
        String complet = nom.contains(":") ? nom : "minecraft:" + ALIAS.getOrDefault(nom, nom);
        Identifier id = Identifier.tryParse(complet);
        if (id != null && BuiltInRegistries.ITEM.containsKey(id)) return BuiltInRegistries.ITEM.getValue(id);
        // 2. Nom anglais affiché en jeu ("Hay Bale", "Jack o'Lantern"), mods compris
        return nomsAffiches().get(normaliser(cellule));
    }

    /** "Jack o'Lantern" -> "jackolantern" : minuscules, lettres et chiffres seulement. */
    private static String normaliser(String s) {
        return s.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private static Map<String, Item> NOMS_AFFICHES = null;

    /** Nom anglais normalisé -> item, construit une fois depuis les traductions du serveur. */
    private static synchronized Map<String, Item> nomsAffiches() {
        if (NOMS_AFFICHES == null) {
            Map<String, Item> map = new HashMap<>();
            net.minecraft.locale.Language langue = net.minecraft.locale.Language.getInstance();
            for (Item item : BuiltInRegistries.ITEM) {
                String cle = item.getDescriptionId();
                if (langue.has(cle)) map.putIfAbsent(normaliser(langue.getOrDefault(cle)), item);
            }
            NOMS_AFFICHES = map;
        }
        return NOMS_AFFICHES;
    }

    /** Nom anglais le plus proche (fautes de frappe), ou null si rien d'assez proche. */
    static String suggestion(String cellule) {
        String cible = normaliser(cellule);
        if (cible.length() < 3) return null;
        net.minecraft.locale.Language langue = net.minecraft.locale.Language.getInstance();
        String meilleur = null;
        int distanceMin = Math.max(2, cible.length() / 4) + 1;
        for (Item item : nomsAffiches().values()) {
            String affiche = langue.getOrDefault(item.getDescriptionId());
            int d = distance(cible, normaliser(affiche));
            if (d < distanceMin) { distanceMin = d; meilleur = affiche; }
        }
        return meilleur;
    }

    /** Distance de Levenshtein (nombre de lettres à changer). */
    private static int distance(String a, String b) {
        if (Math.abs(a.length() - b.length()) > 4) return 99;
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cout = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cout);
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()];
    }

    /** Lit "2 stacks", "2st", "1 stack 23", "1 shulker", "150". Renvoie -1 si illisible. */
    static int lireQuantite(String texte) {
        Matcher m = QUANTITE.matcher(texte.trim());
        if (!m.matches()) return -1;
        int n = Integer.parseInt(m.group(1));
        String unite = m.group(2) == null ? "" : m.group(2).toLowerCase();
        int mult = unite.startsWith("sh") || unite.equals("sb") ? 64 * 27 : unite.isEmpty() ? 1 : 64;
        int reste = m.group(3) == null ? 0 : Integer.parseInt(m.group(3));
        return n * mult + reste;
    }

    /**
     * Remplit les besoins à partir du CSV. Les colonnes sont détectées ligne par ligne :
     * la première cellule qui est un bloc connu = le bloc, la première qui est une quantité = la quantité.
     * Les autres colonnes (image, "Acquired"…) sont ignorées.
     */
    private String appliquerCsv(String csv) {
        besoins.clear();
        donnees.termine = false;
        sauver();
        List<String> inconnus = new ArrayList<>();
        for (String ligne : csv.split("\\r?\\n")) {
            Item item = null;
            int quantite = -1;
            String texte = null; // premier texte non numérique, pour signaler un bloc inconnu
            for (String cellule : decouper(ligne)) {
                String c = cellule.trim();
                if (c.isEmpty()) continue;
                int q = lireQuantite(c);
                if (q > 0) {
                    if (quantite < 0) quantite = q;
                    continue;
                }
                if (item == null) {
                    item = trouverItem(c);
                    if (item == null && texte == null && !c.equalsIgnoreCase("true") && !c.equalsIgnoreCase("false")) {
                        texte = c;
                    }
                }
            }
            if (quantite <= 0) continue; // ligne d'en-tête ou vide
            if (item == null) {
                if (texte != null) {
                    String proche = suggestion(texte);
                    inconnus.add(proche == null ? texte : texte + " (« " + proche + " » ?)");
                }
                continue;
            }
            besoins.merge(item, quantite, Integer::sum);
        }
        String msg = besoins.size() + " matériaux chargés.";
        if (!inconnus.isEmpty()) {
            msg += " Inconnus (mets le nom anglais du jeu ou l'ID, ex. stone_bricks) : " + String.join(", ", inconnus);
        }
        return msg;
    }

    /** Découpe une ligne CSV en gérant les guillemets. */
    private static List<String> decouper(String ligne) {
        List<String> cols = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean guillemets = false;
        for (int i = 0; i < ligne.length(); i++) {
            char c = ligne.charAt(i);
            if (c == '"') {
                if (guillemets && i + 1 < ligne.length() && ligne.charAt(i + 1) == '"') { cur.append('"'); i++; }
                else guillemets = !guillemets;
            } else if (c == ',' && !guillemets) {
                cols.add(cur.toString()); cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        cols.add(cur.toString());
        return cols;
    }

    // ---------------------------------------------------------------- comptage

    /**
     * Recompte : coffres choisis à la main (contenu des shulkers compris) + inventaires des joueurs connectés.
     * Si un coffre n'est pas chargé (personne à côté), on garde le dernier comptage des coffres.
     */
    public void recompter(MinecraftServer serveur) {
        if (aDesCoffres()) recompterCoffres(serveur); else stockCoffres.clear();
        stock.clear();
        stock.putAll(stockCoffres);
        // + ce que les joueurs connectés ont sur eux (shulkers comprises)
        for (ServerPlayer joueur : serveur.getPlayerList().getPlayers()) {
            Container inventaire = joueur.getInventory();
            for (int i = 0; i < inventaire.getContainerSize(); i++) ajouter(stock, inventaire.getItem(i));
        }
        verifierFin(serveur);
    }

    /** Compte seulement les coffres choisis à la main. Si l'un n'est pas chargé, on garde l'ancien comptage. */
    private void recompterCoffres(MinecraftServer serveur) {
        Identifier dimId = donnees.dimensionCoffres == null ? null : Identifier.tryParse(donnees.dimensionCoffres);
        if (dimId == null) return;
        ServerLevel monde = serveur.getLevel(ResourceKey.create(Registries.DIMENSION, dimId));
        if (monde == null) return;

        Map<Item, Integer> nouveau = new HashMap<>();
        for (int[] c : donnees.coffres) {
            BlockPos pos = new BlockPos(c[0], c[1], c[2]);
            if (monde.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) return;
            if (!(monde.getBlockEntity(pos) instanceof Container conteneur)) continue; // coffre cassé
            for (int i = 0; i < conteneur.getContainerSize(); i++) {
                ajouter(nouveau, conteneur.getItem(i));
            }
        }
        stockCoffres.clear();
        stockCoffres.putAll(nouveau);
    }

    /** À 100 % : message pour tout le monde, la liste disparaît (même si on sort ensuite les blocs). */
    private void verifierFin(MinecraftServer serveur) {
        if (donnees.termine || besoins.isEmpty() || pourcentage() < 100) return;
        donnees.termine = true;
        sauver();
        serveur.getPlayerList().broadcastSystemMessage(Component.literal(
                "§a✔ Chantier « " + donnees.titre + " » terminé : toutes les ressources sont réunies !"), false);
        diffuser(serveur);
    }

    private static void ajouter(Map<Item, Integer> compte, ItemStack stack) {
        if (stack.isEmpty()) return;
        compte.merge(stack.getItem(), stack.getCount(), Integer::sum);
        ItemContainerContents contenu = stack.get(DataComponents.CONTAINER);
        if (contenu != null) {
            contenu.nonEmptyItemCopyStream()
                    .forEach(dedans -> compte.merge(dedans.getItem(), dedans.getCount(), Integer::sum));
        }
    }

    /** Avancement global : chaque ligne est plafonnée à 100 %, le surplus ne compte pas. */
    public int pourcentage() {
        long total = 0, fait = 0;
        for (Map.Entry<Item, Integer> e : besoins.entrySet()) {
            total += e.getValue();
            fait += Math.min(e.getValue(), stock.getOrDefault(e.getKey(), 0));
        }
        return total == 0 ? 0 : (int) (fait * 100 / total);
    }

    // ------------------------------------------------------------------ réseau

    private EtatPayload etat() {
        List<EtatPayload.Ligne> lignes = new ArrayList<>();
        for (Map.Entry<Item, Integer> e : besoins.entrySet()) {
            lignes.add(new EtatPayload.Ligne(
                    BuiltInRegistries.ITEM.getKey(e.getKey()).toString(),
                    e.getValue(),
                    stock.getOrDefault(e.getKey(), 0)));
        }
        List<EtatPayload.Ligne> aRecolter = new ArrayList<>();
        for (Map.Entry<Item, Integer> e : recolte.entrySet()) {
            aRecolter.add(new EtatPayload.Ligne(BuiltInRegistries.ITEM.getKey(e.getKey()).toString(), e.getValue(), 0));
        }
        return new EtatPayload(donnees.titre, lignes, aRecolter);
    }

    private static final EtatPayload VIDE = new EtatPayload("", List.of(), List.of());

    /** Dernier calcul des matières premières à récolter (voir Recettes). */
    private Map<Item, Integer> recolte = new LinkedHashMap<>();

    public void diffuser(MinecraftServer serveur) {
        if (estActif()) {
            try {
                recolte = Recettes.aRecolter(serveur, besoins, stock);
            } catch (RuntimeException e) {
                Chantier.LOGGER.error("[Chantier] Calcul des matières premières impossible", e);
                recolte = new LinkedHashMap<>();
            }
        }
        EtatPayload plein = estActif() ? etat() : VIDE;
        for (ServerPlayer joueur : serveur.getPlayerList().getPlayers()) {
            envoyer(joueur, plein);
        }
    }

    public void envoyerA(ServerPlayer joueur) {
        envoyer(joueur, estActif() ? etat() : VIDE);
    }

    private void envoyer(ServerPlayer joueur, EtatPayload etat) {
        // Les joueurs sans le mod côté client sont ignorés (ils peuvent quand même jouer).
        if (!joueur.connection.hasChannel(EtatPayload.TYPE)) return;
        PacketDistributor.sendToPlayer(joueur, masques.contains(joueur.getUUID()) ? VIDE : etat);
    }

    // ----------------------------------------------------------- sauvegarde

    private static Path fichier() { return FMLPaths.CONFIGDIR.get().resolve("chantier.json"); }

    public void charger() {
        Path f = fichier();
        if (!Files.exists(f)) return;
        try {
            Sauvegarde lu = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Sauvegarde.class);
            if (lu != null) donnees = lu;
            if (donnees.masques == null) donnees.masques = new ArrayList<>();
            if (donnees.coffres == null) donnees.coffres = new ArrayList<>();
            masques.clear();
            for (String s : donnees.masques) masques.add(UUID.fromString(s));
        } catch (IOException | RuntimeException e) {
            Chantier.LOGGER.error("[Chantier] Lecture de chantier.json impossible", e);
        }
    }

    private void sauver() {
        try {
            Files.writeString(fichier(), GSON.toJson(donnees), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Chantier.LOGGER.error("[Chantier] Écriture de chantier.json impossible", e);
        }
    }
}
