package fr.maxence.evenements;

/** Dernier état reçu du serveur (côté joueur), lu par l'écran. */
public final class Recu {
    private Recu() {}

    public static volatile Paquets.Etat etat;
    public static volatile int version;
    public static volatile boolean ouvrir;

    static void etat(Paquets.Etat e) {
        etat = e;
        version++;
        if (e.ouvrirEcran()) ouvrir = true;
    }
}
