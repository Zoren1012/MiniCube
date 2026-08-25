/*
 * Page de compte MiniCube.
 *
 * Elle dialogue avec le launcher par les routes /api/. Aucune donnee ne sort de la
 * machine : le serveur qui repond est celui du launcher, sur l'adresse de bouclage.
 *
 * Le contenu variable est toujours insere avec textContent, jamais innerHTML : un
 * pseudo contenant du balisage doit s'afficher tel quel, pas s'executer.
 */

'use strict';

const vues = {
  chargement: document.getElementById('vue-chargement'),
  creation: document.getElementById('vue-creation'),
  connexion: document.getElementById('vue-connexion'),
  profil: document.getElementById('vue-profil')
};

/** N'affiche qu'une vue a la fois. */
function afficher(nom) {
  Object.entries(vues).forEach(([cle, element]) => {
    element.hidden = cle !== nom;
  });
}

function montrerErreur(id, message) {
  const element = document.getElementById(id);
  element.textContent = message;
  element.hidden = false;
}

function cacherErreur(id) {
  document.getElementById(id).hidden = true;
}

/** Appelle une route du launcher et renvoie sa reponse. */
async function appeler(route, corps) {
  const reponse = await fetch(route, {
    method: corps ? 'POST' : 'GET',
    headers: corps ? { 'Content-Type': 'application/json' } : {},
    body: corps ? JSON.stringify(corps) : undefined
  });
  const donnees = await reponse.json();
  if (!donnees.ok) {
    throw new Error(donnees.error || 'Erreur inattendue.');
  }
  return donnees;
}

/** Choisit la vue a montrer selon l'etat renvoye par le launcher. */
function appliquer(etat) {
  document.getElementById('version').textContent = 'v' + etat.version;

  if (!etat.hasAccount) {
    afficher('creation');
    return;
  }
  if (!etat.signedIn) {
    afficher('connexion');
    document.getElementById('connexion-pseudo').value = etat.account.username;
    document.getElementById('connexion-mdp').focus();
    return;
  }
  remplirProfil(etat.account);
  afficher('profil');
}

function remplirProfil(compte) {
  document.getElementById('profil-pseudo').textContent = compte.username;
  document.getElementById('profil-role').textContent = compte.roleLabel;
  const pastille = document.getElementById('pastille');
  pastille.style.background = compte.color;
  // L'initiale evite une pastille vide et rappelle le pseudo d'un coup d'oeil.
  pastille.textContent = compte.username.charAt(0).toUpperCase();
  document.getElementById('profil-role-select').value = compte.role;
  document.getElementById('profil-couleur').value = compte.color;

  document.getElementById('stat-parties').textContent = compte.launchCount;
  document.getElementById('stat-temps').textContent = compte.playTime;
  document.getElementById('stat-version').textContent = compte.lastVersion || '—';

  document.getElementById('profil-creation').textContent = joliesDates(compte.createdAt);
  document.getElementById('profil-vu').textContent = joliesDates(compte.lastSeenAt);

  const liste = document.getElementById('liste-versions');
  liste.replaceChildren();
  const versions = compte.recentVersions || [];
  if (versions.length === 0) {
    const vide = document.createElement('li');
    vide.className = 'vide';
    vide.textContent = 'Aucune partie lancée pour le moment';
    liste.append(vide);
    return;
  }
  versions.forEach((version) => {
    const item = document.createElement('li');
    item.textContent = version;
    liste.append(item);
  });
}

/** "2026-08-25T14:30:00" devient "25/08/2026 à 14:30". */
function joliesDates(brut) {
  if (!brut || brut.length < 16) {
    return '—';
  }
  const [date, heure] = brut.split('T');
  const [annee, mois, jour] = date.split('-');
  return `${jour}/${mois}/${annee} à ${heure.slice(0, 5)}`;
}

async function rafraichir() {
  try {
    appliquer(await appeler('/api/state'));
  } catch (erreur) {
    vues.chargement.querySelector('.attente').textContent =
      "Le launcher ne répond plus. Il a peut-être été fermé.";
    afficher('chargement');
  }
}

/* ------------------------------------------------------------------ */
/* Actions                                                             */
/* ------------------------------------------------------------------ */

document.getElementById('form-creation').addEventListener('submit', async (evenement) => {
  evenement.preventDefault();
  cacherErreur('erreur-creation');

  const pseudo = document.getElementById('creation-pseudo').value;
  const motDePasse = document.getElementById('creation-mdp').value;
  const confirmation = document.getElementById('creation-mdp2').value;

  // La confirmation se verifie ici : inutile d'envoyer deux fois le mot de passe.
  if (motDePasse !== confirmation) {
    montrerErreur('erreur-creation', 'Les deux mots de passe ne correspondent pas.');
    return;
  }
  try {
    appliquer(await appeler('/api/register', { username: pseudo, password: motDePasse }));
  } catch (erreur) {
    montrerErreur('erreur-creation', erreur.message);
  }
});

document.getElementById('form-connexion').addEventListener('submit', async (evenement) => {
  evenement.preventDefault();
  cacherErreur('erreur-connexion');
  try {
    appliquer(await appeler('/api/login', {
      username: document.getElementById('connexion-pseudo').value,
      password: document.getElementById('connexion-mdp').value
    }));
  } catch (erreur) {
    montrerErreur('erreur-connexion', erreur.message);
    document.getElementById('connexion-mdp').value = '';
  }
});

document.getElementById('form-profil').addEventListener('submit', async (evenement) => {
  evenement.preventDefault();
  cacherErreur('erreur-profil');
  try {
    appliquer(await appeler('/api/profile', {
      role: document.getElementById('profil-role-select').value,
      color: document.getElementById('profil-couleur').value
    }));
  } catch (erreur) {
    montrerErreur('erreur-profil', erreur.message);
  }
});

document.getElementById('btn-deconnexion').addEventListener('click', async () => {
  appliquer(await appeler('/api/logout', {}));
});

document.getElementById('btn-supprimer').addEventListener('click', async () => {
  const motDePasse = window.prompt(
    'Cette action est irréversible : le compte et ses statistiques seront effacés.\n\n'
    + 'Saisissez votre mot de passe pour confirmer :');
  if (!motDePasse) {
    return;
  }
  try {
    appliquer(await appeler('/api/delete', { password: motDePasse }));
  } catch (erreur) {
    montrerErreur('erreur-profil', erreur.message);
  }
});

rafraichir();
