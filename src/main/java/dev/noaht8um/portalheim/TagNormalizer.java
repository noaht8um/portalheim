package dev.noaht8um.portalheim;

import java.util.Locale;

public final class TagNormalizer {
    private static final String LEGACY_COLOR_CODES = "(?i)[&\u00A7][0-9A-FK-OR]";

    private TagNormalizer() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }

        String stripped = raw.replaceAll(LEGACY_COLOR_CODES, "");
        return stripped.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }
}
