package fr.maxence.chantier;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Enregistre le paquet. "optional" : un joueur sans le mod peut quand même se connecter. */
public final class Reseau {
    private Reseau() {}

    static void enregistrer(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        // Le gestionnaire ne fait que stocker les données : aucune classe client chargée côté serveur.
        registrar.playToClient(EtatPayload.TYPE, EtatPayload.CODEC,
                (payload, contexte) -> EtatRecu.definir(payload));
    }
}
