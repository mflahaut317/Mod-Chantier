package fr.maxence.chantier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Résumé des chantiers pour d'autres mods (tableau de bord), appelé par réflexion sur le thread du serveur :
 * Class.forName("fr.maxence.chantier.ChantierApi").getMethod("resume").invoke(null)
 */
public final class ChantierApi {
    private ChantierApi() {}

    public static List<Map<String, Object>> resume() {
        List<Map<String, Object>> liste = new ArrayList<>();
        for (Projet p : Chantiers.tous()) {
            Projet.Sauvegarde d = p.donnees();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("titre", d.titre == null ? "Chantier" : d.titre);
            m.put("pourcent", p.pourcentage());
            m.put("materiaux", p.nombreMateriaux());
            m.put("membres", p.nomsMembres());
            m.put("termine", d.termine);
            m.put("coffres", p.nombreCoffres());
            if (d.coffres != null && !d.coffres.isEmpty() && d.dimensionCoffres != null) {
                int[] c = d.coffres.get(0);
                m.put("x", c[0]); m.put("z", c[2]); m.put("dimension", d.dimensionCoffres);
            }
            if (d.holoNom != null) {
                m.put("holo", d.holoNom);
                m.put("holoX", d.holoX); m.put("holoZ", d.holoZ);
                m.put("holoDimension", d.holoDimension == null ? "" : d.holoDimension);
            }
            liste.add(m);
        }
        return liste;
    }
}
