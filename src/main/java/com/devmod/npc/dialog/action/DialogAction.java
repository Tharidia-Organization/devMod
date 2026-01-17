package com.devmod.npc.dialog.action;

import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.devmod.DevMod;
import com.devmod.npc.NpcEmotion;
import com.devmod.npc.dialog.NpcContext;

/**
 * Sealed interface for dialog actions that can be triggered by player choices.
 * Each action type has its own implementation with specific behavior.
 */
public sealed interface DialogAction permits
    DialogAction.GoToNode,
    DialogAction.CloseDialog,
    DialogAction.ExecuteCommand,
    DialogAction.Teleport,
    DialogAction.GiveItem,
    DialogAction.PlaySound,
    DialogAction.OpenGui,
    DialogAction.SetVariable,
    DialogAction.RememberChoice,
    DialogAction.SetEmotion,
    DialogAction.Custom
{
    /**
     * Returns the type identifier for this action.
     */
    @Nonnull
    String getType();

    /**
     * Executes this action for the given player.
     *
     * @param player  The server player executing the action
     * @param context The NPC dialog context
     */
    void execute(@Nonnull ServerPlayer player, @Nonnull NpcContext context);

    /**
     * Dispatch codec for serializing actions by type.
     */
    Codec<DialogAction> CODEC = Codec.STRING.fieldOf("type").codec().dispatch(
        DialogAction::getType,
        type -> switch (type) {
            case "goto" -> GoToNode.CODEC;
            case "close" -> CloseDialog.CODEC;
            case "command" -> ExecuteCommand.CODEC;
            case "teleport" -> Teleport.CODEC;
            case "give_item" -> GiveItem.CODEC;
            case "sound" -> PlaySound.CODEC;
            case "gui" -> OpenGui.CODEC;
            case "set_variable" -> SetVariable.CODEC;
            case "remember" -> RememberChoice.CODEC;
            case "emotion" -> SetEmotion.CODEC;
            case "custom" -> Custom.CODEC;
            default -> throw new IllegalArgumentException("Unknown action type: " + type);
        }
    );

    // ========================================================================
    // Action Implementations
    // ========================================================================

    /**
     * Navigates to another dialog node.
     */
    record GoToNode(@Nonnull String nodeId) implements DialogAction {
        public static final MapCodec<GoToNode> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                Codec.STRING.fieldOf("nodeId").forGetter(GoToNode::nodeId)
            ).apply(instance, GoToNode::new)
        );

        @Override
        public String getType() {
            return "goto";
        }

        @Override
        public void execute(@Nonnull ServerPlayer player, @Nonnull NpcContext context) {
            context.setCurrentNode(nodeId);
        }
    }

    /**
     * Closes the dialog.
     */
    record CloseDialog() implements DialogAction {
        public static final MapCodec<CloseDialog> CODEC = MapCodec.unit(CloseDialog::new);

        @Override
        public String getType() {
            return "close";
        }

        @Override
        public void execute(@Nonnull ServerPlayer player, @Nonnull NpcContext context) {
            context.closeDialog();
        }
    }

    /**
     * Executes a server command.
     */
    record ExecuteCommand(
        @Nonnull String command,
        @Nonnull CommandSource source
    ) implements DialogAction {
        public static final MapCodec<ExecuteCommand> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                Codec.STRING.fieldOf("command").forGetter(ExecuteCommand::command),
                Codec.STRING.fieldOf("source").xmap(
                    CommandSource::valueOf,
                    CommandSource::name
                ).forGetter(ExecuteCommand::source)
            ).apply(instance, ExecuteCommand::new)
        );

        @Override
        public String getType() {
            return "command";
        }

        @Override
        public void execute(@Nonnull ServerPlayer player, @Nonnull NpcContext context) {
            String resolved = resolvePlaceholders(command, player, context);

            var server = player.getServer();
            if (server == null) return;

            var commands = server.getCommands();
            switch (source) {
                case PLAYER -> commands.performPrefixedCommand(
                    player.createCommandSourceStack(),
                    resolved
                );
                case CONSOLE -> commands.performPrefixedCommand(
                    server.createCommandSourceStack(),
                    resolved
                );
                case NPC -> commands.performPrefixedCommand(
                    player.createCommandSourceStack()
                        .withPermission(2)
                        .withSuppressedOutput(),
                    resolved
                );
            }
        }

        private String resolvePlaceholders(String cmd, ServerPlayer player, NpcContext context) {
            return cmd
                .replace("{player}", player.getName().getString())
                .replace("{player_uuid}", player.getStringUUID())
                .replace("{npc_id}", context.npcId().toString())
                .replace("{npc_name}", context.npcName());
        }
    }

    /**
     * Command execution source options.
     */
    enum CommandSource {
        PLAYER,   // Execute as the player
        CONSOLE,  // Execute as console (full permissions)
        NPC       // Execute with limited permissions
    }

    /**
     * Teleports the player to a location.
     */
    record Teleport(
        @Nullable BlockPos position,
        @Nullable ResourceKey<Level> dimension,
        @Nullable String zoneId
    ) implements DialogAction {
        public static final MapCodec<Teleport> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                BlockPos.CODEC.optionalFieldOf("position").forGetter(t -> Optional.ofNullable(t.position())),
                Level.RESOURCE_KEY_CODEC.optionalFieldOf("dimension").forGetter(t -> Optional.ofNullable(t.dimension())),
                Codec.STRING.optionalFieldOf("zoneId").forGetter(t -> Optional.ofNullable(t.zoneId()))
            ).apply(instance, (pos, dim, zone) -> new Teleport(pos.orElse(null), dim.orElse(null), zone.orElse(null)))
        );

        @Override
        public String getType() {
            return "teleport";
        }

        @Override
        public void execute(@Nonnull ServerPlayer player, @Nonnull NpcContext context) {
            var server = player.getServer();
            if (server == null) return;

            // If zoneId is specified, delegate to zone teleport system
            if (zoneId != null && !zoneId.isEmpty()) {
                // Zone teleport would be handled by external system
                DevMod.LOGGER.debug("Zone teleport requested: {} for {}", zoneId, player.getName().getString());
                return;
            }

            // Direct position teleport
            if (position != null) {
                ServerLevel targetLevel = dimension != null
                    ? server.getLevel(dimension)
                    : player.serverLevel();

                if (targetLevel != null) {
                    player.teleportTo(targetLevel,
                        position.getX() + 0.5,
                        position.getY(),
                        position.getZ() + 0.5,
                        player.getYRot(),
                        player.getXRot());
                }
            }
        }
    }

    /**
     * Gives an item to the player.
     */
    record GiveItem(
        @Nonnull ResourceLocation itemId,
        int count,
        @Nullable CompoundTag nbt
    ) implements DialogAction {
        public static final MapCodec<GiveItem> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                ResourceLocation.CODEC.fieldOf("itemId").forGetter(GiveItem::itemId),
                Codec.INT.optionalFieldOf("count", 1).forGetter(GiveItem::count),
                CompoundTag.CODEC.optionalFieldOf("nbt").forGetter(g -> Optional.ofNullable(g.nbt()))
            ).apply(instance, (id, count, nbt) -> new GiveItem(id, count, nbt.orElse(null)))
        );

        @Override
        public String getType() {
            return "give_item";
        }

        @Override
        public void execute(@Nonnull ServerPlayer player, @Nonnull NpcContext context) {
            var item = BuiltInRegistries.ITEM.get(itemId);
            if (item == null) {
                DevMod.LOGGER.warn("Unknown item in GiveItem action: {}", itemId);
                return;
            }

            ItemStack stack = new ItemStack(item, count);
            if (nbt != null) {
                // Apply custom NBT if provided
                // Note: In newer MC versions, use DataComponents instead
            }

            // Try to add to inventory, drop if full
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }

    /**
     * Plays a sound effect.
     */
    record PlaySound(
        @Nonnull ResourceLocation soundId,
        float volume,
        float pitch
    ) implements DialogAction {
        public static final MapCodec<PlaySound> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                ResourceLocation.CODEC.fieldOf("soundId").forGetter(PlaySound::soundId),
                Codec.FLOAT.optionalFieldOf("volume", 1.0f).forGetter(PlaySound::volume),
                Codec.FLOAT.optionalFieldOf("pitch", 1.0f).forGetter(PlaySound::pitch)
            ).apply(instance, PlaySound::new)
        );

        @Override
        public String getType() {
            return "sound";
        }

        @Override
        public void execute(@Nonnull ServerPlayer player, @Nonnull NpcContext context) {
            SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(soundId);
            if (sound != null) {
                player.playNotifySound(sound, SoundSource.MASTER, volume, pitch);
            } else {
                DevMod.LOGGER.warn("Unknown sound in PlaySound action: {}", soundId);
            }
        }
    }

    /**
     * Opens a GUI screen.
     */
    record OpenGui(@Nonnull String guiId) implements DialogAction {
        public static final MapCodec<OpenGui> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                Codec.STRING.fieldOf("guiId").forGetter(OpenGui::guiId)
            ).apply(instance, OpenGui::new)
        );

        @Override
        public String getType() {
            return "gui";
        }

        @Override
        public void execute(@Nonnull ServerPlayer player, @Nonnull NpcContext context) {
            // GUI opening would be handled by the network system
            DevMod.LOGGER.debug("OpenGui requested: {} for {}", guiId, player.getName().getString());
            context.requestGui(guiId);
        }
    }

    /**
     * Sets a custom variable in the dialog context for placeholder resolution.
     */
    record SetVariable(
        @Nonnull String key,
        @Nonnull String value
    ) implements DialogAction {
        public static final MapCodec<SetVariable> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                Codec.STRING.fieldOf("key").forGetter(SetVariable::key),
                Codec.STRING.fieldOf("value").forGetter(SetVariable::value)
            ).apply(instance, SetVariable::new)
        );

        @Override
        public String getType() {
            return "set_variable";
        }

        @Override
        public void execute(@Nonnull ServerPlayer player, @Nonnull NpcContext context) {
            // Resolve placeholders in the value itself
            String resolvedValue = value
                .replace("{player}", player.getName().getString())
                .replace("{player_uuid}", player.getStringUUID())
                .replace("{npc_id}", context.npcId().toString())
                .replace("{npc_name}", context.npcName());
            context.setVariable(key, resolvedValue);
            DevMod.LOGGER.debug("Set variable {} = {} for player {}", key, resolvedValue, player.getName().getString());
        }
    }

    /**
     * Stores a key-value pair in the player's dialog memory for this NPC.
     * Used to remember player choices across sessions.
     */
    record RememberChoice(
        @Nonnull String key,
        @Nonnull String value
    ) implements DialogAction {
        public static final MapCodec<RememberChoice> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                Codec.STRING.fieldOf("key").forGetter(RememberChoice::key),
                Codec.STRING.fieldOf("value").forGetter(RememberChoice::value)
            ).apply(instance, RememberChoice::new)
        );

        @Override
        public String getType() {
            return "remember";
        }

        @Override
        public void execute(@Nonnull ServerPlayer player, @Nonnull NpcContext context) {
            // Resolve placeholders in the value
            String resolvedValue = value
                .replace("{player}", player.getName().getString())
                .replace("{player_uuid}", player.getStringUUID())
                .replace("{npc_id}", context.npcId().toString())
                .replace("{npc_name}", context.npcName());

            // Store in dialog memory through registry
            context.getRegistry().setMemoryData(player.getUUID(), context.npcId(), key, resolvedValue);
            DevMod.LOGGER.debug("Remembered choice {} = {} for player {} with NPC {}",
                key, resolvedValue, player.getName().getString(), context.npcId());
        }
    }

    /**
     * Sets the NPC's emotional state.
     * Affects visual appearance (glow color) and can spawn particles.
     */
    record SetEmotion(@Nonnull NpcEmotion emotion) implements DialogAction {
        public static final MapCodec<SetEmotion> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                NpcEmotion.CODEC.fieldOf("emotion").forGetter(SetEmotion::emotion)
            ).apply(instance, SetEmotion::new)
        );

        @Override
        public String getType() {
            return "emotion";
        }

        @Override
        public void execute(@Nonnull ServerPlayer player, @Nonnull NpcContext context) {
            context.setEmotion(emotion);
            DevMod.LOGGER.debug("Set NPC {} emotion to {} for player {}",
                context.npcId(), emotion.getSerializedName(), player.getName().getString());
        }
    }

    /**
     * Custom action for extensibility.
     */
    record Custom(
        @Nonnull String handlerId,
        @Nonnull CompoundTag data
    ) implements DialogAction {
        public static final MapCodec<Custom> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                Codec.STRING.fieldOf("handlerId").forGetter(Custom::handlerId),
                CompoundTag.CODEC.optionalFieldOf("data", new CompoundTag()).forGetter(Custom::data)
            ).apply(instance, Custom::new)
        );

        public Custom(@Nonnull String handlerId) {
            this(handlerId, new CompoundTag());
        }

        @Override
        public String getType() {
            return "custom";
        }

        @Override
        public void execute(@Nonnull ServerPlayer player, @Nonnull NpcContext context) {
            // Custom handlers would be registered externally
            DevMod.LOGGER.debug("Custom action requested: {} for {}", handlerId, player.getName().getString());
        }
    }
}
