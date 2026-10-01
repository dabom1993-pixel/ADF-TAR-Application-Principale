# ADF TAR – Application principale

Application Android pour tablette Samsung. Au lancement, elle affiche une fenêtre
avec une grille de tuiles : chaque tuile ouvre une application de la tablette.

- Application installée : la tuile affiche son icône, un appui l'ouvre.
- Application absente : la tuile est grisée, un appui télécharge l'APK dans
  l'application et lance directement son installation (champ `telechargement`),
  sans ouvrir le navigateur ; à défaut de lien, il ouvre sa page sur le Play Store.

## Ajouter une application

Modifier `app/src/main/assets/applications.json` :

```json
[
  { "nom": "Chrome", "package": "com.android.chrome" },
  {
    "nom": "PV Jointage",
    "package": "com.adf.pvjointage",
    "telechargement": "https://github.com/dabom1993-pixel/PVJointage/releases/download/tablette-latest/PVJointage.apk"
  }
]
```

`package` est l'identifiant Android de l'application. On le trouve dans l'adresse
de sa page Play Store (`...details?id=com.android.chrome`).

`telechargement` est facultatif : à utiliser pour les applications qui ne sont
pas sur le Play Store.

`beta` est facultatif : version de test de l'application, avec son propre identifiant
(`package`, par ex. `com.adf.pirobinetterie.beta`) pour s'installer à côté de la
version finale :

```json
"beta": {
  "package": "com.adf.pirobinetterie.beta",
  "telechargement": "https://github.com/.../releases/download/tablette-beta/PIRobinetterie-BETA.apk"
}
```

## Versions bêta

Un appui de 5 secondes sur « Version … » (en haut à droite) demande le mot de passe
des versions bêta. S'il est correct, une section « Versions bêta (test) » s'affiche
sous les applications. Un nouvel appui de 5 secondes permet de la masquer.
Seule l'empreinte SHA-256 du mot de passe est dans le code.

## Obtenir l'APK

À chaque push, GitHub Actions compile l'APK et le publie sur la release à tag fixe
`tablette-latest` de ce dépôt (qui doit rester public).
Le lien de téléchargement ne change jamais :

```
https://github.com/dabom1993-pixel/ADF-TAR-Application-Principale/releases/download/tablette-latest/ADF-TAR.apk
```

## Mettre à jour la tablette

Toucher le **logo Groupe ADF** en haut à gauche : ADF TAR vérifie sa propre version
(affichée en haut à droite) et chaque application installée de la liste, finales et
bêtas. Pour ces applications, la date de l'APK publié (en-tête `Last-Modified`) est
comparée à leur date d'installation sur la tablette. Une fenêtre liste ensuite chaque
application avec une case à cocher ; « Mettre à jour » installe les mises à jour
cochées l'une après l'autre (ADF TAR en dernier). Android demande une confirmation (et, la première fois,
d'autoriser l'installation d'applications depuis ADF TAR).

## Secret GitHub nécessaire (Settings → Secrets and variables → Actions)

- `KEYSTORE_BASE64` : la clé de signature fixe, encodée en base64. Chaque mise à jour
  s'installe ainsi par-dessus la précédente. Sans ce secret, l'APK est compilé
  mais pas publié.

## Installer sur la tablette

1. Sur la tablette, ouvrir le lien de téléchargement ci-dessus dans le navigateur.
2. Ouvrir le fichier téléchargé et autoriser l'installation d'applications
   de sources inconnues si Android le demande.
3. Si une version signée avec une autre clé est déjà installée, la désinstaller
   d'abord (sinon Android affiche « Application non installée »).
