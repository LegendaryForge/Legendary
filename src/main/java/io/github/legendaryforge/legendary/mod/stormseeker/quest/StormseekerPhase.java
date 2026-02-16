package io.github.legendaryforge.legendary.mod.stormseeker.quest;

/**
 * Canonical Stormseeker questline phases (v2.0).
 *
 * <p>Rules:
 * <ul>
 *   <li>Exactly one active phase at a time.</li>
 *   <li>Phases only advance forward (no skipping, no rollback).</li>
 * </ul>
 */
public enum StormseekerPhase {
    PHASE_0_UNEASE,
    PHASE_1_STORM_TREK,
    PHASE_1_5_ATTUNEMENT,
    PHASE_2_DUAL_SIGILS,
    PHASE_3_INCOMPLETE_FORM,
    PHASE_4_STORMS_ANSWER,
    PHASE_5_EPILOGUE;

    public boolean isFinal() {
        return this == PHASE_5_EPILOGUE;
    }

    public StormseekerPhase next() {
        return switch (this) {
            case PHASE_0_UNEASE -> PHASE_1_STORM_TREK;
            case PHASE_1_STORM_TREK -> PHASE_1_5_ATTUNEMENT;
            case PHASE_1_5_ATTUNEMENT -> PHASE_2_DUAL_SIGILS;
            case PHASE_2_DUAL_SIGILS -> PHASE_3_INCOMPLETE_FORM;
            case PHASE_3_INCOMPLETE_FORM -> PHASE_4_STORMS_ANSWER;
            case PHASE_4_STORMS_ANSWER -> PHASE_5_EPILOGUE;
            case PHASE_5_EPILOGUE -> PHASE_5_EPILOGUE;
        };
    }
}
