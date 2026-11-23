package fr.maxence.chantier;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Enregistre les paquets. "optional" : un joueur sans le mod peut quand même se connecter. */
public final class Reseau {
    private Reseau() {}

    static void enregistrer(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("3").optional();
        // Les gestionnaires côté joueur ne font que stocker les données : aucune classe client chargée côté serveur.
        registrar.playToClient(EtatPayload.TYPE, EtatPayload.CODEC,
                (payload, contexte) -> EtatRecu.definir(payload));
        registrar.playToClient(ConfigPayload.TYPE, ConfigPayload.CODEC,
                (payload, contexte) -> ConfigRecue.recevoir(payload));
        registrar.playToServer(ActionPayload.TYPE, ActionPayload.CODEC, Actions::recevoir);
    }
}
