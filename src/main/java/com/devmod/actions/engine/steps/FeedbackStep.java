package com.devmod.actions.engine.steps;

import net.minecraft.network.chat.Component;

import com.devmod.actions.ActionResult;
import com.devmod.actions.catalog.ActionSpec;
import com.devmod.actions.engine.ExecutionContext;
import com.devmod.actions.engine.PipelineStep;
import com.devmod.actions.engine.StepResult;

/**
 * Sends UI feedback based on the ActionResult. Handles error messages
 * by sending them to the player via the ActionContext.
 *
 * <p>This step always returns CONTINUE - it is a post-execution step that
 * should not affect pipeline flow.
 */
public final class FeedbackStep implements PipelineStep {

    @Override
    public StepResult process(ExecutionContext ctx) {
        ActionResult result = ctx.result();
        if (result == null) {
            return StepResult.continueStep();
        }

        ActionSpec spec = ctx.resolvedSpec();

        // Send failure feedback for blocked/failed results
        if (result.isBlocked() || result.isFailed()) {
            String message = result.message();
            if (message != null && !message.isEmpty()) {
                ctx.actionContext().sendFailure(Component.literal(message));
            }
            return StepResult.continueStep();
        }

        // Send success feedback if action has a label and result is OK
        if (result.isSuccess() && spec != null) {
            String successMsg = result.message();
            if (successMsg != null && !successMsg.isEmpty()) {
                ctx.actionContext().sendSuccess(Component.literal(successMsg), true);
            }
        }

        return StepResult.continueStep();
    }

    @Override
    public String stepName() {
        return "Feedback";
    }
}
