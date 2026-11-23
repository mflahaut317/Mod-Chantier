package fr.maxence.chantier;

/**
 * Derniers réglages reçus du serveur (côté joueur). Aucune classe client ici :
 * le côté client branche {@link #ouvrirEcran} au démarrage pour ouvrir l'écran.
 */
public final class ConfigRecue {
    private static volatile ConfigPayload config = null;

    /** Branché par le client : ouvre l'écran Chantier. Reste vide sur le serveur. */
    public static volatile Runnable ouvrirEcran = () -> {};

    private ConfigRecue() {}

    public static void recevoir(ConfigPayload nouvelle) {
        config = nouvelle;
        if (nouvelle.ouvrir()) ouvrirEcran.run();
    }

    public static ConfigPayload get() { return config; }

    public static void vider() { config = null; }
}
