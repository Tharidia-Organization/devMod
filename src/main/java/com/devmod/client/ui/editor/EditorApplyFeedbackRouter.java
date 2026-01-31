package com.devmod.client.ui.editor;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.Nonnull;

import com.devmod.network.EditorApplyConfirmPayload;

/**
 * Routes editor apply confirmations to active UI screens without hard references.
 */
public final class EditorApplyFeedbackRouter {

    private static final List<WeakReference<Listener>> LISTENERS = new CopyOnWriteArrayList<>();

    private EditorApplyFeedbackRouter() {}

    public interface Listener {
        /**
         * Returns true if handled (for metrics/logging), false otherwise.
         *
         * @return true if handled (for metrics/logging), false otherwise.
         */
        boolean onConfirm(@Nonnull EditorApplyConfirmPayload payload);
    }

    public static void register(@Nonnull Listener listener) {
        cleanup();
        LISTENERS.add(new WeakReference<>(listener));
    }

    public static void unregister(@Nonnull Listener listener) {
        for (Iterator<WeakReference<Listener>> it = LISTENERS.iterator(); it.hasNext();) {
            Listener current = it.next().get();
            if (current == null || current == listener) {
                it.remove();
            }
        }
    }

    public static void notify(@Nonnull EditorApplyConfirmPayload payload) {
        cleanup();
        for (WeakReference<Listener> ref : LISTENERS) {
            Listener listener = ref.get();
            if (listener != null) {
                listener.onConfirm(payload);
            }
        }
    }

    private static void cleanup() {
        for (Iterator<WeakReference<Listener>> it = LISTENERS.iterator(); it.hasNext();) {
            if (it.next().get() == null) {
                it.remove();
            }
        }
    }
}
