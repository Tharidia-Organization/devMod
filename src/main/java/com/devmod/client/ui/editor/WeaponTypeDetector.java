package com.devmod.client.ui.editor;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import com.devmod.DevMod;
import com.devmod.config.EditorClientConfig;
import com.devmod.tags.ModTags;
import com.devmod.util.ConfigPaths;
public class WeaponTypeDetector {

    public record DetectionResult(
        WeaponType type,
        DetectionMethod method,
        float confidence,
        @Nullable String warning
    ) {
        public boolean isHighConfidence() {
            return confidence >= 0.8f;
        }
    }

    public enum WeaponType {
        SWORD,
        AXE,
        PICKAXE_COMBAT,
        MACE,
        TRIDENT,
        GENERIC_MELEE,
        BOW,
        CROSSBOW,
        GENERIC_RANGED,
        SHIELD,
        UNKNOWN,
        NOT_A_WEAPON
    }

    public enum DetectionMethod {
        CLASS_INSTANCEOF,
        ITEM_TAG,
        ATTRIBUTE_HEURISTIC,
        CONFIG_WHITELIST,
        CONFIG_BLACKLIST,
        FALLBACK_GENERIC
    }

    private static final String[] SWORD_KEYWORDS = {"sword", "blade", "katana", "saber"};
    private static final String[] AXE_KEYWORDS = {"axe", "hatchet", "cleaver"};
    private static final String[] BOW_KEYWORDS = {"bow", "longbow", "shortbow"};
    private static final String[] CROSSBOW_KEYWORDS = {"crossbow", "arbalest"};
    private static final String[] TRIDENT_KEYWORDS = {"trident", "harpoon"};

    /**
     * Detailed detection with confidence and method.
     */
    public static DetectionResult detectDetailed(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return logResult(stack, new DetectionResult(WeaponType.NOT_A_WEAPON, DetectionMethod.FALLBACK_GENERIC, 1.0f, null));
        }

        Item item = stack.getItem();
        boolean heuristicsEnabled = EditorClientConfig.EDITOR_WEAPON_HEURISTIC_ENABLED.get();
        boolean allowPickaxe = EditorClientConfig.EDITOR_WEAPON_TREAT_PICKAXE.get();

        // Config blacklist/whitelist stubs (extendable)
        if (ConfigWeaponLists.isBlacklisted(item)) {
            return logResult(stack, new DetectionResult(WeaponType.NOT_A_WEAPON, DetectionMethod.CONFIG_BLACKLIST, 1.0f, null));
        }
        WeaponType whitelist = ConfigWeaponLists.getWhitelist(item);
        if (whitelist != null) {
            return logResult(stack, new DetectionResult(whitelist, DetectionMethod.CONFIG_WHITELIST, 1.0f, null));
        }

        // Priority 1: class
        DetectionResult classResult = detectByClass(item, allowPickaxe);
        if (classResult != null) return logResult(stack, classResult);

        // Priority 2: tags
        DetectionResult tagResult = detectByTags(stack);
        if (tagResult != null) return logResult(stack, tagResult);

        // Priority 3: attributes + name heuristic
        if (heuristicsEnabled) {
            DetectionResult attrResult = detectByAttributes(stack);
            if (attrResult != null) return logResult(stack, attrResult);

            // Fallback generic melee if attack damage present
            if (hasAttackDamage(stack)) {
                return logResult(stack, new DetectionResult(WeaponType.GENERIC_MELEE, DetectionMethod.ATTRIBUTE_HEURISTIC, 0.5f,
                    "Detected via attack damage attribute"));
            }
        }
        Supplier<DetectionResult> fallback = () -> new DetectionResult(WeaponType.NOT_A_WEAPON, DetectionMethod.FALLBACK_GENERIC, 1.0f, null);
        return logResult(stack, fallback.get());
    }

    /**
     * Backward-compatible detection returning only the type.
     */
    public static WeaponType detect(ItemStack stack) {
        return detectDetailed(stack).type();
    }

    public static boolean isRanged(WeaponType type) {
        return type == WeaponType.BOW || type == WeaponType.CROSSBOW || type == WeaponType.GENERIC_RANGED;
    }

    public static boolean isMelee(WeaponType type) {
        return switch (type) {
            case SWORD, AXE, PICKAXE_COMBAT, MACE, TRIDENT, GENERIC_MELEE -> true;
            default -> false;
        };
    }

    public static boolean isShield(WeaponType type) {
        return type == WeaponType.SHIELD;
    }

    // ===== Detection helpers =====

    private static DetectionResult detectByClass(Item item, boolean allowPickaxe) {
        if (item instanceof SwordItem) return result(WeaponType.SWORD, DetectionMethod.CLASS_INSTANCEOF, 1.0f);
        if (item instanceof AxeItem) return result(WeaponType.AXE, DetectionMethod.CLASS_INSTANCEOF, 1.0f);
        if (item instanceof PickaxeItem) {
            if (!allowPickaxe) {
                return new DetectionResult(WeaponType.NOT_A_WEAPON, DetectionMethod.CONFIG_BLACKLIST, 1.0f, "Pickaxe detection disabled");
            }
            return result(WeaponType.PICKAXE_COMBAT, DetectionMethod.CLASS_INSTANCEOF, 0.6f);
        }
        if (item instanceof BowItem) return result(WeaponType.BOW, DetectionMethod.CLASS_INSTANCEOF, 1.0f);
        if (item instanceof CrossbowItem) return result(WeaponType.CROSSBOW, DetectionMethod.CLASS_INSTANCEOF, 1.0f);
        if (item instanceof TridentItem) return result(WeaponType.TRIDENT, DetectionMethod.CLASS_INSTANCEOF, 1.0f);
        if (item instanceof ShieldItem) return result(WeaponType.SHIELD, DetectionMethod.CLASS_INSTANCEOF, 1.0f);
        if (isInstance(item, "net.minecraft.world.item.MaceItem")) {
            return result(WeaponType.MACE, DetectionMethod.CLASS_INSTANCEOF, 0.9f);
        }
        return null;
    }

    private static DetectionResult detectByTags(ItemStack stack) {
        if (inTag(stack, ModTags.Items.NOT_EDITABLE)) {
            return new DetectionResult(WeaponType.NOT_A_WEAPON, DetectionMethod.CONFIG_BLACKLIST, 1.0f, "Tag: not_editable");
        }
        if (inTag(stack, ModTags.Items.EDITABLE_MELEE_WEAPONS) || inTag(stack, ModTags.Items.MELEE_WEAPONS)) {
            return result(WeaponType.GENERIC_MELEE, DetectionMethod.ITEM_TAG, 0.8f);
        }
        if (inTag(stack, ModTags.Items.EDITABLE_RANGED_WEAPONS) || inTag(stack, ModTags.Items.RANGED_WEAPONS)) {
            return result(WeaponType.GENERIC_RANGED, DetectionMethod.ITEM_TAG, 0.8f);
        }
        if (inTag(stack, ModTags.Items.EDITABLE_SHIELDS)) {
            return result(WeaponType.SHIELD, DetectionMethod.ITEM_TAG, 0.8f);
        }
        return null;
    }

    private static DetectionResult detectByAttributes(ItemStack stack) {
        try {
            ItemAttributeModifiers attributes = stack.getAttributeModifiers();
            if (attributes == null) return null;
            boolean hasDamage = attributes.modifiers().stream().anyMatch(entry -> isAttackDamage(entry.attribute()));
            if (hasDamage) {
                String name = stack.getDescriptionId().toLowerCase(Locale.ROOT);
                if (containsAny(name, SWORD_KEYWORDS)) return result(WeaponType.SWORD, DetectionMethod.ATTRIBUTE_HEURISTIC, 0.55f);
                if (containsAny(name, AXE_KEYWORDS)) return result(WeaponType.AXE, DetectionMethod.ATTRIBUTE_HEURISTIC, 0.55f);
                if (containsAny(name, TRIDENT_KEYWORDS)) return result(WeaponType.TRIDENT, DetectionMethod.ATTRIBUTE_HEURISTIC, 0.5f);
                return result(WeaponType.GENERIC_MELEE, DetectionMethod.ATTRIBUTE_HEURISTIC, 0.5f);
            }
            boolean hasRangedKeywords = containsAny(stack.getDescriptionId().toLowerCase(Locale.ROOT), BOW_KEYWORDS) ||
                containsAny(stack.getDescriptionId().toLowerCase(Locale.ROOT), CROSSBOW_KEYWORDS);
            if (hasRangedKeywords) {
                return result(WeaponType.GENERIC_RANGED, DetectionMethod.ATTRIBUTE_HEURISTIC, 0.4f);
            }
        } catch (Exception ignored) {
            // best-effort
        }
        return null;
    }

    private static boolean hasAttackDamage(ItemStack item) {
        try {
            ItemAttributeModifiers attributes = item.getAttributeModifiers();
            if (attributes == null) return false;
            return attributes.modifiers().stream()
                .anyMatch(entry -> isAttackDamage(entry.attribute()));
        } catch (Throwable t) {
            try {
                var m = item.getClass().getMethod("getAttributeModifiers", net.minecraft.world.entity.EquipmentSlot.class);
                Object mods = m.invoke(item, EquipmentSlot.MAINHAND);
                if (mods instanceof net.minecraft.world.item.component.ItemAttributeModifiers am) {
                    return am.modifiers().stream()
                        .anyMatch(entry -> isAttackDamage(entry.attribute()));
                }
            } catch (Exception ignored) {
                // ignore
            }
        }
        return false;
    }

    private static boolean isAttackDamage(Holder<Attribute> attributeHolder) {
        if (attributeHolder == null) return false;
        Attribute attribute = attributeHolder.value();
        return attribute == Attributes.ATTACK_DAMAGE.value();
    }

    private static boolean isInstance(Item item, String className) {
        try {
            Class<?> cls = Class.forName(className);
            return cls.isInstance(item);
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private static boolean inTag(ItemStack stack, TagKey<Item> tag) {
        try {
            return tag != null && stack.is(tag);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean containsAny(String value, String[] keywords) {
        if (value == null) return false;
        String lower = value.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (lower.contains(keyword)) return true;
        }
        return false;
    }

    private static DetectionResult result(WeaponType type, DetectionMethod method, float confidence) {
        return new DetectionResult(type, method, confidence, null);
    }

    private static DetectionResult logResult(ItemStack stack, DetectionResult result) {
        if (!EditorClientConfig.EDITOR_WEAPON_LOG_DETECTION.get()) {
            return result;
        }
        ResourceLocation id = Optional.ofNullable(stack)
            .map(ItemStack::getItem)
            .map(it -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(it)))
            .orElse(null);
        String name = id == null ? "<unknown>" : id.toString();
        DevMod.LOGGER.info("[WeaponTypeDetector] {} -> type={} method={} conf={} warn={}",
            name, result.type(), result.method(), String.format(Locale.US, "%.2f", result.confidence()),
            result.warning());
        return result;
    }

    /**
     * Simple in-memory config-backed lists (extendable).
     */
    private static final class ConfigWeaponLists {
        private static Set<ResourceLocation> MELEE_WHITELIST = Set.of();
        private static Set<ResourceLocation> RANGED_WHITELIST = Set.of();
        private static Set<ResourceLocation> MACE_WHITELIST = Set.of();
        private static Set<ResourceLocation> TRIDENT_WHITELIST = Set.of();
        private static Set<ResourceLocation> BLACKLIST = Set.of();

        static {
            reload();
        }

        @Nullable
        static WeaponType getWhitelist(Item item) {
            ResourceLocation id = getId(item);
            if (id == null) return null;
            if (MELEE_WHITELIST.contains(id)) return WeaponType.GENERIC_MELEE;
            if (RANGED_WHITELIST.contains(id)) return WeaponType.GENERIC_RANGED;
            if (MACE_WHITELIST.contains(id)) return WeaponType.MACE;
            if (TRIDENT_WHITELIST.contains(id)) return WeaponType.TRIDENT;
            return null;
        }

        static boolean isBlacklisted(Item item) {
            ResourceLocation id = getId(item);
            return id != null && BLACKLIST.contains(id);
        }

        static void reload() {
            try {
                Lists lists = Lists.load();
                MELEE_WHITELIST = lists.melee();
                RANGED_WHITELIST = lists.ranged();
                MACE_WHITELIST = lists.mace();
                TRIDENT_WHITELIST = lists.trident();
                BLACKLIST = lists.blacklist();
                DevMod.LOGGER.info("[WeaponTypeDetector] Reloaded weapon whitelist/blacklist ({} melee, {} ranged, {} mace, {} trident, {} blacklist)",
                    MELEE_WHITELIST.size(), RANGED_WHITELIST.size(), MACE_WHITELIST.size(), TRIDENT_WHITELIST.size(), BLACKLIST.size());
            } catch (Exception e) {
                DevMod.LOGGER.warn("[WeaponTypeDetector] Failed to reload weapon lists: {}", e.getMessage());
            }
        }

        static boolean addToWhitelist(Item item, WeaponType type) {
            ResourceLocation id = getId(item);
            if (id == null) return false;
            String field = whitelistFieldForType(type);
            if (field == null) return false;
            java.nio.file.Path path = ConfigPaths.getWeaponWhitelistFile();
            try {
                if (path.getParent() != null) {
                    java.nio.file.Files.createDirectories(path.getParent());
                }
                JsonObject root = java.nio.file.Files.exists(path)
                    ? com.google.gson.JsonParser.parseReader(java.nio.file.Files.newBufferedReader(path)).getAsJsonObject()
                    : new JsonObject();
                JsonArray array = root.has(field) && root.get(field).isJsonArray()
                    ? root.getAsJsonArray(field)
                    : new JsonArray();
                boolean alreadyPresent = false;
                for (var el : array) {
                    try {
                        if (el.isJsonPrimitive() && id.toString().equals(el.getAsString())) {
                            alreadyPresent = true;
                            break;
                        }
                    } catch (Exception ignored) { }
                }
                if (!alreadyPresent) {
                    array.add(id.toString());
                }
                root.add(field, array);
                if (!root.has("_comment")) {
                    root.addProperty("_comment", "Items explicitly whitelisted for the DevMod weapon editor");
                }
                try (var writer = java.nio.file.Files.newBufferedWriter(path)) {
                    new GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
                }
                reload();
                return true;
            } catch (Exception e) {
                DevMod.LOGGER.warn("[WeaponTypeDetector] Failed to add {} to whitelist: {}", id, e.getMessage());
                return false;
            }
        }

        @Nullable
            private static ResourceLocation getId(Item item) {
                try {
                    return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(item));
                } catch (Exception e) {
                    return null;
                }
            }

        private record Lists(Set<ResourceLocation> melee, Set<ResourceLocation> ranged, Set<ResourceLocation> mace,
                              Set<ResourceLocation> trident, Set<ResourceLocation> blacklist) {
            static Lists load() {
                return new Lists(
                    WeaponListLoader.loadArray(ConfigPaths.getWeaponWhitelistFile(), "melee"),
                    WeaponListLoader.loadArray(ConfigPaths.getWeaponWhitelistFile(), "ranged"),
                    WeaponListLoader.loadArray(ConfigPaths.getWeaponWhitelistFile(), "mace"),
                    WeaponListLoader.loadArray(ConfigPaths.getWeaponWhitelistFile(), "trident"),
                    WeaponListLoader.loadArray(ConfigPaths.getWeaponBlacklistFile(), "items")
                );
            }
        }

        @Nullable
        private static String whitelistFieldForType(WeaponType type) {
            return switch (type) {
                case BOW, CROSSBOW, GENERIC_RANGED -> "ranged";
                case MACE -> "mace";
                case TRIDENT -> "trident";
                case SWORD, AXE, PICKAXE_COMBAT, GENERIC_MELEE -> "melee";
                default -> null;
            };
        }
    }

    private static final class WeaponListLoader {
        static Set<ResourceLocation> loadArray(java.nio.file.Path path, String field) {
            try {
                if (path == null || !java.nio.file.Files.exists(path)) {
                    return Set.of();
                }
                var root = com.google.gson.JsonParser.parseReader(java.nio.file.Files.newBufferedReader(path)).getAsJsonObject();
                if (!root.has(field)) return Set.of();
                var arr = root.getAsJsonArray(field);
                java.util.Set<ResourceLocation> out = new java.util.HashSet<>();
                arr.forEach(el -> {
                    try {
                        ResourceLocation id = ResourceLocation.tryParse(Objects.requireNonNull(el.getAsString()));
                        if (id != null) out.add(id);
                    } catch (Exception ignored) {}
                });
                return out;
            } catch (Exception e) {
                DevMod.LOGGER.warn("[WeaponTypeDetector] Failed to parse {}: {}", path, e.getMessage());
                return Set.of();
            }
        }
    }

    /**
     * Reload weapon whitelist/blacklist files from disk.
     */
    public static void reloadWeaponLists() {
        ConfigWeaponLists.reload();
    }

    /**
     * Add an item to the weapon whitelist file (categorized by detected type).
     */
    public static boolean addToWhitelist(Item item, WeaponType type) {
        return ConfigWeaponLists.addToWhitelist(item, type);
    }
}
