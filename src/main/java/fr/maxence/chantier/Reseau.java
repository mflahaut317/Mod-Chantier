package fr.maxence.chantier;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Enregistre les paquets. "optional" : un joueur sans le mod peut quand même se connecter. */
public final class Reseau {
    private Reseau() {}

    static void enregistrer(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("9").optional();
        // Les gestionnaires côté joueur ne font que stocker les données : aucune classe client chargée côté serveur.
        registrar.playToClient(EtatPayload.TYPE, EtatPayload.CODEC,
                (payload, contexte) -> EtatRecu.definir(payload));
        registrar.playToClient(ConfigPayload.TYPE, ConfigPayload.CODEC,
                (payload, contexte) -> ConfigRecue.recevoir(payload));
        registrar.playToServer(ActionPayload.TYPE, ActionPayload.CODEC, Actions::recevoir);
        registrar.playToServer(ImportPayload.TYPE, ImportPayload.CODEC, Actions::importer);
        registrar.playToClient(GaleriePayload.TYPE, GaleriePayload.CODEC,
                (payload, contexte) -> GalerieRecue.definir(payload));
        registrar.playToServer(PartagePayload.TYPE, PartagePayload.CODEC, Actions::partager);
        registrar.playToServer(ImagePayload.TYPE, ImagePayload.CODEC, Actions::image);
    }
}
