//! État du serveur Minecraft (protocole « Server List Ping », comme la liste des serveurs du jeu).

use serde::Serialize;
use std::io::{Read, Write};
use std::net::{TcpStream, ToSocketAddrs};
use std::time::{Duration, Instant};

#[derive(Serialize, Default)]
pub struct Etat {
    pub en_ligne: bool,
    pub joueurs: i64,
    pub max: i64,
    pub noms: Vec<String>,
    pub version: String,
    pub ping_ms: u128,
}

fn ecrire_varint(mut v: u32, sortie: &mut Vec<u8>) {
    loop {
        if v & !0x7F == 0 {
            sortie.push(v as u8);
            return;
        }
        sortie.push(((v & 0x7F) | 0x80) as u8);
        v >>= 7;
    }
}

fn lire_varint(flux: &mut impl Read) -> std::io::Result<u32> {
    let mut resultat = 0u32;
    for i in 0..5 {
        let mut octet = [0u8; 1];
        flux.read_exact(&mut octet)?;
        resultat |= ((octet[0] & 0x7F) as u32) << (7 * i);
        if octet[0] & 0x80 == 0 {
            return Ok(resultat);
        }
    }
    Err(std::io::Error::new(std::io::ErrorKind::InvalidData, "varint trop long"))
}

fn paquet(contenu: &[u8]) -> Vec<u8> {
    let mut p = Vec::new();
    ecrire_varint(contenu.len() as u32, &mut p);
    p.extend_from_slice(contenu);
    p
}

pub fn interroger(hote: &str, port: u16) -> Etat {
    match essayer(hote, port) {
        Ok(e) => e,
        Err(_) => Etat::default(),
    }
}

fn essayer(hote: &str, port: u16) -> std::io::Result<Etat> {
    let adresse = (hote, port)
        .to_socket_addrs()?
        .next()
        .ok_or_else(|| std::io::Error::new(std::io::ErrorKind::NotFound, "adresse introuvable"))?;
    let debut = Instant::now();
    let mut flux = TcpStream::connect_timeout(&adresse, Duration::from_secs(4))?;
    flux.set_read_timeout(Some(Duration::from_secs(4)))?;
    flux.set_write_timeout(Some(Duration::from_secs(4)))?;

    // Poignée de main : paquet 0, protocole -1 (inconnu), adresse, port, état suivant = 1 (statut)
    let mut poignee = Vec::new();
    ecrire_varint(0, &mut poignee);
    ecrire_varint(u32::MAX, &mut poignee);
    ecrire_varint(hote.len() as u32, &mut poignee);
    poignee.extend_from_slice(hote.as_bytes());
    poignee.extend_from_slice(&port.to_be_bytes());
    ecrire_varint(1, &mut poignee);
    flux.write_all(&paquet(&poignee))?;
    flux.write_all(&paquet(&[0]))?; // demande de statut

    let _longueur = lire_varint(&mut flux)?;
    let _id = lire_varint(&mut flux)?;
    let taille = lire_varint(&mut flux)? as usize;
    if taille > 1_000_000 {
        return Err(std::io::Error::new(std::io::ErrorKind::InvalidData, "réponse trop grande"));
    }
    let mut json = vec![0u8; taille];
    flux.read_exact(&mut json)?;
    let ping_ms = debut.elapsed().as_millis();

    let v: serde_json::Value = serde_json::from_slice(&json)
        .map_err(|e| std::io::Error::new(std::io::ErrorKind::InvalidData, e))?;
    let noms = v["players"]["sample"]
        .as_array()
        .map(|liste| liste.iter().filter_map(|j| j["name"].as_str().map(String::from)).collect())
        .unwrap_or_default();
    Ok(Etat {
        en_ligne: true,
        joueurs: v["players"]["online"].as_i64().unwrap_or(0),
        max: v["players"]["max"].as_i64().unwrap_or(0),
        noms,
        version: v["version"]["name"].as_str().unwrap_or("").to_string(),
        ping_ms,
    })
}
