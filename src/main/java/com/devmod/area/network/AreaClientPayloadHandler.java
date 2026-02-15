package com.devmod.area.network;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import javax.annotation.Nullable;

import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.devmod.DevMod;

/**
 * Handles client-side area network payloads via cached MethodHandles.
 * Uses reflection to invoke AreaClientHooks to avoid class loading on dedicated servers.
 * Extracted from AreaNetworkHandler for better modularity.
 */
final class AreaClientPayloadHandler {

    private static final String CLIENT_HOOKS_CLASS = "com.devmod.client.area.AreaClientHooks";

    @Nullable private static volatile MethodHandle openBuilderScreenMethod;
    @Nullable private static volatile MethodHandle openEditorCentralScreenMethod;
    @Nullable private static volatile MethodHandle showAreaPreviewMethod;
    @Nullable private static volatile MethodHandle handleZoneListMethod;
    @Nullable private static volatile MethodHandle handleSaveAreaResultMethod;
    @Nullable private static volatile MethodHandle handleBuildStatusMethod;

    private AreaClientPayloadHandler() {}

    // ========================================================================
    // Client Handlers (called on client thread)
    // Uses cached MethodHandles for ~10x faster invocation than reflection
    // ========================================================================

    static void handleOpenBuilderClient(OpenAreaBuilderPayload payload, IPayloadContext ctx) {
        AreaNetworkHandler.enqueueWork(ctx, () -> {
            MethodHandle method = getOpenBuilderScreenMethod();
            if (method == null) return;
            try {
                method.invokeExact(payload);
            } catch (Throwable e) {
                DevMod.LOGGER.error("Failed to open Area Builder screen", e);
            }
        });
    }

    static void handleOpenEditorCentralClient(OpenEditorCentralPayload payload, IPayloadContext ctx) {
        AreaNetworkHandler.enqueueWork(ctx, () -> {
            MethodHandle method = getOpenEditorCentralScreenMethod();
            if (method == null) return;
            try {
                method.invokeExact(payload);
            } catch (Throwable e) {
                DevMod.LOGGER.error("Failed to open Editor Central screen", e);
            }
        });
    }

    static void handleAreaPreviewClient(AreaPreviewPayload payload, IPayloadContext ctx) {
        AreaNetworkHandler.enqueueWork(ctx, () -> {
            MethodHandle method = getShowAreaPreviewMethod();
            // H-03 fix: Log warning instead of silently failing
            if (method == null) {
                DevMod.LOGGER.warn("[Area] Cannot show preview: client hook not found (ensure AreaClientHooks is initialized)");
                return;
            }
            try {
                method.invokeExact(payload);
            } catch (Throwable e) {
                DevMod.LOGGER.error("Failed to show area preview", e);
            }
        });
    }

    static void handleZoneListClient(ZoneListPayload payload, IPayloadContext ctx) {
        AreaNetworkHandler.enqueueWork(ctx, () -> {
            MethodHandle method = getHandleZoneListMethod();
            // H-03 fix: Log warning instead of silently failing
            if (method == null) {
                DevMod.LOGGER.warn("[Area] Cannot handle zone list: client hook not found");
                return;
            }
            try {
                method.invokeExact(payload);
            } catch (Throwable e) {
                DevMod.LOGGER.error("Failed to handle zone list", e);
            }
        });
    }

    static void handleSaveAreaResultClient(SaveAreaResultPayload payload, IPayloadContext ctx) {
        AreaNetworkHandler.enqueueWork(ctx, () -> {
            MethodHandle method = getHandleSaveAreaResultMethod();
            // H-03 fix: Log warning instead of silently failing
            if (method == null) {
                DevMod.LOGGER.warn("[Area] Cannot handle save result: client hook not found");
                return;
            }
            try {
                method.invokeExact(payload);
            } catch (Throwable e) {
                DevMod.LOGGER.error("Failed to handle save area result", e);
            }
        });
    }

    static void handleBuildStatusClient(BuildStatusPayload payload, IPayloadContext ctx) {
        AreaNetworkHandler.enqueueWork(ctx, () -> {
            // CRIT-07 fix: Invoke client hook via MethodHandle like other handlers
            MethodHandle method = getHandleBuildStatusMethod();
            if (method != null) {
                try {
                    method.invoke(payload);
                } catch (Throwable e) {
                    DevMod.LOGGER.error("[Area] Failed to invoke handleBuildStatus: {}", e.getMessage());
                }
            } else {
                // Fallback logging for dedicated server
                DevMod.LOGGER.debug("[Area] Build status update: area={}, status={}, progress={}%",
                    payload.areaId(), payload.status().getSerializedName(), payload.progressPercent());
            }
        });
    }

    // ========================================================================
    // Cached MethodHandle accessors (double-checked locking)
    // ========================================================================

    @Nullable
    private static MethodHandle getOpenBuilderScreenMethod() {
        if (openBuilderScreenMethod == null) {
            synchronized (AreaClientPayloadHandler.class) {
                if (openBuilderScreenMethod == null) {
                    openBuilderScreenMethod = lookupMethod("openBuilderScreen", OpenAreaBuilderPayload.class);
                }
            }
        }
        return openBuilderScreenMethod;
    }

    @Nullable
    private static MethodHandle getOpenEditorCentralScreenMethod() {
        if (openEditorCentralScreenMethod == null) {
            synchronized (AreaClientPayloadHandler.class) {
                if (openEditorCentralScreenMethod == null) {
                    openEditorCentralScreenMethod = lookupMethod("openEditorCentralScreen", OpenEditorCentralPayload.class);
                }
            }
        }
        return openEditorCentralScreenMethod;
    }

    @Nullable
    private static MethodHandle getShowAreaPreviewMethod() {
        if (showAreaPreviewMethod == null) {
            synchronized (AreaClientPayloadHandler.class) {
                if (showAreaPreviewMethod == null) {
                    showAreaPreviewMethod = lookupMethod("showAreaPreview", AreaPreviewPayload.class);
                }
            }
        }
        return showAreaPreviewMethod;
    }

    @Nullable
    private static MethodHandle getHandleZoneListMethod() {
        if (handleZoneListMethod == null) {
            synchronized (AreaClientPayloadHandler.class) {
                if (handleZoneListMethod == null) {
                    handleZoneListMethod = lookupMethod("handleZoneList", ZoneListPayload.class);
                }
            }
        }
        return handleZoneListMethod;
    }

    @Nullable
    private static MethodHandle getHandleSaveAreaResultMethod() {
        if (handleSaveAreaResultMethod == null) {
            synchronized (AreaClientPayloadHandler.class) {
                if (handleSaveAreaResultMethod == null) {
                    handleSaveAreaResultMethod = lookupMethod("handleSaveAreaResult", SaveAreaResultPayload.class);
                }
            }
        }
        return handleSaveAreaResultMethod;
    }

    @Nullable
    private static MethodHandle getHandleBuildStatusMethod() {
        if (handleBuildStatusMethod == null) {
            synchronized (AreaClientPayloadHandler.class) {
                if (handleBuildStatusMethod == null) {
                    handleBuildStatusMethod = lookupMethod("handleBuildStatus", BuildStatusPayload.class);
                }
            }
        }
        return handleBuildStatusMethod;
    }

    @Nullable
    private static MethodHandle lookupMethod(String methodName, Class<?> paramType) {
        try {
            Class<?> hooksClass = Class.forName(CLIENT_HOOKS_CLASS);
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            return lookup.findStatic(hooksClass, methodName, MethodType.methodType(void.class, paramType));
        } catch (ClassNotFoundException e) {
            // Expected on dedicated server - client classes not available
            DevMod.LOGGER.debug("[Area] Client hooks class not available (dedicated server)");
            return null;
        } catch (NoSuchMethodException | IllegalAccessException e) {
            DevMod.LOGGER.error("[Area] Failed to lookup client method: {}", methodName, e);
            return null;
        }
    }
}
