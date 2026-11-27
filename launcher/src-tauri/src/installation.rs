//! Installation et lancement : Java 25, Prism Launcher (version portable, cachée), instance de la survie, packwiz.

use std::fs;
use std::io::{Read, Write};
use std::path::{Path, PathBuf};
use std::process::Command;

pub const INSTANCE: &str = "survie";
const NOM_INSTANCE: &str = "Pixel Potes";
const PACK: &str = "https://raw.githubusercontent.com/mflahaut317/survie-pack/main/pack.toml";
const BOOTSTRAP: &str =
    "https://github.com/packwiz/packwiz-installer-bootstrap/releases/latest/download/packwiz-installer-bootstrap.jar";
const JAVA: &str =
    "https://api.adoptium.net/v3/binary/latest/25/ga/windows/x64/jre/hotspot/normal/eclipse";
const PRISM_API: &str = "https://api.github.com/repos/PrismLauncher/PrismLauncher/releases/latest";
const AGENT: &str = "PixelPotes-Launcher";

/// Résultat d'un clic sur JOUER.
pub enum Issue {
    /// Le jeu se lance.
    Lance,
    /// Aucun compte Microsoft : Prism est ouvert pour se connecter.
    Connexion,
}

pub struct Chemins {
    pub racine: PathBuf,
}

impl Chemins {
    fn java_dir(&self) -> PathBuf { self.racine.join("java") }
    fn javaw(&self) -> PathBuf { self.java_dir().join("bin").join("javaw.exe") }
    fn prism_dir(&self) -> PathBuf { self.racine.join("prism") }
    fn prism_exe(&self) -> PathBuf { self.prism_dir().join("prismlauncher.exe") }
    fn instance_dir(&self) -> PathBuf { self.prism_dir().join("instances").join(INSTANCE) }
    fn jeu_dir(&self) -> PathBuf { self.instance_dir().join("minecraft") }
}

fn erreur(contexte: &str, e: impl std::fmt::Display) -> String {
    format!("{contexte} : {e}")
}

/// Télécharge une URL dans un fichier, en signalant la progression (0.0 à 1.0).
fn telecharger(url: &str, destination: &Path, progression: &mut dyn FnMut(f32)) -> Result<(), String> {
    let reponse = ureq::get(url)
        .set("User-Agent", AGENT)
        .call()
        .map_err(|e| erreur("Téléchargement impossible", e))?;
    let total: u64 = reponse.header("Content-Length").and_then(|v| v.parse().ok()).unwrap_or(0);
    let mut lecteur = reponse.into_reader();
    let temporaire = destination.with_extension("part");
    let mut fichier = fs::File::create(&temporaire).map_err(|e| erreur("Écriture impossible", e))?;
    let mut tampon = vec![0u8; 64 * 1024];
    let mut recu: u64 = 0;
    loop {
        let n = lecteur.read(&mut tampon).map_err(|e| erreur("Téléchargement interrompu", e))?;
        if n == 0 {
            break;
        }
        fichier.write_all(&tampon[..n]).map_err(|e| erreur("Écriture impossible", e))?;
        recu += n as u64;
        if total > 0 {
            progression(recu as f32 / total as f32);
        }
    }
    drop(fichier);
    fs::rename(&temporaire, destination).map_err(|e| erreur("Écriture impossible", e))?;
    Ok(())
}

fn texte(url: &str) -> Result<String, String> {
    ureq::get(url)
        .set("User-Agent", AGENT)
        .call()
        .map_err(|e| erreur("Connexion impossible", e))?
        .into_string()
        .map_err(|e| erreur("Réponse illisible", e))
}

/// Décompresse un zip ; si tout est dans un seul dossier racine, ce dossier est « aplati ».
fn decompresser(zip: &Path, destination: &Path) -> Result<(), String> {
    let fichier = fs::File::open(zip).map_err(|e| erreur("Archive introuvable", e))?;
    let mut archive = zip::ZipArchive::new(fichier).map_err(|e| erreur("Archive abîmée", e))?;
    // Dossier racine commun ?
    let mut racine: Option<String> = None;
    let mut commun = true;
    for i in 0..archive.len() {
        let nom = archive.by_index(i).map_err(|e| erreur("Archive abîmée", e))?.name().to_string();
        let premier = nom.split('/').next().unwrap_or("").to_string();
        if !nom.contains('/') {
            commun = false;
            break;
        }
        match &racine {
            None => racine = Some(premier),
            Some(r) if *r != premier => {
                commun = false;
                break;
            }
            _ => {}
        }
    }
    let prefixe = if commun { racine.map(|r| format!("{r}/")) } else { None };
    fs::create_dir_all(destination).map_err(|e| erreur("Dossier impossible", e))?;
    for i in 0..archive.len() {
        let mut entree = archive.by_index(i).map_err(|e| erreur("Archive abîmée", e))?;
        let Some(chemin) = entree.enclosed_name() else { continue };
        let mut relatif = chemin.to_string_lossy().replace('\\', "/");
        if let Some(p) = &prefixe {
            match relatif.strip_prefix(p.as_str()) {
                Some(r) => relatif = r.to_string(),
                None => continue,
            }
        }
        if relatif.is_empty() {
            continue;
        }
        let cible = destination.join(&relatif);
        if entree.is_dir() {
            fs::create_dir_all(&cible).map_err(|e| erreur("Dossier impossible", e))?;
        } else {
            if let Some(parent) = cible.parent() {
                fs::create_dir_all(parent).map_err(|e| erreur("Dossier impossible", e))?;
            }
            let mut sortie = fs::File::create(&cible).map_err(|e| erreur("Écriture impossible", e))?;
            std::io::copy(&mut entree, &mut sortie).map_err(|e| erreur("Écriture impossible", e))?;
        }
    }
    Ok(())
}

fn installer_java(c: &Chemins, etape: &mut dyn FnMut(&str, f32)) -> Result<(), String> {
    if c.javaw().exists() {
        return Ok(());
    }
    etape("Téléchargement de Java…", 0.0);
    fs::create_dir_all(&c.racine).map_err(|e| erreur("Dossier impossible", e))?;
    let zip = c.racine.join("java.zip");
    telecharger(JAVA, &zip, &mut |p| etape("Téléchargement de Java…", p))?;
    etape("Installation de Java…", 1.0);
    let _ = fs::remove_dir_all(c.java_dir());
    decompresser(&zip, &c.java_dir())?;
    let _ = fs::remove_file(&zip);
    if !c.javaw().exists() {
        return Err("Java n'a pas pu être installé.".into());
    }
    Ok(())
}

fn installer_prism(c: &Chemins, etape: &mut dyn FnMut(&str, f32)) -> Result<(), String> {
    if !c.prism_exe().exists() {
        etape("Recherche de Prism Launcher…", 0.0);
        let infos: serde_json::Value =
            serde_json::from_str(&texte(PRISM_API)?).map_err(|e| erreur("Réponse illisible", e))?;
        let url = infos["assets"]
            .as_array()
            .and_then(|liste| {
                let candidats: Vec<&serde_json::Value> = liste
                    .iter()
                    .filter(|a| {
                        let n = a["name"].as_str().unwrap_or("").to_lowercase();
                        n.contains("windows") && n.contains("portable") && n.ends_with(".zip") && !n.contains("arm64")
                    })
                    .collect();
                candidats
                    .iter()
                    .find(|a| a["name"].as_str().unwrap_or("").contains("MSVC"))
                    .or(candidats.first())
                    .and_then(|a| a["browser_download_url"].as_str().map(String::from))
            })
            .ok_or("Version portable de Prism introuvable.")?;
        let zip = c.racine.join("prism.zip");
        telecharger(&url, &zip, &mut |p| etape("Téléchargement de Prism Launcher…", p))?;
        etape("Installation de Prism Launcher…", 1.0);
        decompresser(&zip, &c.prism_dir())?;
        let _ = fs::remove_file(&zip);
        if !c.prism_exe().exists() {
            return Err("Prism Launcher n'a pas pu être installé.".into());
        }
    }
    // Mode portable : toutes les données de Prism restent dans son dossier
    let portable = c.prism_dir().join("portable.txt");
    if !portable.exists() {
        let _ = fs::write(&portable, "");
    }
    // Réglages de Prism (évite l'assistant de premier lancement autant que possible)
    let cfg = c.prism_dir().join("prismlauncher.cfg");
    if !cfg.exists() {
        let java = c.javaw().to_string_lossy().replace('\\', "/");
        let contenu = format!(
            "[General]\nLanguage=fr\nApplicationTheme=dark\nIconTheme=pe_colored\nBackgroundCat=kitteh\n\
             JavaPath={java}\nAutomaticJavaSwitch=false\nAutomaticJavaDownload=false\nUserAskedAboutAutomaticJavaDownload=true\n\
             IgnoreJavaWizard=true\nCloseAfterLaunch=true\nQuitAfterGameStop=true\nShowConsole=false\nShowConsoleOnError=true\n\
             PastebinType=0\nPastebinCustomAPIBase=\nMinMemAlloc=1024\nMaxMemAlloc=4096\n"
        );
        let _ = fs::write(&cfg, contenu);
    }
    Ok(())
}

/// Versions demandées par le pack (minecraft, neoforge), lues dans pack.toml.
fn versions_du_pack() -> Result<(String, String), String> {
    let pack = texte(PACK)?;
    let mut minecraft = String::new();
    let mut neoforge = String::new();
    for ligne in pack.lines() {
        let ligne = ligne.trim();
        if let Some((cle, valeur)) = ligne.split_once('=') {
            let valeur = valeur.trim().trim_matches('"').to_string();
            match cle.trim() {
                "minecraft" => minecraft = valeur,
                "neoforge" => neoforge = valeur,
                _ => {}
            }
        }
    }
    if minecraft.is_empty() || neoforge.is_empty() {
        return Err("Le pack ne donne pas les versions de Minecraft et NeoForge.".into());
    }
    Ok((minecraft, neoforge))
}

fn preparer_instance(c: &Chemins, ram_mo: u32, etape: &mut dyn FnMut(&str, f32)) -> Result<(), String> {
    etape("Vérification du pack…", 0.0);
    fs::create_dir_all(c.jeu_dir()).map_err(|e| erreur("Dossier impossible", e))?;

    let bootstrap = c.jeu_dir().join("packwiz-installer-bootstrap.jar");
    if !bootstrap.exists() {
        telecharger(BOOTSTRAP, &bootstrap, &mut |p| etape("Téléchargement de l'outil de mods…", p))?;
    }

    // Versions du jeu : on ne réécrit mmc-pack.json que si elles ont changé (Prism le complète lui-même)
    let (minecraft, neoforge) = versions_du_pack()?;
    let pack_json = c.instance_dir().join("mmc-pack.json");
    let a_jour = fs::read_to_string(&pack_json)
        .ok()
        .and_then(|t| serde_json::from_str::<serde_json::Value>(&t).ok())
        .map(|v| {
            let comp = v["components"].as_array().cloned().unwrap_or_default();
            let version = |uid: &str| {
                comp.iter()
                    .find(|x| x["uid"] == uid)
                    .and_then(|x| x["version"].as_str().map(String::from))
                    .unwrap_or_default()
            };
            version("net.minecraft") == minecraft && version("net.neoforged") == neoforge
        })
        .unwrap_or(false);
    if !a_jour {
        let contenu = serde_json::json!({
            "components": [
                { "uid": "net.minecraft", "version": minecraft, "important": true },
                { "uid": "net.neoforged", "version": neoforge }
            ],
            "formatVersion": 1
        });
        fs::write(&pack_json, serde_json::to_string_pretty(&contenu).unwrap())
            .map_err(|e| erreur("Écriture impossible", e))?;
    }

    // Réglages de l'instance, réécrits à chaque lancement (mémoire choisie, Java, synchronisation des mods)
    let java = c.javaw().to_string_lossy().replace('\\', "/");
    let min = 1024.min(ram_mo);
    let cfg = format!(
        "[General]\nConfigVersion=1.2\nInstanceType=OneSix\nname={NOM_INSTANCE}\niconKey=default\n\
         OverrideJavaLocation=true\nJavaPath={java}\nAutomaticJava=false\n\
         OverrideMemory=true\nMinMemAlloc={min}\nMaxMemAlloc={ram_mo}\n\
         OverrideCommands=true\nPreLaunchCommand=\"$INST_JAVA\" -jar packwiz-installer-bootstrap.jar {PACK}\n"
    );
    fs::write(c.instance_dir().join("instance.cfg"), cfg).map_err(|e| erreur("Écriture impossible", e))?;
    Ok(())
}

/// Un compte Microsoft est-il déjà connecté dans Prism ?
fn compte_connecte(c: &Chemins) -> bool {
    fs::read_to_string(c.prism_dir().join("accounts.json"))
        .ok()
        .and_then(|t| serde_json::from_str::<serde_json::Value>(&t).ok())
        .and_then(|v| v["accounts"].as_array().map(|a| !a.is_empty()))
        .unwrap_or(false)
}

pub fn jouer(c: &Chemins, ram_mo: u32, serveur: &str, etape: &mut dyn FnMut(&str, f32)) -> Result<Issue, String> {
    installer_java(c, etape)?;
    installer_prism(c, etape)?;
    preparer_instance(c, ram_mo, etape)?;
    if !compte_connecte(c) {
        etape("Connexion à ton compte Microsoft…", 1.0);
        Command::new(c.prism_exe())
            .current_dir(c.prism_dir())
            .spawn()
            .map_err(|e| erreur("Prism ne démarre pas", e))?;
        return Ok(Issue::Connexion);
    }
    etape("Lancement du jeu…", 1.0);
    Command::new(c.prism_exe())
        .current_dir(c.prism_dir())
        .args(["--launch", INSTANCE, "--server", serveur])
        .spawn()
        .map_err(|e| erreur("Prism ne démarre pas", e))?;
    Ok(Issue::Lance)
}
