package design.inherent.skyweavecompat.configuration;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;

/** Search resolved interface text, independently of optional mod classes or the current language. */
public final class SettingsSearch {
    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }
    public static boolean matches(String query, String... text) {
        String haystack = normalize(String.join(" ", text));
        return Arrays.stream(normalize(query).strip().split("\\s+")).allMatch(haystack::contains);
    }
    private SettingsSearch() {}
}
