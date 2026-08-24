package io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing;

/**
 * Durable, authoritative Phase C trial state for a single player.
 *
 * <p>In a full ECS integration this would be represented by Components. Here we keep it as a single
 * immutable-ish state carrier to make mechanics deterministic and unit-testable.
 */
public final class FlowingTrialState {

    public FlowingTrialStatus status = FlowingTrialStatus.INACTIVE;

    // Detection/candidacy
    public double candidateScore = 0.0;
    public int stableTicks = 0;

    // Alignment
    public FlowAlignment alignment = FlowAlignment.NEUTRAL;
    public double alignmentScore = 0.0;

    // Completion
    public double progress = 0.0;

    // Direction memory (for coherence/variance)
    public boolean hasLastDir = false;
    public double lastDirX = 0.0;
    public double lastDirY = 0.0;
    public double lastDirZ = 0.0;

    public FlowingTrialState copy() {
        FlowingTrialState s = new FlowingTrialState();
        s.status = this.status;
        s.candidateScore = this.candidateScore;
        s.stableTicks = this.stableTicks;
        s.alignment = this.alignment;
        s.alignmentScore = this.alignmentScore;
        s.progress = this.progress;
        s.hasLastDir = this.hasLastDir;
        s.lastDirX = this.lastDirX;
        s.lastDirY = this.lastDirY;
        s.lastDirZ = this.lastDirZ;
        return s;
    }
}
