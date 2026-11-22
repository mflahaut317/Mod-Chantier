package fr.maxence.chantier;

/** Dernier état reçu du serveur (utilisé par l'affichage côté client). */
public final class EtatRecu {
    private static volatile EtatPayload etat = null;

    private EtatRecu() {}

    public static void definir(EtatPayload nouveau) {
        etat = nouveau.lignes().isEmpty() ? null : nouveau;
    }

    public static EtatPayload get() { return etat; }

    public static void vider() { etat = null; }
}
