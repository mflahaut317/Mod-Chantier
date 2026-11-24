package fr.maxence.chantier;

import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;

/** Accès sûr à Open Parties and Claims : ne fait rien (et ne plante pas) si le mod est absent. */
public final class Parties {
    private Parties() {}

    private static Boolean present;

    public static boolean present() {
        if (present == null) present = ModList.get().isLoaded("openpartiesandclaims");
        return present;
    }

    static UUID idParty(MinecraftServer serveur, UUID joueur) {
        if (!present()) return null;
        try { return OpenParties.idParty(serveur, joueur); } catch (LinkageError | RuntimeException e) { return null; }
    }

    static List<String[]> membres(MinecraftServer serveur, UUID idParty) {
        if (!present() || idParty == null) return List.of();
        try { return OpenParties.membres(serveur, idParty); } catch (LinkageError | RuntimeException e) { return List.of(); }
    }

    static String nomParty(MinecraftServer serveur, UUID idParty) {
        if (!present() || idParty == null) return "";
        try { return OpenParties.nomParty(serveur, idParty); } catch (LinkageError | RuntimeException e) { return ""; }
    }

    /**
     * Ajoute au chantier les membres de sa party liée qui n'y sont pas encore (et ne sont dans aucun autre chantier).
     * @return les pseudos ajoutés
     */
    static List<String> synchroniser(Projet projet, MinecraftServer serveur) {
        String id = projet.donnees().party;
        if (id == null) return List.of();
        UUID idParty;
        try { idParty = UUID.fromString(id); } catch (IllegalArgumentException e) { return List.of(); }
        List<String> ajoutes = new java.util.ArrayList<>();
        for (String[] m : membres(serveur, idParty)) {
            UUID uuid = UUID.fromString(m[0]);
            if (projet.estMembre(uuid) || Chantiers.de(uuid) != null) continue;
            projet.ajouterMembre(uuid, m[1]);
            ajoutes.add(m[1]);
        }
        return ajoutes;
    }
}
