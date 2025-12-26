package com.devmod.arena.autosmoke;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AutosmokeSchedulerTest {

    @Test
    void triggerNowUpdatesLastRunStatus() throws Exception {
        AutosmokeRunner runner = mock(AutosmokeRunner.class);
        AutosmokeRunner.AutosmokeReport report = new AutosmokeRunner.AutosmokeReport(
            null,
            List.of(),
            Duration.ofSeconds(1),
            2,
            0,
            new AutosmokeGuard.GuardResult(true, true, true, true)
        );
        when(runner.runAll()).thenReturn(report);

        AutosmokeScheduler scheduler = new AutosmokeScheduler(
            runner,
            new AutosmokeScheduler.ScheduleConfig(true, LocalTime.of(3, 0), false),
            ZoneId.systemDefault()
        );
        try {
            AutosmokeRunner.AutosmokeReport result = scheduler.triggerNow().get(5, TimeUnit.SECONDS);
            assertEquals(report, result);

            AutosmokeScheduler.RunStatus status = scheduler.getLastRunStatus();
            assertNotNull(status);
            assertTrue(status.success());
            assertEquals(2, status.passedCount());
            assertEquals(0, status.failedCount());
        } finally {
            scheduler.shutdown();
        }
    }
}
