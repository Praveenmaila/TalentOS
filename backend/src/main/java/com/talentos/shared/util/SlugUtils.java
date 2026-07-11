package com.talentos.shared.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class SlugUtils {

    private static final Pattern NONLATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]+");
    private static final Pattern DOUBLE_HYPHEN = Pattern.compile("-{2,}");

    private SlugUtils() {
        // Prevent instantiation
    }

    public static String toSlug(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("Input string for slug generation cannot be null or empty");
        }
        
        String nowhitespace = WHITESPACE.matcher(input.trim()).replaceAll("-");
        String normalized = Normalizer.normalize(nowhitespace, Normalizer.Form.NFD);
        String slug = NONLATIN.matcher(normalized).replaceAll("");
        slug = DOUBLE_HYPHEN.matcher(slug).replaceAll("-");
        
        // Remove trailing or leading hyphens
        if (slug.startsWith("-")) {
            slug = slug.substring(1);
        }
        if (slug.endsWith("-")) {
            slug = slug.substring(0, slug.length() - 1);
        }
        
        return slug.toLowerCase(Locale.ENGLISH);
    }
}
