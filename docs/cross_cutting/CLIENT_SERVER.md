# Client/Server Safety

> **Audit Date**: 2024-12-23

---

## Dist Annotation Guide

### @OnlyIn(Dist.CLIENT)

Use for:
- Screen classes
- Rendering code
- Client-only mixins
- Input handlers
- HUD overlays

```java
@OnlyIn(Dist.CLIENT)
public class ItemEditorScreen extends Screen {
    // Client-only implementation
}
```

### @OnlyIn(Dist.DEDICATED_SERVER)

Rarely used - most server code is shared.

---

## Safe Patterns

### Runtime Dist Check

```java
public static void addVFX(Vec3 pos) {
    if (!FMLEnvironment.dist.isClient()) {
        return;  // No-op on server
    }
    // Safe to call client code
    ClientVFXHelper.addVFX(pos);
}
```

### Proxy Pattern (Recommended)

```java
public class ClientVFXProxy {
    private static Method addVFXMethod;

    public static void addVFX(Vec3 pos) {
        if (!FMLEnvironment.dist.isClient()) return;

        try {
            initClient();  // @OnlyIn(Dist.CLIENT)
            addVFXMethod.invoke(null, pos);
        } catch (Exception e) {
            LOGGER.error("VFX error", e);
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static void initClient() {
        Class<?> helper = Class.forName("...ClientVFXHelper");
        addVFXMethod = helper.getMethod("addVFX", Vec3.class);
    }
}
```

### Network Handler Pattern

```java
// CORRECT: Client logic in playToClient handler
registrar.playToClient(PAYLOAD_ID, CODEC, (payload, context) -> {
    context.enqueueWork(() -> {
        // This runs on client thread
        Minecraft.getInstance().setScreen(new MyScreen());
    });
});
```

---

## Unsafe Patterns

### Direct Minecraft Import

```java
// WRONG: Will crash on dedicated server
import net.minecraft.client.Minecraft;

public class NetworkHandler {
    public static void handle(Payload p) {
        Minecraft.getInstance().setScreen(...);  // CRASH!
    }
}
```

### Static Import of Client Class

```java
// WRONG: Class loading triggers import resolution
import com.example.client.ClientHelper;

public class SharedHandler {
    // Even if ClientHelper is never called,
    // the import causes ClassNotFoundException on server
}
```

---

## Package Boundaries

### Client-Only Packages

```
com.frenkvs.devmod.client/       - Bridge, input
com.frenkvs.devmod.ui/           - All screens
com.frenkvs.devmod.hud/          - HUD overlays
com.frenkvs.devmod.rendering/    - Debug rendering
com.frenkvs.devmod.mixin/ (client mixins)
```

### Shared Packages

```
com.frenkvs.devmod.network/      - Handlers (careful!)
com.frenkvs.devmod.actions/      - Action definitions
com.frenkvs.devmod.telemetry/    - Telemetry (server writes)
```

---

## Critical Fixes Needed

| File | Issue | Fix |
|------|-------|-----|
| `ClientConfigFeedback.java` | Missing @OnlyIn | Add annotation |
| `ConfigNetworkHandler.java` | Direct Minecraft import | Use proxy |
| All client mixins | Missing @OnlyIn | Add annotations |
| `EnduranceNetworkHandler.java` | Static client import | Lazy load |

---

## Cross-References

- [[areas/client_server/README]] - Full audit
- [[ENTRYPOINTS]] - Network handlers
- [[cross_cutting/CONCURRENCY]] - Thread safety
