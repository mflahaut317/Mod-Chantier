package fr.maxence.chantier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;

/**
 * Convertit ce qui manque en matières premières à récolter (bûches, sable, pierre…),
 * en suivant les recettes du jeu (établi, four, tailleur de pierre), mods compris.
 */
final class Recettes {
    private Recettes() {}

    /** Une façon de fabriquer un objet : ingrédients pour UNE fabrication, et nombre d'objets obtenus. */
    private record Option(Map<Item, Integer> ingredients, int obtenus) {
        double coutParObjet() {
            int total = 0;
            for (int n : ingredients.values()) total += n;
            return (double) total / obtenus;
        }
    }

    /** Matières qu'on récolte directement : on ne remonte pas plus loin. */
    private static final Pattern BASE = Pattern.compile(
            ".*_(log|stem|ingot|seeds|sapling|wool|dye|ore|leaves|tulip)$"
            + "|^(sand|red_sand|gravel|cobblestone|cobbled_deepslate|blackstone|netherrack|dirt|clay_ball|clay"
            + "|diamond|emerald|coal|charcoal|redstone|lapis_lazuli|quartz|amethyst_shard|string|leather|wheat"
            + "|sugar_cane|bamboo|cactus|pumpkin|melon_slice|melon|carrot|potato|beetroot|nether_wart|soul_sand"
            + "|bone_meal|bone|glowstone_dust|slime_ball|honeycomb|egg|feather|flint|snowball|ice|obsidian|end_stone"
            + "|kelp|sweet_berries|apple|stone|andesite|diorite|granite|tuff|calcite|deepslate|basalt|copper_ingot"
            + "|iron_ingot|gold_ingot|netherite_ingot|prismarine_shard|prismarine_crystals|ender_pearl|blaze_rod"
            + "|gunpowder|paper|book|sugar|honey_bottle|water_bucket|lava_bucket|milk_bucket)$");

    private static Map<Item, Option> meilleures = null;

    /** Construit (une fois) la meilleure recette pour chaque objet. */
    private static synchronized Map<Item, Option> index(MinecraftServer serveur) {
        if (meilleures != null) return meilleures;
        Map<Item, Option> map = new HashMap<>();
        if (!(serveur.overworld().recipeAccess() instanceof RecipeManager manager)) {
            meilleures = map;
            return map;
        }
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            try {
                Recipe<?> recette = holder.value();
                if (recette.isSpecial()) continue;
                Option option = lire(recette);
                if (option == null) continue;
                Item resultat = resultatDe(recette);
                if (resultat == null || option.ingredients().containsKey(resultat)) continue;
                Option actuelle = map.get(resultat);
                if (actuelle == null || option.coutParObjet() < actuelle.coutParObjet()) map.put(resultat, option);
            } catch (RuntimeException e) {
                // Recette exotique d'un mod : on l'ignore.
            }
        }
        Chantier.LOGGER.info("[Chantier] {} recettes indexées pour le calcul des matières premières", map.size());
        meilleures = map;
        return map;
    }

    private static ItemStack premier(Ingredient ingredient) {
        Optional<Holder<Item>> item = ingredient.items().findFirst();
        return item.map(h -> new ItemStack(h.value())).orElse(ItemStack.EMPTY);
    }

    private static Item resultatDe(Recipe<?> recette) {
        ItemStack r = resultat(recette);
        return r.isEmpty() ? null : r.getItem();
    }

    private static ItemStack resultat(Recipe<?> recette) {
        if (recette instanceof ShapedRecipe s) {
            List<ItemStack> grille = new ArrayList<>();
            for (Optional<Ingredient> i : s.getIngredients()) grille.add(i.map(Recettes::premier).orElse(ItemStack.EMPTY));
            return s.assemble(CraftingInput.of(s.getWidth(), s.getHeight(), grille));
        }
        if (recette instanceof ShapelessRecipe s) {
            return s.result().create();
        }
        if (recette instanceof SingleItemRecipe s) {
            return s.assemble(new SingleRecipeInput(premier(s.input())));
        }
        return ItemStack.EMPTY;
    }

    private static Option lire(Recipe<?> recette) {
        if (!(recette instanceof ShapedRecipe || recette instanceof ShapelessRecipe || recette instanceof SingleItemRecipe)) {
            return null;
        }
        ItemStack sortie = resultat(recette);
        if (sortie.isEmpty()) return null;
        Map<Item, Integer> ingredients = new LinkedHashMap<>();
        for (Ingredient ingredient : recette.placementInfo().ingredients()) {
            ItemStack p = premier(ingredient);
            if (p.isEmpty()) return null;
            ingredients.merge(p.getItem(), 1, Integer::sum);
        }
        if (ingredients.isEmpty()) return null;
        // Ignore le « décompactage » vers une matière première (bloc de fer -> 9 lingots),
        // mais garde lingot -> 9 pépites (les pépites se fabriquent à partir de lingots).
        int nbIngredients = ingredients.values().stream().mapToInt(Integer::intValue).sum();
        if (nbIngredients == 1 && sortie.getCount() >= 9 && estBase(sortie.getItem())) return null;
        return new Option(ingredients, sortie.getCount());
    }

    private static boolean estBase(Item item) {
        return BASE.matcher(BuiltInRegistries.ITEM.getKey(item).getPath()).matches();
    }

    /**
     * Ce qu'il reste à récolter pour finir le chantier.
     * Ce qui est dans les coffres sert d'abord aux lignes de la liste, le surplus sert aux fabrications.
     */
    static Map<Item, Integer> aRecolter(MinecraftServer serveur, Map<Item, Integer> besoins, Map<Item, Integer> stock) {
        Map<Item, Option> index = index(serveur);
        Map<Item, Integer> reserve = new HashMap<>(stock);
        Map<Item, Integer> manquants = new LinkedHashMap<>();
        for (Map.Entry<Item, Integer> e : besoins.entrySet()) {
            int dispo = reserve.getOrDefault(e.getKey(), 0);
            int utilise = Math.min(dispo, e.getValue());
            reserve.put(e.getKey(), dispo - utilise);
            if (e.getValue() > utilise) manquants.put(e.getKey(), e.getValue() - utilise);
        }
        Map<Item, Integer> resultat = new LinkedHashMap<>();
        for (Map.Entry<Item, Integer> e : manquants.entrySet()) {
            developper(index, reserve, resultat, e.getKey(), e.getValue(), 0, new HashSet<>());
        }
        return resultat;
    }

    private static void developper(Map<Item, Option> index, Map<Item, Integer> reserve, Map<Item, Integer> resultat,
                                   Item item, int quantite, int profondeur, Set<Item> chemin) {
        if (profondeur > 0) {
            int dispo = reserve.getOrDefault(item, 0);
            int pris = Math.min(dispo, quantite);
            if (pris > 0) reserve.put(item, dispo - pris);
            quantite -= pris;
            if (quantite <= 0) return;
        }
        Option option = index.get(item);
        if (option == null || profondeur >= 8 || estBase(item) || chemin.contains(item)) {
            resultat.merge(item, quantite, Integer::sum);
            return;
        }
        int fabrications = (quantite + option.obtenus() - 1) / option.obtenus();
        chemin.add(item);
        for (Map.Entry<Item, Integer> ing : option.ingredients().entrySet()) {
            developper(index, reserve, resultat, ing.getKey(), ing.getValue() * fabrications, profondeur + 1, chemin);
        }
        chemin.remove(item);
    }
}
