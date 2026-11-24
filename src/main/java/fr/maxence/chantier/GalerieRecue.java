package fr.maxence.chantier;

/** Dernière galerie commune reçue du serveur (côté joueur). */
public final class GalerieRecue {
    private static volatile GaleriePayload galerie = null;

    private GalerieRecue() {}

    public static void definir(GaleriePayload g) { galerie = g; }

    public static GaleriePayload get() { return galerie; }
}
