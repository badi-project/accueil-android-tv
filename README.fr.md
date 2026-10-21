# Accueil — un écran d'accueil épuré et sans pub pour Android TV

**Accueil** est un petit lanceur Android TV inspiré de l'écran d'accueil de l'Apple TV : fond sombre
aux couleurs de l'appli sélectionnée, grandes vignettes arrondies qui grossissent quand on passe
dessus, l'heure et la date dans le coin, et c'est tout. Pas de recommandations, pas de bandeaux
sponsorisés, pas de pub, pas de pistage.

Il affiche aussi, si on le souhaite, les **horaires de prière** du jour pour la ville de son choix.

- **Léger et rapide** : environ 40 Ko, en Java pur, sans bibliothèque. Pensé pour les TV modestes
  (testé sur une TV Android TV 11, MediaTek 32 bits, 1,3 Go de RAM).
- **Respectueux de la vie privée** : sa seule connexion sert à lire la page publique des horaires de
  [guidemusulman.com](https://www.guidemusulman.com) pour votre ville. Avec `GUIDE_MUSULMAN_URL = ""`
  ou `SHOW_PRAYER_TIMES = false`, il ne se connecte jamais. Ni statistiques, ni compte.
- **Votre disposition** : appui long sur OK (ou touche Menu) sur une vignette pour la déplacer, la
  masquer ou ouvrir les infos de l'appli. Les applis masquées restent accessibles par le bouton en
  bas de l'écran.

## Horaires de prière

- Ils viennent de la page « mensuel » du Guide Musulman pour votre ville (31 jours d'avance), relue
  toutes les 6 heures et gardée sur la TV : ils s'affichent même pendant une coupure d'Internet.
- Sans Internet et sans copie enregistrée, l'accueil les calcule lui-même (position du soleil pour
  vos coordonnées). Les angles et marges ont été calés sur les calendriers du Guide Musulman
  (méthode 12° par défaut) : une minute d'écart au plus. Le bandeau indique alors
  « CALCULÉ HORS LIGNE ».
- La prochaine prière est mise en avant avec un compte à rebours ; après Icha, le bandeau affiche
  les horaires du lendemain. Le passage heure d'été / heure d'hiver est automatique.

## Réglages

Modifiez [`src/app/accueil/tv/Config.java`](src/app/accueil/tv/Config.java) : `SHOW_PRAYER_TIMES`,
`CITY`, `LATITUDE` / `LONGITUDE`, `TIME_ZONE` et `GUIDE_MUSULMAN_URL` (la page **mensuel** de votre
ville sur guidemusulman.com, ou `""`).

## Compiler

Ni Android Studio ni Gradle. Sous Ubuntu/Debian :

```bash
sudo apt-get install android-sdk-platform-23 aapt dalvik-exchange zipalign apksigner default-jdk
./build.sh            # → build/AccueilTV.apk
```

La première compilation crée une clé de signature locale (`keystore.jks`, ignorée par git) :
gardez-la, Android n'accepte les mises à jour que signées avec la même clé.

## Installer sur la TV

1. Sur la TV : *Paramètres › Préférences de l'appareil › À propos*, appuyez 7 fois sur *Build*,
   puis *Options pour les développeurs › Débogage USB* → activé. Notez l'adresse IP de la TV
   (*À propos › État*).
2. Depuis un ordinateur sur le même réseau :

   ```bash
   bash scripts/install.sh <ADRESSE_IP_DE_LA_TV>
   ```

   Acceptez « Autoriser le débogage USB » sur la TV (« Toujours autoriser »).

### En option : nettoyer une TV fabriquée par Vestel (Edenwood…)

```bash
bash scripts/debloat-vestel.sh <ADRESSE_IP_DE_LA_TV> --dns   # désactive l'accueil Google, la télémétrie Vestel, les pubs…
bash scripts/restore-vestel.sh <ADRESSE_IP_DE_LA_TV>         # tout remettre comme avant
```

Les paquets système sont seulement *désactivés* (rien n'est supprimé) et le script de restauration
les réactive. À utiliser à vos risques.

## Licence

MIT — voir [LICENSE](LICENSE). Horaires fournis par guidemusulman.com (projet non affilié) ;
vérifiez les horaires de prière en commun auprès de votre mosquée.
