package fr.maxence.souvenirs;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Ce que le serveur a envoyé (côté joueur) : simple stockage, lu par le code client. */
public final class Recu {
    private Recu() {}

    public static volatile List<Entree> entrees = List.of();
    /** Augmente à chaque nouvelle liste. */
    public static volatile int version;
    /** Demande d'ouverture de l'écran (commande /journal), consommée par le client. */
    public static volatile boolean ouvrir;
    /** Photos complètes reçues (JPEG), par id de souvenir. */
    public static final Map<String, byte[]> PHOTOS = new ConcurrentHashMap<>();
    private static final Map<String, ByteArrayOutputStream> EN_COURS = new ConcurrentHashMap<>();

    static void liste(Paquets.Liste p) {
        entrees = List.copyOf(p.entrees());
        version++;
        if (p.ouvrir()) ouvrir = true;
    }

    static synchronized void photo(Paquets.Photo p) {
        ByteArrayOutputStream t = p.numero() == 0 ? new ByteArrayOutputStream() : EN_COURS.get(p.id());
        if (t == null) return;
        EN_COURS.put(p.id(), t);
        t.writeBytes(p.donnees());
        if (p.numero() == p.total() - 1) {
            EN_COURS.remove(p.id());
            PHOTOS.put(p.id(), t.toByteArray());
        }
    }
}
