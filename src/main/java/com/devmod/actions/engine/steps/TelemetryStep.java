package com.devmod.actions.engine.steps;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

import javax.annotation.Nullable;

import com.devmod.actions.ActionContext;
import com.devmod.actions.ActionResult;
import com.devmod.actions.ActionType;
import com.devmod.actions.catalog.ActionSpec;
import com.devmod.actions.engine.ExecutionContext;
import com.devmod.actions.engine.PipelineStep;
import com.devmod.actions.engine.StepResult;
import com.devmod.telemetry.TelemetryJson;
import com.devmod.telemetry.TelemetryService;

/**
 * Logs invocation to TelemetryService. Records: actionId, origin, player,
 * dimension, actionType, result status, errorCode, durationMs.
 *
 * <p>Compatible with existing telemetry format from ActionRegistry.
 * This step always returns CONTINUE - telemetry failures should not
 * affect action execution.
 */
public final class TelemetryStep implements PipelineStep {

    /**
     * Functional interface for telemetry output, allowing injection in tests.
     */
    @FunctionalInterface
    public interface TelemetrySink {
        void appendActionLine(String line);
    }

    private final TelemetrySink sink;

    /**
     * Creates a TelemetryStep using the global TelemetryService.
     */
    public TelemetryStep() {
        this(TelemetryService.INSTANCE::appendActionLine);
    }

    /**
     * Creates a TelemetryStep with a custom sink (for testing).
     */
    public TelemetryStep(TelemetrySink sink) {
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    @Override
    public StepResult process(ExecutionContext ctx) {
        ActionResult result = ctx.result();
        if (result == null) {
            return StepResult.continueStep();
        }

        ActionSpec spec = ctx.resolvedSpec();
        ActionContext actionCtx = ctx.actionContext();

        try {
            String line = buildTelemetryLine(ctx.actionId(), spec, actionCtx, result);
            sink.appendActionLine(line);
        } catch (Exception e) {
            // Telemetry failures must not break action execution
            org.slf4j.LoggerFactory.getLogger(TelemetryStep.class)
                .warn("[TelemetryStep] Failed to log telemetry for action '{}'",
                    ctx.actionId(), e);
        }

        return StepResult.continueStep();
    }

    /**
     * Builds a NDJSON telemetry line compatible with the existing ActionRegistry format.
     */
    private static String buildTelemetryLine(String actionId, @Nullable ActionSpec spec,
                                              ActionContext context, ActionResult result) {
        String playerName = "server";
        var player = context.getPlayer();
        if (player != null) {
            playerName = player.getGameProfile().getName();
        }

        String dimension = "";
        var level = context.getLevel();
        if (level != null) {
            dimension = level.dimension().location().toString();
        }

        String eventType = switch (result.status()) {
            case OK -> "action_v2_invoked";
            case BLOCKED -> "action_v2_blocked";
            case FAILED -> "action_v2_failed";
        };

        String actionTypeStr = "";
        if (spec != null) {
            ActionType actionType = spec.actionType();
            if (actionType != null) {
                actionTypeStr = actionType.name().toLowerCase(Locale.ROOT);
            }
        }

        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"ts\":\"").append(Instant.now()).append("\",");
        sb.append("\"type\":\"").append(eventType).append("\",");
        sb.append("\"actionId\":\"").append(TelemetryJson.escape(actionId)).append("\",");
        sb.append("\"origin\":\"").append(TelemetryJson.escape(
            context.getOrigin().name().toLowerCase(Locale.ROOT))).append("\",");
        sb.append("\"player\":\"").append(TelemetryJson.escape(playerName)).append("\",");
        sb.append("\"dimension\":\"").append(TelemetryJson.escape(dimension)).append("\",");
        if (!actionTypeStr.isEmpty()) {
            sb.append("\"actionType\":\"").append(actionTypeStr).append("\",");
        }
        sb.append("\"result\":\"").append(result.status().name()).append("\",");
        if (result.errorCode() != null) {
            sb.append("\"errorCode\":\"").append(TelemetryJson.escape(result.errorCode())).append("\",");
        }
        sb.append("\"engine\":\"v2\",");
        sb.append("\"durationMs\":").append(result.durationMs()).append("}");

        return sb.toString();
    }

    @Override
    public String stepName() {
        return "Telemetry";
    }
}
