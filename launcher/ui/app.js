const { invoke } = window.__TAURI__.core;
const { listen } = window.__TAURI__.event;
const $ = (id) => document.getElementById(id);

// ---------- État du serveur (toutes les 20 s) ----------
async function majServeur() {
  try {
    const e = await invoke('etat_serveur');
    $('point').className = 'point ' + (e.en_ligne ? 'on' : 'off');
    $('etat-texte').textContent = e.en_ligne ? 'En ligne' : 'Hors ligne';
    $('joueurs').textContent = e.en_ligne
      ? (e.joueurs === 0 ? 'Personne pour le moment' : `${e.joueurs} joueur${e.joueurs > 1 ? 's' : ''} connecté${e.joueurs > 1 ? 's' : ''} sur ${e.max}`)
      : 'Le serveur est éteint ou injoignable.';
    $('noms').innerHTML = '';
    for (const n of e.noms) {
      const li = document.createElement('li');
      li.textContent = n;
      $('noms').appendChild(li);
    }
    $('detail').textContent = e.en_ligne ? `Version ${e.version} · ${e.ping_ms} ms` : '';
  } catch (err) {
    $('etat-texte').textContent = 'Hors ligne';
  }
}

// ---------- Nouveautés : derniers changements du pack ----------
function dateCourte(iso) {
  return new Date(iso).toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' });
}
async function majNouveautes() {
  const liste = $('nouveautes');
  try {
    const r = await fetch('https://api.github.com/repos/mflahaut317/survie-pack/commits?per_page=8');
    const commits = await r.json();
    liste.innerHTML = '';
    for (const c of commits) {
      const message = c.commit.message.split('\n')[0];
      const li = document.createElement('li');
      const date = document.createElement('div');
      date.className = 'date';
      date.textContent = dateCourte(c.commit.author.date);
      li.appendChild(date);
      const maj = message.match(/^Mise à jour :\s*(.*)$/);
      if (maj) {
        // « Chantier 2.17.1 Souvenirs 1.0.0 … » -> une étiquette par mod
        const mods = document.createElement('div');
        mods.className = 'mods';
        for (const m of maj[1].matchAll(/(\S+)\s+(\d+(?:\.\d+)+)/g)) {
          const s = document.createElement('span');
          s.className = 'mod';
          s.textContent = `${m[1]} ${m[2]}`;
          mods.appendChild(s);
        }
        li.appendChild(mods);
      } else {
        const msg = document.createElement('div');
        msg.className = 'msg';
        msg.textContent = message;
        li.appendChild(msg);
      }
      liste.appendChild(li);
    }
    if (!commits.length) liste.innerHTML = '<li class="vide">Rien de neuf.</li>';
  } catch (err) {
    liste.innerHTML = '<li class="vide">Nouveautés indisponibles (pas d\'Internet ?).</li>';
  }
}

// ---------- Mémoire ----------
function afficherRam() {
  const go = $('ram').value / 1024;
  $('ram-valeur').textContent = (Number.isInteger(go) ? go : go.toFixed(1).replace('.', ',')) + ' Go';
}
async function initRam() {
  try {
    const total = await invoke('memoire_totale');
    // On laisse au moins 2,5 Go à Windows
    const max = Math.max(2048, Math.min(16384, Math.floor((total - 2560) / 512) * 512));
    $('ram').max = max;
  } catch (e) { /* valeurs par défaut */ }
  let ram = 4096;
  try { ram = parseInt(localStorage.getItem('ram') || '4096', 10); } catch (e) { }
  $('ram').value = Math.min(ram, $('ram').max);
  afficherRam();
}
$('ram').addEventListener('input', () => {
  afficherRam();
  try { localStorage.setItem('ram', $('ram').value); } catch (e) { }
});

// ---------- JOUER ----------
listen('progression', (ev) => {
  $('progression').hidden = false;
  $('progression-texte').textContent = ev.payload.texte;
  $('barre').style.width = Math.round(ev.payload.pourcent * 100) + '%';
});

$('jouer').addEventListener('click', async () => {
  const bouton = $('jouer');
  bouton.disabled = true;
  bouton.textContent = '…';
  $('progression').hidden = false;
  try {
    const issue = await invoke('jouer', { ramMo: parseInt($('ram').value, 10) });
    if (issue === 'connexion') {
      $('progression-texte').textContent =
        'Première fois : dans la fenêtre Prism, clique sur « Comptes » puis « Ajouter Microsoft » et connecte-toi. Ferme Prism, puis reclique sur JOUER.';
      $('barre').style.width = '100%';
    } else {
      $('progression-texte').textContent = 'Le jeu démarre, les mods se mettent à jour… Bon jeu !';
      $('barre').style.width = '100%';
    }
  } catch (err) {
    $('progression-texte').textContent = '⚠ ' + err;
    $('barre').style.width = '0';
  }
  setTimeout(() => {
    bouton.disabled = false;
    bouton.textContent = 'JOUER';
  }, 5000);
});

majServeur();
setInterval(majServeur, 20000);
majNouveautes();
initRam();
