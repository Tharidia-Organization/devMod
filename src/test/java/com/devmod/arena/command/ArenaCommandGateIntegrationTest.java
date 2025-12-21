package com.devmod.arena.command;

import com.devmod.arena.builder.ArenaBuilder;
import com.devmod.arena.config.ArenaTemplateConfig;
import com.devmod.arena.registry.ArenaTemplate;
import com.devmod.arena.registry.ArenaTemplateRegistry;
import com.devmod.arena.telemetry.ArenaTelemetry;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArenaCommandGateIntegrationTest {

    @Test
    @SuppressWarnings("null")
    void arenaCreateFailsWhenInstanceOnlyFalse() throws Exception {
        String prevInstanceOnly = System.getProperty("devmod.arena.instanceOnly");
        String prevLegacyAllowed = System.getProperty("devmod.arena.allowLegacyOverworldArena");
        try {
            System.setProperty("devmod.arena.instanceOnly", "false");
            System.clearProperty("devmod.arena.allowLegacyOverworldArena");

            try (ArenaTemplateRegistry registry = new ArenaTemplateRegistry(new ArenaTelemetry())) {
                registry.load(ArenaTemplate.defaultTemplate());

                ArenaTemplateConfig.ConfigSnapshot snapshot = ArenaTemplateConfig.load().snapshot();
                ResourceLocation overworld = Objects.requireNonNull(
                    ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
                    "overworldLocation");
                ResourceKey<Level> key = Objects.requireNonNull(
                    ResourceKey.create(Objects.requireNonNull(Registries.DIMENSION, "dimensionRegistry"), overworld),
                    "dimensionKey");
                ArenaCommands commands = new ArenaCommands(
                    registry,
                    null,
                    null,
                    Path.of("config/devmod/arena_templates"),
                    level -> mock(ArenaBuilder.class),
                    null,
                    () -> snapshot,
                    null,
                    null
                ) {
                    @Override
                    protected ResourceKey<Level> resolveDimensionKey(CommandSourceStack source) {
                        return key;
                    }
                };

                CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
                commands.register(dispatcher);

                CommandSourceStack source = mock(CommandSourceStack.class);
                when(source.isPlayer()).thenReturn(false);

                dispatcher.execute("arena create default_flat_64", source);

                ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
                verify(source).sendFailure(captor.capture());
                Component failure = Objects.requireNonNull(captor.getValue(), "failureMessage");
                assertTrue(failure.getString().contains("Instance-only mode"));
            }
        } finally {
            restoreProperty("devmod.arena.instanceOnly", prevInstanceOnly);
            restoreProperty("devmod.arena.allowLegacyOverworldArena", prevLegacyAllowed);
        }
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
