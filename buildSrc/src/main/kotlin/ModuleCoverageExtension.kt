abstract class ModuleCoverageExtension {
    internal var exemptionReason: String? = null
    internal var exemptionPredicate: (() -> Boolean)? = null

    /**
     * Declares the condition under which this module legitimately compiles less than the
     * whole of its source set. If the predicate is false the declaration does NOT apply and
     * checkModuleCoverage fails — a declaration can document a known environmental gap but
     * can never excuse a real breakage.
     *
     * Covers all three shortfalls, each of which fails the build when undeclared:
     *  - PARTIAL — some sources compiled, some excluded (operator decision 2026-08-24)
     *  - EXEMPT  — sources present, none compiled (e.g. excluded for a missing SDK)
     *  - EMPTY   — no sources on disk at all (e.g. scaffolded before its code is written)
     *
     * Renamed from `zeroCompileAllowedWhen` on 2026-08-24. That name described the original
     * single case and had already been stretched once, to cover EMPTY, with a comment asking
     * readers to interpret it as "zero output". Extending it to PARTIAL would have made it
     * simply false: a partial compile is neither zero compile nor zero output. There was one
     * call site.
     *
     * One declaration covers all three states, which has a known weakness worth stating: a
     * module declared for "no SDK, so nothing compiles" will also silently bless a PARTIAL
     * arising from an unrelated cause, such as an errant exclude, as long as the predicate
     * still holds. Splitting the declaration per state would close that; it has not been
     * done, because no module has yet had two independent reasons to fall short.
     *
     * The predicate is invoked while the project is being CONFIGURED, not inside the task
     * action, because the task's inputs must be plain serializable values for the
     * configuration cache. It therefore runs on any invocation that configures this project
     * — `./gradlew help` included — not only on `check`, and regardless of the module's
     * actual compile state. It must be pure and cheap: no I/O, no network, no side effects.
     * That was advice when the predicate ran inside the task action; it is load-bearing now.
     */
    fun incompleteCompilationAllowedWhen(reason: String, predicate: () -> Boolean) {
        exemptionReason = reason
        exemptionPredicate = predicate
    }
}
