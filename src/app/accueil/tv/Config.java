package app.accueil.tv;

/**
 * Réglages à adapter avant de compiler.
 * Settings to adapt before building.
 */
final class Config {

    private Config() { }

    /** Afficher le bandeau des horaires de prière. / Show the prayer-times bar. */
    static final boolean SHOW_PRAYER_TIMES = true;

    /** Nom de la ville affiché au-dessus des horaires. / City name shown above the times. */
    static final String CITY = "Paris";

    /** Coordonnées de la ville, pour le calcul hors ligne. / City coordinates, for the offline calculation. */
    static final double LATITUDE = 48.8566;
    static final double LONGITUDE = 2.3522;

    /** Fuseau horaire (heure d'été/hiver gérée automatiquement). / Time zone (DST handled automatically). */
    static final String TIME_ZONE = "Europe/Paris";

    /**
     * Page « mensuel » de votre ville sur guidemusulman.com (cherchez votre ville sur le site et
     * copiez l'adresse). Laissez "" pour n'utiliser que le calcul hors ligne.
     * Monthly page of your city on guidemusulman.com, or "" to use only the offline calculation.
     */
    static final String GUIDE_MUSULMAN_URL =
            "https://www.guidemusulman.com/horaires-prieres/mensuel/ville/39225/paris-75001";
}
