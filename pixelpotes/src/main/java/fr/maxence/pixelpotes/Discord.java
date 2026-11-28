package fr.maxence.pixelpotes;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Statut Discord (« Rich Presence ») : parle au Discord installé sur le PC par son canal local
 * (\\.\pipe\discord-ipc-N sous Windows). Si Discord n'est pas ouvert, on réessaie plus tard, sans rien casser.
 */
final class Discord {
    private Discord() {}

    private static final String APPLICATION = "1576213736744812755";
    private static final String SITE = "https://mflahaut317.github.io/pixel-potes/";

    /** Mis à jour par le jeu (fil principal), lu par le fil Discord. */
    static volatile boolean enPartie = false;
    static volatile int joueurs = 0;
    static volatile long debutPartie = 0;

    private static Thread fil;

    static void demarrer() {
        if (fil != null) return;
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return;
        fil = new Thread(Discord::boucle, "Pixel Potes Discord");
        fil.setDaemon(true);
        fil.start();
    }

    private static void boucle() {
        while (true) {
            try (RandomAccessFile canal = ouvrir()) {
                if (canal != null) {
                    envoyer(canal, 0, "{\"v\":1,\"client_id\":\"" + APPLICATION + "\"}");
                    lire(canal);
                    String dernier = "";
                    while (true) {
                        String activite = activite();
                        if (!activite.equals(dernier)) {
                            envoyer(canal, 1, "{\"cmd\":\"SET_ACTIVITY\",\"args\":{\"pid\":" + ProcessHandle.current().pid()
                                    + ",\"activity\":" + activite + "},\"nonce\":\"" + System.nanoTime() + "\"}");
                            lire(canal);
                            dernier = activite;
                        }
                        Thread.sleep(5000);
                    }
                }
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                // Discord fermé ou redémarré : on réessaie
            }
            try {
                Thread.sleep(30_000);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private static RandomAccessFile ouvrir() {
        for (int i = 0; i < 10; i++) {
            try {
                return new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw");
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    private static String activite() {
        StringBuilder a = new StringBuilder("{");
        if (enPartie) {
            a.append("\"details\":\"La survie entre potes\",");
            int n = joueurs;
            a.append("\"state\":\"").append(n <= 1 ? "Seul sur le serveur" : n + " joueurs connectés").append("\",");
            if (debutPartie > 0) a.append("\"timestamps\":{\"start\":").append(debutPartie).append("},");
        } else {
            a.append("\"details\":\"Dans les menus\",");
        }
        a.append("\"assets\":{\"large_image\":\"logo\",\"large_text\":\"Pixel Potes\"},");
        a.append("\"buttons\":[{\"label\":\"Rejoindre la survie\",\"url\":\"").append(SITE).append("\"}]");
        a.append("}");
        return a.toString();
    }

    /** Une trame : code (4 octets), longueur (4 octets), puis le JSON — en petit-boutiste. */
    private static void envoyer(RandomAccessFile canal, int code, String json) throws IOException {
        byte[] donnees = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer b = ByteBuffer.allocate(8 + donnees.length).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(code).putInt(donnees.length).put(donnees);
        canal.write(b.array());
    }

    private static void lire(RandomAccessFile canal) throws IOException {
        byte[] entete = new byte[8];
        canal.readFully(entete);
        int taille = ByteBuffer.wrap(entete).order(ByteOrder.LITTLE_ENDIAN).getInt(4);
        if (taille < 0 || taille > 1_000_000) throw new IOException("trame invalide");
        canal.readFully(new byte[taille]);
    }
}
