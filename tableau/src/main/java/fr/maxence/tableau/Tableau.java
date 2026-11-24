package fr.maxence.tableau;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * Tableau de bord web du serveur : http://<adresse du serveur>:8099 depuis un téléphone du même réseau.
 * Côté serveur uniquement : les joueurs n'ont rien à installer.
 */
@Mod(Tableau.MODID)
public class Tableau {
    public static final String MODID = "tableau";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static ServeurWeb web;
    private static int ticks;

    public Tableau() {
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> {
            Instantane.demarrage = System.currentTimeMillis();
            Instantane.mettreAJour(e.getServer());
            web = ServeurWeb.demarrer();
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> {
            if (web != null) web.arreter();
            web = null;
        });
        // Toutes les 2 secondes, sur le thread du jeu : une photo de l'état, servie telle quelle au navigateur
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            if (++ticks % 40 == 0) Instantane.mettreAJour(e.getServer());
        });
    }
}
