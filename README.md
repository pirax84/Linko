# Linko

Tes liens et videos favoris, ranges par theme, avec **fiches recettes** :
- video : la recette ecrite s'affiche a cote de la video ;
- lien sans video : seulement la photo principale + la recette (ingredients, etapes) ;
- 3 roulettes de classement (moment, famille, style) avec « Autre » libre ;
- titre original ou personnel ; bandeau temperature + position de cuisson ;
- liste de courses, partage depuis TikTok / YouTube / navigateur, synchro Android <-> PC (Supabase).

## Obtenir l'appli (rien a installer sur ton PC)

1. **APK Android** : onglet **Releases** du depot -> `Linko.apk` -> ouvre-le sur ton telephone.
2. **Version PC** : l'adresse « github.io » du depot (Settings > Pages).
Les deux sont refaits automatiquement a chaque changement (onglet **Actions**).

## Reglages a faire une seule fois

- GitHub : Settings > Pages > Source : **GitHub Actions**.
- Supabase : coller `supabase/schema.sql` dans SQL Editor > Run.
  Puis mettre l'adresse et la cle publique dans `src/config.json` (ou dans l'appli : Reglages > Synchronisation).

## Dossiers

- `src/` : le code de l'appli (`build.py` assemble `web/index.html`)
- `web/` : la version web / PC (installable aussi comme PWA)
- `android/` : le projet Android (WebView + « Partager vers Linko »)
- `supabase/` : la base de donnees
- `.github/workflows/build.yml` : fabrique l'APK et met le site en ligne

> Note : `android/keystore/linko.jks` signe l'APK de test. Depot public : c'est pratique pour tester,
> mais ne l'utilise pas pour une publication officielle sur le Play Store.
