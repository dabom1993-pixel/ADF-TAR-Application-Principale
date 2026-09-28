# ADF TAR – Application principale

Application Android pour tablette Samsung. Au lancement, elle affiche une fenêtre
avec une grille de tuiles : chaque tuile ouvre une application de la tablette.

- Application installée : la tuile affiche son icône, un appui l'ouvre.
- Application absente : la tuile est grisée, un appui ouvre son lien de
  téléchargement (champ `telechargement`) ou, à défaut, sa page sur le Play Store.

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

`telechargement` est facultatif : à utiliser pour les applications qui ne sont
pas sur le Play Store.

`package` est l'identifiant Android de l'application. On le trouve dans l'adresse
de sa page Play Store (`...details?id=com.android.chrome`).

## Obtenir l'APK

À chaque push, GitHub Actions compile l'APK et le publie sur la release à tag fixe
`tablette-latest`. Le lien de téléchargement ne change jamais :

```
https://github.com/dabom1993-pixel/ADF-TAR-Application-Principale/releases/download/tablette-latest/ADF-TAR.apk
```

## Mettre à jour la tablette

Toucher le **logo Groupe ADF** en haut à gauche : l'application compare sa version
(affichée en haut à droite) à la dernière publiée, télécharge la nouvelle si besoin
et lance l'installation. Android demande une confirmation (et, la première fois,
d'autoriser l'installation d'applications depuis ADF TAR).

L'APK est signé avec une clé fixe (`app/debug.keystore`) : chaque mise à jour
s'installe par-dessus la précédente.

## Installer sur la tablette

1. Copier l'APK sur la tablette.
2. L'ouvrir depuis *Mes fichiers* et autoriser l'installation d'applications
   de sources inconnues si Android le demande.
