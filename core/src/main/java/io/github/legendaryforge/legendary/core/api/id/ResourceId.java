package io.github.legendaryforge.legendary.core.api.id;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable, validated identifier in the format {@code namespace:path}.
 *
 * Rules:
 * - namespace: [a-z0-9_.-]+
 * - path:      [a-z0-9_./-]+
 * - lowercase only (fail fast)
 */
public record ResourceId(String namespace, String path) {

    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("^[a-z0-9_.-]+$");
    private static final Pattern PATH_PATTERN = Pattern.compile("^[a-z0-9_./-]+$");

    public ResourceId {
        namespace = requireLowercase(namespace, "namespace");
        path = requireLowercase(path, "path");

        if (namespace.isBlank()) {
            throw new IllegalArgumentException("ResourceId namespace must not be blank");
        }
        if (path.isBlank()) {
            throw new IllegalArgumentException("ResourceId path must not be blank");
        }
        if (!NAMESPACE_PATTERN.matcher(namespace).matches()) {
            throw new IllegalArgumentException("Invalid ResourceId namespace: '" + namespace + "'");
        }
        if (!PATH_PATTERN.matcher(path).matches()) {
            throw new IllegalArgumentException("Invalid ResourceId path: '" + path + "'");
        }
    }

    public static ResourceId of(String namespace, String path) {
        return new ResourceId(namespace, path);
    }

    /**
     * Parse {@code namespace:path}. Exactly one ':' is required.
     */
    public static ResourceId parse(String value) {
        Objects.requireNonNull(value, "value");
        int idx = value.indexOf(':');
        if (idx <= 0 || idx != value.lastIndexOf(':') || idx == value.length() - 1) {
            throw new IllegalArgumentException("ResourceId must be in format 'namespace:path' (got '" + value + "')");
        }
        String ns = value.substring(0, idx);
        String p = value.substring(idx + 1);
        return new ResourceId(ns, p);
    }

    /**
     * Create a child id by appending {@code /childPath} to the current path.
     * Example: {@code legendarycore:encounter} + {@code private_arena} -> {@code legendarycore:encounter/private_arena}
     */
    public ResourceId child(String childPath) {
        childPath = requireLowercase(childPath, "childPath");
        if (childPath.isBlank()) {
            throw new IllegalArgumentException("childPath must not be blank");
        }
        String combined = this.path.endsWith("/") ? (this.path + childPath) : (this.path + "/" + childPath);
        return new ResourceId(this.namespace, combined);
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }

    private static String requireLowercase(String value, String field) {
        Objects.requireNonNull(value, field);
        String lower = value.toLowerCase(Locale.ROOT);
        if (!value.equals(lower)) {
            throw new IllegalArgumentException(field + " must be lowercase (got '" + value + "')");
        }
        return value;
    }
}
