package com.devmod.arena.integration;

import com.devmod.arena.builder.ArenaBuilder;
import com.devmod.arena.builder.AsyncArenaBuilder;
import com.devmod.arena.policy.ArenaPolicy;
import com.devmod.arena.policy.ArenaPolicyRegistry;
import com.devmod.arena.policy.PolicyResolver;
import com.devmod.arena.policy.ResolvedArena;
import com.devmod.arena.registry.ArenaTemplate;
import com.devmod.arena.registry.ArenaTemplateRegistry;
import com.devmod.arena.telemetry.ArenaTelemetry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ArenaQuestIntegrationAsyncTest {

    @Test
    void prepareArenaRoutesAsyncPriority() throws Exception {
        ArenaTemplate template = ArenaTemplate.builder("async_template")
            .buildSettings(new ArenaTemplate.BuildSettings(
                ArenaTemplate.BuildSettings.Priority.ASYNC,
                ArenaTemplate.BuildSettings.Order.FLOOR_FIRST))
            .build();

        ResolvedArena resolved = ResolvedArena.create(template, ArenaPolicy.DEFAULT, Map.of());

        ArenaTemplateRegistry templateRegistry = mock(ArenaTemplateRegistry.class);
        ArenaPolicyRegistry policyRegistry = mock(ArenaPolicyRegistry.class);
        PolicyResolver policyResolver = mock(PolicyResolver.class);
        ArenaBuilder arenaBuilder = mock(ArenaBuilder.class);
        AsyncArenaBuilder asyncArenaBuilder = mock(AsyncArenaBuilder.class);
        ArenaTelemetry telemetry = new ArenaTelemetry(event -> {});
        Executor executor = Runnable::run;

        when(policyRegistry.loadAllSources(any())).thenReturn(
            new ArenaPolicyRegistry.LoadResult(List.of(), List.of()));
        when(policyResolver.resolve(any())).thenReturn(resolved);
        when(asyncArenaBuilder.submitBuildAsync(any(), any(), anyInt(), anyInt(), anyInt()))
            .thenReturn(CompletableFuture.completedFuture(
                AsyncArenaBuilder.AsyncBuildResult.success(UUID.randomUUID(), template.id(), 1, 5)));

        ArenaQuestIntegration integration = new ArenaQuestIntegration(
            templateRegistry,
            policyRegistry,
            policyResolver,
            arenaBuilder,
            asyncArenaBuilder,
            telemetry,
            executor
        );

        ArenaQuestIntegration.QuestContext context = ArenaQuestIntegration.QuestContext.builder()
            .leaderId(UUID.randomUUID())
            .questType("endurance")
            .difficulty("normal")
            .playerCount(1)
            .build();

        ArenaQuestIntegration.PrepareResult result = integration.prepareArena(context).get();
        assertTrue(result.success());
        assertNotNull(result.handle());

        verify(asyncArenaBuilder).submitBuildAsync(any(), eq(template), anyInt(), anyInt(), anyInt());
        verifyNoInteractions(arenaBuilder);
    }

    @Test
    void prepareArenaRoutesSyncPriority() throws Exception {
        ArenaTemplate template = ArenaTemplate.builder("sync_template")
            .buildSettings(new ArenaTemplate.BuildSettings(
                ArenaTemplate.BuildSettings.Priority.SYNC,
                ArenaTemplate.BuildSettings.Order.FLOOR_FIRST))
            .build();

        ResolvedArena resolved = ResolvedArena.create(template, ArenaPolicy.DEFAULT, Map.of());

        ArenaTemplateRegistry templateRegistry = mock(ArenaTemplateRegistry.class);
        ArenaPolicyRegistry policyRegistry = mock(ArenaPolicyRegistry.class);
        PolicyResolver policyResolver = mock(PolicyResolver.class);
        ArenaBuilder arenaBuilder = mock(ArenaBuilder.class);
        AsyncArenaBuilder asyncArenaBuilder = mock(AsyncArenaBuilder.class);
        ArenaTelemetry telemetry = new ArenaTelemetry(event -> {});
        Executor executor = Runnable::run;

        when(policyRegistry.loadAllSources(any())).thenReturn(
            new ArenaPolicyRegistry.LoadResult(List.of(), List.of()));
        when(policyResolver.resolve(any())).thenReturn(resolved);
        when(arenaBuilder.build(eq(template), eq(resolved.policy().id()), eq(resolved.policy().version()),
            anyInt(), anyInt(), anyInt()))
            .thenReturn(ArenaBuilder.BuildResult.success(UUID.randomUUID(), template.id(), 1, 5));

        ArenaQuestIntegration integration = new ArenaQuestIntegration(
            templateRegistry,
            policyRegistry,
            policyResolver,
            arenaBuilder,
            asyncArenaBuilder,
            telemetry,
            executor
        );

        ArenaQuestIntegration.QuestContext context = ArenaQuestIntegration.QuestContext.builder()
            .leaderId(UUID.randomUUID())
            .questType("endurance")
            .difficulty("normal")
            .playerCount(1)
            .build();

        ArenaQuestIntegration.PrepareResult result = integration.prepareArena(context).get();
        assertTrue(result.success());
        assertNotNull(result.handle());

        verify(arenaBuilder).build(eq(template), eq(resolved.policy().id()), eq(resolved.policy().version()),
            anyInt(), anyInt(), anyInt());
        verifyNoInteractions(asyncArenaBuilder);
    }
}
