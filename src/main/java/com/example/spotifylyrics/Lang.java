package com.example.spotifylyrics;

/** Sprache der Menüs: 0 = Deutsch, 1 = English. */
final class Lang {
    private Lang() {}

    static final String[] NAMES = {"Deutsch", "English"};

    static boolean en() {
        return Config.INSTANCE.lang == 1;
    }

    /** Text in der gewählten Sprache. */
    static String t(String de, String en) {
        return en() ? en : de;
    }
}
