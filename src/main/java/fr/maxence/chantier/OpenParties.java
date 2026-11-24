package fr.maxence.chantier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;

/**
 * Lien avec Open Parties and Claims. Cette classe n'est chargée que si le mod est installé :
 * passer par Parties, qui vérifie sa présence.
 */
final class OpenParties {
    private OpenParties() {}

    /** Party du joueur (null s'il n'en a pas). */
    private static IServerPartyAPI party(MinecraftServer serveur, UUID joueur) {
        return OpenPACServerAPI.get(serveur).getPartyManager().getPartyByMember(joueur);
    }

    static UUID idParty(MinecraftServer serveur, UUID joueur) {
        IServerPartyAPI p = party(serveur, joueur);
        return p == null ? null : p.getId();
    }

    /** Membres d'une party : {uuid, pseudo}. Liste vide si la party n'existe plus. */
    static List<String[]> membres(MinecraftServer serveur, UUID idParty) {
        List<String[]> liste = new ArrayList<>();
        IServerPartyAPI p = OpenPACServerAPI.get(serveur).getPartyManager().getPartyById(idParty);
        if (p == null) return liste;
        p.getMemberInfoStream().forEach(m -> liste.add(new String[]{m.getUUID().toString(), m.getUsername()}));
        return liste;
    }

    static String nomParty(MinecraftServer serveur, UUID idParty) {
        IServerPartyAPI p = OpenPACServerAPI.get(serveur).getPartyManager().getPartyById(idParty);
        return p == null ? "" : "party de " + p.getOwner().getUsername();
    }
}
