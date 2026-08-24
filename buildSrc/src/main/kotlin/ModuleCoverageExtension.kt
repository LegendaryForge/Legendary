abstract class ModuleCoverageExtension {
    internal var exemptionReason: String? = null
    internal var exemptionPredicate: (() -> Boolean)? = null

    /**
     * Declares the condition under which this module legitimately compiles zero
     * sources. If the predicate is false at execution time the exemption does NOT
     * apply and checkModuleCoverage fails — a declaration can document a known
     * environmental gap but can never excuse a real breakage.
     */
    fun zeroCompileAllowedWhen(reason: String, predicate: () -> Boolean) {
        exemptionReason = reason
        exemptionPredicate = predicate
    }
}
