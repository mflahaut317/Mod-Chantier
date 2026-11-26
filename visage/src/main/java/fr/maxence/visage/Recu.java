package fr.maxence.visage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Dernières images reçues, par numéro d'entité. Rempli par le réseau, lu par le côté joueur (aucune classe client ici). */
public final class Recu {
    private Recu() {}

    /** Image JPEG en attente d'affichage (vide = caméra éteinte). */
    public static final Map<Integer, byte[]> IMAGES = new ConcurrentHashMap<>();
}
