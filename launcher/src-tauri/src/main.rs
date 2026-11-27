// Pas de fenêtre de console sous Windows
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod installation;
mod serveur;

use serde::Serialize;
use tauri::{Emitter, Manager};

const HOTE: &str = "dedication-rubbish.tun.ply.gg";
const PORT: u16 = 25565;

#[derive(Serialize, Clone)]
struct Progression {
    texte: String,
    pourcent: f32,
}

#[tauri::command]
async fn etat_serveur() -> serveur::Etat {
    tauri::async_runtime::spawn_blocking(|| serveur::interroger(HOTE, PORT))
        .await
        .unwrap_or_default()
}

/// Mémoire totale du PC, en Mo.
#[tauri::command]
fn memoire_totale() -> u64 {
    let mut s = sysinfo::System::new();
    s.refresh_memory();
    s.total_memory() / (1024 * 1024)
}

/// JOUER : installe ce qui manque puis lance le jeu sur le serveur. Renvoie "lance" ou "connexion".
#[tauri::command]
async fn jouer(app: tauri::AppHandle, ram_mo: u32) -> Result<String, String> {
    let racine = app
        .path()
        .app_local_data_dir()
        .map_err(|e| format!("Dossier de données introuvable : {e}"))?;
    let app2 = app.clone();
    tauri::async_runtime::spawn_blocking(move || {
        let chemins = installation::Chemins { racine };
        let mut dernier = std::time::Instant::now() - std::time::Duration::from_secs(1);
        let mut etape = |texte: &str, pourcent: f32| {
            // pas plus de 20 messages par seconde vers la fenêtre
            if pourcent >= 1.0 || pourcent == 0.0 || dernier.elapsed().as_millis() > 50 {
                dernier = std::time::Instant::now();
                let _ = app2.emit("progression", Progression { texte: texte.to_string(), pourcent });
            }
        };
        let serveur = format!("{HOTE}:{PORT}");
        installation::jouer(&chemins, ram_mo.clamp(1024, 32768), &serveur, &mut etape).map(|issue| match issue {
            installation::Issue::Lance => "lance".to_string(),
            installation::Issue::Connexion => "connexion".to_string(),
        })
    })
    .await
    .map_err(|e| format!("Erreur interne : {e}"))?
}

fn main() {
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![etat_serveur, memoire_totale, jouer])
        .run(tauri::generate_context!())
        .expect("impossible de démarrer Pixel Potes");
}
