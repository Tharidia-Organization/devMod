# DevMod - Next Implementation Steps

> Last updated: 2025-12-26
> Status: PLANNING

## 🎯 IMMEDIATE PRIORITIES (Next 2-3 hours)

### 1. Register Components & Payloads
```java
// In DevMod.java - add to mod constructor
ArmorComponents.COMPONENTS.register(modEventBus);

// In NetworkHandler.java - register V2 payload
CHANNEL.messageBuilder(ArmorStatsPayloadV2.class, nextId++)
    .decoder(ArmorStatsPayloadV2.STREAM_CODEC)
    .encoder(ArmorStatsPayloadV2.STREAM_CODEC)
    .consumerMainThread(ArmorStatsPayloadV2::handle)
    .add();
```

### 2. Update ArmorModule to use Components
```java
// In ArmorModule.java - replace NBT usage
@Override
public void applyPreview() {
    ItemStack copy = item.copy();
    copy.set(ArmorComponents.ARMOR_STATS.get(), stats);
    setPreviewItem(copy);
}

@Override
public CustomPacketPayload buildPayload(boolean isGlobal) {
    return new ArmorStatsPayloadV2(item, stats, isGlobal);
}
```

### 3. Add Source Badges to ArmorModule
```java
// Add badge rendering to each slider/toggle
private void renderSourceBadge(GuiGraphics g, int x, int y, SourceType source) {
    String badge = switch(source) {
        case COMPONENT -> "DEV";
        case NBT -> "NBT"; 
        case VANILLA -> "VAN";
    };
    int color = switch(source) {
        case COMPONENT -> 0xFF66FF66;
        case NBT -> 0xFFFFAA00;
        case VANILLA -> 0xFF888888;
    };
    g.drawString(font, badge, x, y, color, false);
}
```

## 🔧 INTEGRATION TASKS

### 4. Update ArmorConfigManager
```java
// Replace getStats() to use migration helper
public static ArmorStats getStats(ItemStack stack) {
    return ArmorMigrationHelper.getStatsWithMigration(stack);
}
```

### 5. Server Handler for V2 Payload
```java
// In NetworkHandler.java
public static void handleArmorStatsV2(ArmorStatsPayloadV2 payload, ServerPlayer player) {
    // Validate with PacketSecurityService
    ArmorStats clamped = PacketSecurityService.validateArmorStats(payload.stats());
    
    if (payload.isGlobal()) {
        ArmorConfigManager.setGlobalStats(payload.item().getItem(), clamped);
    } else {
        payload.item().set(ArmorComponents.ARMOR_STATS.get(), clamped);
    }
}
```

## 📋 TESTING CHECKLIST

- [ ] Component registration works
- [ ] V2 payload serialization works
- [ ] Migration from NBT preserves all data
- [ ] Source badges display correctly
- [ ] Global vs specific armor configs work
- [ ] Preview mode uses components
- [ ] Apply mode persists to component

## 🎨 UI IMPROVEMENTS

### Source Badge Legend
```java
// Add to ArmorModule panel header
private void renderBadgeLegend(GuiGraphics g, int x, int y) {
    g.drawString(font, "DEV", x, y, 0xFF66FF66, false);
    g.drawString(font, "NBT", x + 30, y, 0xFFFFAA00, false); 
    g.drawString(font, "VAN", x + 60, y, 0xFF888888, false);
}
```

## 🚀 COMPLETION ESTIMATE

- **Component Registration**: 30 min
- **ArmorModule Updates**: 1 hour  
- **Source Badges**: 45 min
- **Server Handler**: 30 min
- **Testing & Debug**: 45 min

**Total: ~3.5 hours to full armor parity**

## 📊 SUCCESS METRICS

✅ ArmorModule shows source badges like WeaponModule
✅ Component-first storage with NBT fallback
✅ V2 payload with proper validation
✅ Auto-migration preserves existing data
✅ All tests pass
✅ UI matches weapon editor quality