package fr.maxence.souvenirs;

/**
 * Point d'entrée pour les autres mods, appelé par réflexion (aucune dépendance) :
 * Class.forName("fr.maxence.souvenirs.SouvenirsApi").getMethod("chantierTermine", String.class, String.class)
 */
public final class SouvenirsApi {
    private SouvenirsApi() {}

    /** Un chantier (mod Chantier) vient d'être terminé. */
    public static void chantierTermine(String titre, String membres) {
        Journal.ajouterDepuisApi(membres, "chantier", "minecraft:bricks", "🏠 Chantier terminé : " + titre,
                "Bravo à " + membres + " : toute la liste a été réunie !");
    }
}
