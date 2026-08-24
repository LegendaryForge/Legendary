abstract class ModuleCoverageExtension {
    internal var exemptionReason: String? = null
    internal var exemptionPredicate: (() -> Boolean)? = null

    /**
     * Declares the condition under which this module legitimately contributes nothing
     * to the build. If the predicate is false the exemption does NOT apply and
     * checkModuleCoverage fails — a declaration can document a known environmental gap
     * but can never excuse a real breakage.
     *
     * Covers BOTH ways a module can contribute nothing (operator decision 2026-08-24):
     * sources present but none compiled (e.g. excluded for a missing SDK), and no
     * sources on disk at all (e.g. a module scaffolded before its code is written).
     * The name says "zeroCompile" for continuity; read it as zero output. Requiring a
     * declaration for the second case is what stops the check being defeated by
     * deleting the evidence — moving every file out of a module used to turn a
     * FAIL-worthy state into a green EMPTY.
     *
     * The predicate is invoked while the project is being CONFIGURED, not inside the
     * task action, because the task's inputs must be plain serializable values for the
     * configuration cache. It therefore runs on any invocation that configures this
     * project — `./gradlew help` included — not only on `check`, and it runs regardless
     * of the module's actual compile state. It must be pure and cheap: no I/O, no
     * network, no side effects. That was advice when the predicate ran inside the task
     * action; it is load-bearing now.
     */
    fun zeroCompileAllowedWhen(reason: String, predicate: () -> Boolean) {
        exemptionReason = reason
        exemptionPredicate = predicate
    }
}
