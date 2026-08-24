abstract class ModuleCoverageExtension {
    internal var exemptionReason: String? = null
    internal var exemptionPredicate: (() -> Boolean)? = null

    /**
     * Declares the condition under which this module legitimately compiles zero
     * sources. If the predicate is false at execution time the exemption does NOT
     * apply and checkModuleCoverage fails — a declaration can document a known
     * environmental gap but can never excuse a real breakage.
     *
     * The predicate is invoked on **every** `check` run, regardless of the
     * module's actual compile state -- including runs where the module compiles
     * fully and the exemption is never needed. It must therefore be pure and
     * cheap: no I/O, no network, no side effects. A future predicate that shells
     * out or hits the network turns every `check` invocation into a slow or
     * flaky one, for a check that is skipped almost every time it runs.
     */
    fun zeroCompileAllowedWhen(reason: String, predicate: () -> Boolean) {
        exemptionReason = reason
        exemptionPredicate = predicate
    }
}
