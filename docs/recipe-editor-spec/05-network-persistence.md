# Network & Persistence - Recipe Editor

> Sincronizzazione multiplayer e persistenza dati

## Overview

```
Persistence Layers
├── Layer 1: Runtime (ConcurrentHashMap in RecipeConfigManager)
├── Layer 2: Server Config (serverconfig/devmod/recipes/index.json)
└── Layer 3: Datapack Export (datapacks/<pack>/data/devmod/recipe/*.json)

Network Flow
├── Client → Server: RecipeSyncPayload (edit request)
├── Server validates + stores
└── Server → All Clients: RecipeSyncPayload (broadcast)
```

---

## RecipeSyncPayload

Payload per sincronizzazione ricette tra client e server.

```java
public record RecipeSyncPayload(
    List<RecipeData> recipes,
    boolean isGlobal,
    SyncOperation operation
) implements CustomPacketPayload {

    // === Identifiers ===

    public static final ResourceLocation ID =
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "recipe_sync");

    public static final CustomPacketPayload.Type<RecipeSyncPayload> TYPE =
        new CustomPacketPayload.Type<>(ID);

    // === Codecs ===

    public static final StreamCodec<RegistryFriendlyByteBuf, RecipeSyncPayload> STREAM_CODEC =
        StreamCodec.composite(
            RecipeDataCodec.LIST_CODEC, RecipeSyncPayload::recipes,
            ByteBufCodecs.BOOL, RecipeSyncPayload::isGlobal,
            SyncOperation.STREAM_CODEC, RecipeSyncPayload::operation,
            RecipeSyncPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // === Server-side Handler ===

    public void handleOnServer(ServerPlayer player, IPayloadContext context) {
        context.enqueueWork(() -> {
            // Permission check
            if (!hasPermission(player)) {
                LOGGER.warn("[RecipeSync] Player {} tried to modify recipes without permission",
                    player.getName().getString());
                sendError(player, "No permission to modify recipes");
                return;
            }

            // Process each recipe
            List<RecipeData> processedRecipes = new ArrayList<>();

            for (RecipeData recipe : recipes) {
                // Validate
                RecipeValidator.ValidationResult result = RecipeValidator.validate(recipe);

                if (!result.valid()) {
                    LOGGER.warn("[RecipeSync] Invalid recipe from {}: {} - {}",
                        player.getName().getString(),
                        recipe.id(),
                        result.errors());
                    continue;
                }

                // Apply operation
                switch (operation) {
                    case ADD, UPDATE -> {
                        RecipeConfigManager.addRecipe(recipe);
                        processedRecipes.add(recipe);
                    }
                    case DELETE -> {
                        RecipeConfigManager.removeRecipe(recipe.id());
                        processedRecipes.add(recipe);
                    }
                }
            }

            // Broadcast to all players
            if (!processedRecipes.isEmpty()) {
                RecipeSyncPayload broadcast = new RecipeSyncPayload(
                    processedRecipes, isGlobal, operation
                );
                broadcastToAll(player.server, broadcast);
            }

            LOGGER.info("[RecipeSync] {} processed {} recipes from {}",
                operation, processedRecipes.size(), player.getName().getString());
        });
    }

    // === Client-side Handler ===

    public void handleOnClient(IPayloadContext context) {
        context.enqueueWork(() -> {
            for (RecipeData recipe : recipes) {
                switch (operation) {
                    case ADD, UPDATE -> RecipeConfigManager.addRecipeClientOnly(recipe);
                    case DELETE -> RecipeConfigManager.removeRecipeClientOnly(recipe.id());
                }
            }

            LOGGER.debug("[RecipeSync] Applied {} {} operations on client",
                recipes.size(), operation);
        });
    }

    // === Helper Methods ===

    private static boolean hasPermission(ServerPlayer player) {
        // OP level 2 or higher, or creative mode
        return player.hasPermissions(2) ||
               player.isCreative() ||
               player.getAbilities().instabuild;
    }

    private static void sendError(ServerPlayer player, String message) {
        player.sendSystemMessage(
            Component.literal("[DevMod] " + message)
                .withStyle(ChatFormatting.RED)
        );
    }

    private static void broadcastToAll(MinecraftServer server, RecipeSyncPayload payload) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    // === Operations ===

    public enum SyncOperation {
        ADD,
        UPDATE,
        DELETE;

        public static final StreamCodec<ByteBuf, SyncOperation> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(
                i -> values()[i],
                SyncOperation::ordinal
            );
    }
}
```

---

## RecipeDataCodec

Codec per serializzazione ricette su network.

```java
public class RecipeDataCodec {

    // === Single Recipe Codec ===

    public static final StreamCodec<RegistryFriendlyByteBuf, RecipeData> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public RecipeData decode(RegistryFriendlyByteBuf buf) {
                // Read type discriminator
                String type = buf.readUtf();

                return switch (type) {
                    case "crafting" -> decodeCrafting(buf);
                    case "smelting" -> decodeSmelting(buf);
                    case "smithing" -> decodeSmithing(buf);
                    case "stonecutting" -> decodeStonecutting(buf);
                    default -> throw new IllegalArgumentException("Unknown recipe type: " + type);
                };
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, RecipeData recipe) {
                // Write type discriminator
                switch (recipe) {
                    case CraftingRecipeData c -> {
                        buf.writeUtf("crafting");
                        encodeCrafting(buf, c);
                    }
                    case SmeltingRecipeData s -> {
                        buf.writeUtf("smelting");
                        encodeSmelting(buf, s);
                    }
                    case SmithingRecipeData sm -> {
                        buf.writeUtf("smithing");
                        encodeSmithing(buf, sm);
                    }
                    case StonecuttingRecipeData sc -> {
                        buf.writeUtf("stonecutting");
                        encodeStonecutting(buf, sc);
                    }
                }
            }
        };

    // === List Codec ===

    public static final StreamCodec<RegistryFriendlyByteBuf, List<RecipeData>> LIST_CODEC =
        STREAM_CODEC.apply(ByteBufCodecs.list());

    // === Crafting Encoding ===

    private static void encodeCrafting(RegistryFriendlyByteBuf buf, CraftingRecipeData recipe) {
        buf.writeResourceLocation(recipe.id());
        buf.writeEnum(recipe.craftingType());
        buf.writeEnum(recipe.category());
        buf.writeNullable(recipe.group(), FriendlyByteBuf::writeUtf);

        // Ingredients
        buf.writeCollection(recipe.ingredients(), (b, ing) -> encodeIngredient(b, ing));

        // Pattern (nullable for shapeless)
        if (recipe.pattern() != null) {
            buf.writeBoolean(true);
            buf.writeVarInt(recipe.pattern().length);
            for (String row : recipe.pattern()) {
                buf.writeUtf(row);
            }
        } else {
            buf.writeBoolean(false);
        }

        // Result
        encodeResult(buf, recipe.result());

        buf.writeBoolean(recipe.showNotification());
        buf.writeBoolean(recipe.isModified());
        buf.writeNullable(recipe.originalId(), FriendlyByteBuf::writeResourceLocation);
    }

    private static CraftingRecipeData decodeCrafting(RegistryFriendlyByteBuf buf) {
        ResourceLocation id = buf.readResourceLocation();
        CraftingType type = buf.readEnum(CraftingType.class);
        RecipeCategory category = buf.readEnum(RecipeCategory.class);
        String group = buf.readNullable(FriendlyByteBuf::readUtf);

        List<IngredientData> ingredients = buf.readList(b -> decodeIngredient(b));

        String[] pattern = null;
        if (buf.readBoolean()) {
            int len = buf.readVarInt();
            pattern = new String[len];
            for (int i = 0; i < len; i++) {
                pattern[i] = buf.readUtf();
            }
        }

        ResultData result = decodeResult(buf);
        boolean showNotification = buf.readBoolean();
        boolean isModified = buf.readBoolean();
        ResourceLocation originalId = buf.readNullable(FriendlyByteBuf::readResourceLocation);

        return new CraftingRecipeData(
            id, type, category, group,
            ingredients, pattern, null, // keyToSlot rebuilt on demand
            result, showNotification, isModified, originalId
        );
    }

    // === Ingredient Encoding ===

    private static void encodeIngredient(RegistryFriendlyByteBuf buf, IngredientData ing) {
        // Type discriminator: 0=empty, 1=item, 2=tag, 3=alternatives
        if (ing.isEmpty()) {
            buf.writeByte(0);
        } else if (ing.item() != null) {
            buf.writeByte(1);
            buf.writeResourceLocation(ing.item());
        } else if (ing.tag() != null) {
            buf.writeByte(2);
            buf.writeResourceLocation(ing.tag().location());
        } else if (ing.alternatives() != null) {
            buf.writeByte(3);
            buf.writeCollection(ing.alternatives(), FriendlyByteBuf::writeResourceLocation);
        }
    }

    private static IngredientData decodeIngredient(RegistryFriendlyByteBuf buf) {
        byte type = buf.readByte();
        return switch (type) {
            case 0 -> IngredientData.empty();
            case 1 -> IngredientData.ofItem(buf.readResourceLocation());
            case 2 -> IngredientData.ofTag(TagKey.create(
                Registries.ITEM, buf.readResourceLocation()));
            case 3 -> IngredientData.ofAny(buf.readList(FriendlyByteBuf::readResourceLocation));
            default -> throw new IllegalArgumentException("Unknown ingredient type: " + type);
        };
    }

    // === Result Encoding ===

    private static void encodeResult(RegistryFriendlyByteBuf buf, ResultData result) {
        if (result == null || result.isEmpty()) {
            buf.writeBoolean(false);
            return;
        }
        buf.writeBoolean(true);
        buf.writeResourceLocation(result.itemId());
        buf.writeVarInt(result.count());
        buf.writeNullable(result.components(), (b, tag) -> b.writeNbt(tag));
    }

    private static ResultData decodeResult(RegistryFriendlyByteBuf buf) {
        if (!buf.readBoolean()) {
            return null;
        }
        ResourceLocation id = buf.readResourceLocation();
        int count = buf.readVarInt();
        CompoundTag components = buf.readNullable(FriendlyByteBuf::readNbt);
        return new ResultData(id, count, components);
    }

    // === Similar methods for Smelting, Smithing, Stonecutting ===
    // ... (omitted for brevity, same pattern)
}
```

---

## Network Registration

```java
// In NetworkHandler.java

public class NetworkHandler {

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(DevMod.MODID)
            .versioned("1.0")
            .optional();

        // === Recipe Sync ===

        // Client → Server (player edits recipe)
        registrar.playToServer(
            RecipeSyncPayload.TYPE,
            RecipeSyncPayload.STREAM_CODEC,
            NetworkHandler::handleRecipeSyncServer
        );

        // Server → Client (broadcast changes)
        registrar.playToClient(
            RecipeSyncPayload.TYPE,
            RecipeSyncPayload.STREAM_CODEC,
            NetworkHandler::handleRecipeSyncClient
        );
    }

    private static void handleRecipeSyncServer(
            RecipeSyncPayload payload,
            IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        payload.handleOnServer(player, context);
    }

    private static void handleRecipeSyncClient(
            RecipeSyncPayload payload,
            IPayloadContext context) {
        payload.handleOnClient(context);
    }
}
```

---

## Datapack Sync Event

Sincronizzazione ricette durante reload datapack.

```java
// In CommonModEvents.java

@SubscribeEvent
public static void onDatapackSync(OnDatapackSyncEvent event) {
    // Get all custom recipes
    List<RecipeData> customRecipes = RecipeConfigManager.getAllCustomRecipes();

    if (customRecipes.isEmpty()) {
        return;
    }

    RecipeSyncPayload payload = new RecipeSyncPayload(
        customRecipes,
        true, // isGlobal
        RecipeSyncPayload.SyncOperation.ADD
    );

    if (event.getPlayer() != null) {
        // Single player joining
        PacketDistributor.sendToPlayer(event.getPlayer(), payload);
        LOGGER.debug("[RecipeSync] Sent {} recipes to {}",
            customRecipes.size(), event.getPlayer().getName().getString());
    } else {
        // Datapack reload - broadcast to all
        for (ServerPlayer player : event.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
        LOGGER.info("[RecipeSync] Broadcast {} recipes to all players",
            customRecipes.size());
    }
}
```

---

## Persistence - RecipeConfigManager

```java
public class RecipeConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecipeConfigManager.class);
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    // === Storage ===

    // Server-side: authoritative storage
    private static final Map<ResourceLocation, RecipeData> serverRecipes =
        new ConcurrentHashMap<>();

    // Client-side: mirror of server state
    private static final Map<ResourceLocation, RecipeData> clientRecipes =
        new ConcurrentHashMap<>();

    private static Path serverConfigPath = null;
    private static boolean initialized = false;

    // === Initialization ===

    public static void initializeServer(Path serverConfigDir) {
        if (initialized) return;

        serverConfigPath = serverConfigDir.resolve("devmod").resolve("recipes");
        try {
            Files.createDirectories(serverConfigPath);
            loadServerRecipes();
            initialized = true;
            LOGGER.info("[RecipeConfig] Server initialized with {} recipes",
                serverRecipes.size());
        } catch (Exception e) {
            LOGGER.error("[RecipeConfig] Failed to initialize server storage", e);
        }
    }

    public static void initializeClient() {
        // Client just needs empty map, will be populated by server sync
        clientRecipes.clear();
        LOGGER.debug("[RecipeConfig] Client initialized");
    }

    // === Server Operations ===

    public static void addRecipe(RecipeData recipe) {
        Objects.requireNonNull(recipe);
        Objects.requireNonNull(recipe.id());

        serverRecipes.put(recipe.id(), recipe);
        saveServerRecipes();
        LOGGER.debug("[RecipeConfig] Added recipe: {}", recipe.id());
    }

    public static void removeRecipe(ResourceLocation id) {
        RecipeData removed = serverRecipes.remove(id);
        if (removed != null) {
            saveServerRecipes();
            LOGGER.debug("[RecipeConfig] Removed recipe: {}", id);
        }
    }

    public static Optional<RecipeData> getRecipe(ResourceLocation id) {
        return Optional.ofNullable(serverRecipes.get(id));
    }

    public static List<RecipeData> getAllCustomRecipes() {
        return new ArrayList<>(serverRecipes.values());
    }

    // === Client Operations ===

    public static void addRecipeClientOnly(RecipeData recipe) {
        clientRecipes.put(recipe.id(), recipe);
    }

    public static void removeRecipeClientOnly(ResourceLocation id) {
        clientRecipes.remove(id);
    }

    public static List<RecipeData> getClientRecipes() {
        return new ArrayList<>(clientRecipes.values());
    }

    // === Persistence ===

    private static void loadServerRecipes() {
        if (serverConfigPath == null) return;

        Path indexFile = serverConfigPath.resolve("index.json");
        if (!Files.exists(indexFile)) {
            LOGGER.info("[RecipeConfig] No index file found, starting fresh");
            return;
        }

        try {
            String content = Files.readString(indexFile, StandardCharsets.UTF_8);
            JsonObject index = GSON.fromJson(content, JsonObject.class);

            JsonArray recipesArray = index.getAsJsonArray("recipes");
            if (recipesArray == null) return;

            int loaded = 0;
            int failed = 0;

            for (JsonElement elem : recipesArray) {
                try {
                    JsonObject recipeJson = elem.getAsJsonObject();
                    String idStr = GsonHelper.getAsString(recipeJson, "id");
                    ResourceLocation id = ResourceLocation.parse(idStr);

                    RecipeData recipe = RecipeData.fromJson(id, recipeJson);
                    serverRecipes.put(id, recipe);
                    loaded++;
                } catch (Exception e) {
                    LOGGER.error("[RecipeConfig] Failed to load recipe entry", e);
                    failed++;
                }
            }

            LOGGER.info("[RecipeConfig] Loaded {} recipes ({} failed)",
                loaded, failed);
        } catch (Exception e) {
            LOGGER.error("[RecipeConfig] Failed to load index file", e);
        }
    }

    private static void saveServerRecipes() {
        if (serverConfigPath == null) return;

        try {
            JsonObject index = new JsonObject();
            index.addProperty("version", 1);
            index.addProperty("savedAt", System.currentTimeMillis());

            JsonArray recipesArray = new JsonArray();
            for (RecipeData recipe : serverRecipes.values()) {
                JsonObject recipeJson = recipe.toJson();
                recipeJson.addProperty("id", recipe.id().toString());
                recipesArray.add(recipeJson);
            }
            index.add("recipes", recipesArray);

            Path indexFile = serverConfigPath.resolve("index.json");
            Files.writeString(indexFile, GSON.toJson(index), StandardCharsets.UTF_8);

            LOGGER.debug("[RecipeConfig] Saved {} recipes", serverRecipes.size());
        } catch (Exception e) {
            LOGGER.error("[RecipeConfig] Failed to save recipes", e);
        }
    }

    // === Datapack Export ===

    public static int exportToDatapack(String packName) {
        Path gameDir = ConfigPaths.getGameDir();
        if (gameDir == null) {
            LOGGER.error("[RecipeConfig] Cannot export - game directory unknown");
            return 0;
        }

        Path packDir = gameDir.resolve("datapacks").resolve(packName);
        Path recipesDir = packDir.resolve("data").resolve("devmod").resolve("recipe");

        try {
            // Create directories
            Files.createDirectories(recipesDir);

            // Write pack.mcmeta
            writePackMeta(packDir, packName);

            // Export each recipe
            int count = 0;
            for (RecipeData recipe : serverRecipes.values()) {
                String filename = recipe.id().getPath().replace("/", "_") + ".json";
                Path recipeFile = recipesDir.resolve(filename);

                JsonObject json = recipe.toJson();
                Files.writeString(recipeFile, GSON.toJson(json), StandardCharsets.UTF_8);
                count++;
            }

            LOGGER.info("[RecipeConfig] Exported {} recipes to datapack '{}'",
                count, packName);
            return count;
        } catch (Exception e) {
            LOGGER.error("[RecipeConfig] Failed to export datapack", e);
            return 0;
        }
    }

    private static void writePackMeta(Path packDir, String packName) throws IOException {
        JsonObject meta = new JsonObject();
        JsonObject pack = new JsonObject();
        pack.addProperty("description", "DevMod Custom Recipes - " + packName);
        pack.addProperty("pack_format", 48); // 1.21.x
        meta.add("pack", pack);

        Path metaFile = packDir.resolve("pack.mcmeta");
        Files.writeString(metaFile, GSON.toJson(meta), StandardCharsets.UTF_8);
    }

    // === Recipe Priorities Export ===

    public static void exportPriorities(Path packDir, Map<ResourceLocation, Integer> priorities) {
        if (priorities.isEmpty()) return;

        try {
            Path prioritiesDir = packDir.resolve("data").resolve("devmod");
            Files.createDirectories(prioritiesDir);

            JsonObject json = new JsonObject();
            JsonObject values = new JsonObject();

            for (Map.Entry<ResourceLocation, Integer> entry : priorities.entrySet()) {
                values.addProperty(entry.getKey().toString(), entry.getValue());
            }

            json.add("values", values);

            Path file = prioritiesDir.resolve("recipe_priorities.json");
            Files.writeString(file, GSON.toJson(json), StandardCharsets.UTF_8);

            LOGGER.debug("[RecipeConfig] Exported {} recipe priorities", priorities.size());
        } catch (Exception e) {
            LOGGER.error("[RecipeConfig] Failed to export priorities", e);
        }
    }
}
```

---

## index.json Format

```json
{
  "version": 1,
  "savedAt": 1702828800000,
  "recipes": [
    {
      "id": "devmod:custom_diamond_sword_1234",
      "type": "minecraft:crafting_shaped",
      "category": "equipment",
      "pattern": [
        " # ",
        " # ",
        " | "
      ],
      "key": {
        "#": "minecraft:diamond",
        "|": "minecraft:stick"
      },
      "result": {
        "id": "minecraft:diamond_sword",
        "count": 1
      }
    },
    {
      "id": "devmod:custom_iron_ingot_5678",
      "type": "minecraft:smelting",
      "category": "misc",
      "ingredient": "minecraft:raw_iron",
      "result": {
        "id": "minecraft:iron_ingot"
      },
      "experience": 0.7,
      "cookingtime": 200
    }
  ]
}
```

---

## Error Handling

```java
public class RecipeSyncError {

    public enum ErrorType {
        NO_PERMISSION,
        INVALID_RECIPE,
        VALIDATION_FAILED,
        NETWORK_ERROR,
        STORAGE_ERROR
    }

    public record Error(
        ErrorType type,
        String message,
        @Nullable ResourceLocation recipeId
    ) {}

    // Send error to player
    public static void sendError(ServerPlayer player, Error error) {
        Component message = Component.literal("[DevMod Recipe] ")
            .withStyle(ChatFormatting.RED)
            .append(Component.literal(error.message())
                .withStyle(ChatFormatting.WHITE));

        player.sendSystemMessage(message);

        LOGGER.warn("[RecipeSync] Error for {}: {} - {}",
            player.getName().getString(),
            error.type(),
            error.message());
    }
}
```
