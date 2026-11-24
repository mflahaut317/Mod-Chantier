package fr.maxence.chantier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Volumes 3D des projets de la galerie reçus du serveur (côté joueur), par id. */
public final class VolumeRecu {
    private static final Map<String, byte[]> VOLUMES = new ConcurrentHashMap<>();

    private VolumeRecu() {}

    public static void definir(VolumePayload p) { VOLUMES.put(p.id(), p.donnees()); }

    public static byte[] get(String id) { return VOLUMES.get(id); }
}
