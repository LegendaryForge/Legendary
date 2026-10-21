package io.github.legendaryforge.legendary.quests.stormseeker.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.legendaryforge.legendary.quests.stormseeker.runtime.FlowingTrialHostTick;
import io.github.legendaryforge.legendary.quests.stormseeker.runtime.OutcomeRecordingHostRuntime;
import java.util.List;
import org.junit.jupiter.api.Test;

public final class StormseekerMilestoneOutcomeEmissionTest {

    @Test
    void emitsFlowingTrialOutcomeExactlyOnceOnCoursingLodestoneGrant() {
        var progress = new StormseekerProgress();
        var host = new OutcomeRecordingHostRuntime(List.of("p1"), progress);
        var tick = new FlowingTrialHostTick();

        // Tick until the Coursing Lodestone is granted
        for (int i = 0; i < 500 && !progress.hasCoursingLodestone(); i++) {
            tick.tick(host);
        }

        assertEquals(
                true,
                progress.hasCoursingLodestone(),
                "Test precondition: the Coursing Lodestone should have been granted within tick budget");

        assertEquals(1, host.outcomes.size(), "Flowing Trial outcome should emit once on the Coursing Lodestone grant");

        // Additional ticks must not emit again
        tick.tick(host);
        tick.tick(host);

        assertEquals(1, host.outcomes.size(), "Flowing Trial outcome must not re-emit on subsequent ticks");
    }

    @Test
    void emitsDualLodestonesGrantedWhenStandingLodestoneAlreadyGrantedOnCoursingLodestoneGrant() {
        var progress = new StormseekerProgress();
        progress.grantStandingLodestone();
        var host = new OutcomeRecordingHostRuntime(List.of("p1"), progress);
        var tick = new FlowingTrialHostTick();

        // Tick until the Coursing Lodestone is granted
        for (int i = 0; i < 500 && !progress.hasCoursingLodestone(); i++) {
            tick.tick(host);
        }

        assertEquals(
                true,
                progress.hasCoursingLodestone(),
                "Test precondition: the Coursing Lodestone should have been granted within tick budget");

        assertEquals(2, host.outcomes.size(), "A grant with B already granted must emit A + dual");
        assertEquals(
                StormseekerPhaseMilestone.COURSING_LODESTONE_GRANTED,
                host.outcomes.get(0).milestone());
        assertEquals(
                StormseekerPhaseMilestone.DUAL_LODESTONES_GRANTED,
                host.outcomes.get(1).milestone());

        // Additional ticks must not emit again
        tick.tick(host);
        tick.tick(host);

        assertEquals(2, host.outcomes.size(), "Outcomes must not re-emit on subsequent ticks");
    }
}
