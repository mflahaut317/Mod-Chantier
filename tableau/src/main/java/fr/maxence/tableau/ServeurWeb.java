package fr.maxence.tableau;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Tout petit serveur web (lecture seule) : la page, l'état en JSON et les captures du journal.
 * Réglages dans config/tableau.properties : port (8099 par défaut) et mot de passe (vide = aucun).
 */
final class ServeurWeb {
    private final ServerSocket socket;
    private final ExecutorService fils = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "Tableau web");
        t.setDaemon(true);
        return t;
    });
    private final String motDePasse;
    private static byte[] page;

    private ServeurWeb(ServerSocket socket, String motDePasse) {
        this.socket = socket;
        this.motDePasse = motDePasse;
    }

    static ServeurWeb demarrer() {
        Properties reglages = reglages();
        int port = 8099;
        try { port = Integer.parseInt(reglages.getProperty("port", "8099").strip()); } catch (NumberFormatException ignored) { }
        try {
            ServerSocket s = new ServerSocket();
            s.setReuseAddress(true);
            s.bind(new InetSocketAddress(port));
            ServeurWeb web = new ServeurWeb(s, reglages.getProperty("motdepasse", "").strip());
            Thread t = new Thread(web::accepter, "Tableau web (accueil)");
            t.setDaemon(true);
            t.start();
            Tableau.LOGGER.info("[Tableau] Tableau de bord sur le port {}", port);
            return web;
        } catch (IOException e) {
            Tableau.LOGGER.error("[Tableau] Impossible d'ouvrir le port {}", port, e);
            return null;
        }
    }

    /** Lit (ou crée) config/tableau.properties. */
    private static Properties reglages() {
        Path f = FMLPaths.CONFIGDIR.get().resolve("tableau.properties");
        Properties p = new Properties();
        try {
            if (!Files.exists(f)) {
                p.setProperty("port", "8099");
                p.setProperty("motdepasse", "");
                try (Writer w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
                    p.store(w, "Tableau de bord web : port, et mot de passe (vide = pas de mot de passe ; identifiant au choix)");
                }
            } else {
                try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) { p.load(r); }
            }
        } catch (IOException ignored) { }
        return p;
    }

    void arreter() {
        try { socket.close(); } catch (IOException ignored) { }
        fils.shutdownNow();
    }

    private void accepter() {
        while (!socket.isClosed()) {
            try {
                Socket client = socket.accept();
                client.setSoTimeout(5000);
                fils.submit(() -> repondre(client));
            } catch (IOException e) {
                if (socket.isClosed()) return;
            } catch (RuntimeException e) {
                // pool fermé pendant l'arrêt
                return;
            }
        }
    }

    private void repondre(Socket client) {
        try (client; InputStream in = client.getInputStream(); OutputStream out = client.getOutputStream()) {
            BufferedReader lecteur = new BufferedReader(new InputStreamReader(in, StandardCharsets.ISO_8859_1));
            String premiere = lecteur.readLine();
            if (premiere == null) return;
            String autorisation = "";
            String ligne;
            while ((ligne = lecteur.readLine()) != null && !ligne.isEmpty()) {
                if (ligne.regionMatches(true, 0, "Authorization:", 0, 14)) autorisation = ligne.substring(14).strip();
            }
            String[] parties = premiere.split(" ");
            if (parties.length < 2 || !parties[0].equals("GET")) { envoyer(out, 405, "text/plain", "Méthode refusée".getBytes(StandardCharsets.UTF_8)); return; }
            if (!autorise(autorisation)) {
                String r = "HTTP/1.1 401 Unauthorized\r\nWWW-Authenticate: Basic realm=\"Serveur Minecraft\"\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
                out.write(r.getBytes(StandardCharsets.ISO_8859_1));
                return;
            }
            String chemin = parties[1];
            int q = chemin.indexOf('?');
            if (q >= 0) chemin = chemin.substring(0, q);
            if (chemin.equals("/") || chemin.equals("/index.html")) {
                envoyer(out, 200, "text/html; charset=utf-8", page());
            } else if (chemin.equals("/api/etat")) {
                envoyer(out, 200, "application/json; charset=utf-8", Instantane.json.getBytes(StandardCharsets.UTF_8));
            } else if (chemin.startsWith("/photo/")) {
                String id = chemin.substring(7).replaceAll("[^a-z0-9-]", "");
                Path f = FMLPaths.CONFIGDIR.get().resolve("souvenirs").resolve("photos").resolve(id + ".jpg");
                if (!id.isEmpty() && Files.exists(f)) envoyer(out, 200, "image/jpeg", Files.readAllBytes(f));
                else envoyer(out, 404, "text/plain", "Introuvable".getBytes(StandardCharsets.UTF_8));
            } else {
                envoyer(out, 404, "text/plain", "Introuvable".getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException | RuntimeException ignored) {
            // navigateur parti en cours de route : rien à faire
        }
    }

    private boolean autorise(String entete) {
        if (motDePasse.isEmpty()) return true;
        if (!entete.regionMatches(true, 0, "Basic ", 0, 6)) return false;
        try {
            String decode = new String(Base64.getDecoder().decode(entete.substring(6).strip()), StandardCharsets.UTF_8);
            int deux = decode.indexOf(':');
            return deux >= 0 && decode.substring(deux + 1).equals(motDePasse);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static void envoyer(OutputStream out, int code, String type, byte[] corps) throws IOException {
        String statut = switch (code) { case 200 -> "OK"; case 404 -> "Not Found"; default -> "Method Not Allowed"; };
        String entete = "HTTP/1.1 " + code + " " + statut + "\r\nContent-Type: " + type + "\r\nContent-Length: " + corps.length
                + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n";
        out.write(entete.getBytes(StandardCharsets.ISO_8859_1));
        out.write(corps);
        out.flush();
    }

    private static byte[] page() throws IOException {
        if (page == null) {
            try (InputStream in = ServeurWeb.class.getResourceAsStream("/assets/tableau/web/index.html")) {
                page = in == null ? "Page introuvable".getBytes(StandardCharsets.UTF_8) : in.readAllBytes();
            }
        }
        return page;
    }
}
