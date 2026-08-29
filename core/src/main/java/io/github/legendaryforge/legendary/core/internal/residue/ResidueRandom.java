package io.github.legendaryforge.legendary.core.internal.residue;

/**
 * Stateless deterministic hashing (SplitMix64 finaliser).
 *
 * <p>Any step of any arm can be computed without replaying the ones before it, which is what makes
 * the network queryable at a point rather than only generable as a whole. Output depends solely on
 * the arguments, so it is stable across runs, machines and JVM versions.
 */
final class ResidueRandom {

    private static final long GOLDEN = 0x9E3779B97F4A7C15L;
    private static final long MIX_A = 0xBF58476D1CE4E5B9L;
    private static final long MIX_B = 0x94D049BB133111EBL;

    private ResidueRandom() {}

    static long mix(long seed, long a, long b) {
        long z = seed ^ (a * GOLDEN) ^ (b * MIX_A);
        z = (z ^ (z >>> 30)) * MIX_A;
        z = (z ^ (z >>> 27)) * MIX_B;
        return z ^ (z >>> 31);
    }

    /** Uniform in {@code [0.0, 1.0)}. */
    static double unit(long seed, long a, long b) {
        return (mix(seed, a, b) >>> 11) * 0x1.0p-53;
    }

    /** Uniform in {@code [-1.0, 1.0)}. */
    static double signed(long seed, long a, long b) {
        return unit(seed, a, b) * 2.0 - 1.0;
    }

    /**
     * 64-bit FNV-1a over a string's UTF-16 code units.
     *
     * <p>Computed here rather than via {@code String.hashCode()} or record {@code hashCode()}:
     * the former is only 32 bits and clusters badly for short similar ids, and the latter carries
     * no cross-JVM stability guarantee at all. This class promises output stable across runs,
     * machines and JVM versions, and a seed derived from an identifier has to keep that promise.
     */
    static long stableHash(String value) {
        long h = 0xCBF29CE484222325L;
        for (int i = 0; i < value.length(); i++) {
            h ^= value.charAt(i);
            h *= 0x100000001B3L;
        }
        return h;
    }
}
