package com.devmod.area.network;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import com.devmod.TestBootstrap;
import com.devmod.area.data.AreaDefinition;
import com.devmod.area.data.AreaDimensions;
import com.devmod.area.data.AreaGenerationType;
import com.devmod.area.data.AreaOptions;
import com.devmod.area.data.AreaPalette;
import com.devmod.area.data.AreaShape;
import com.devmod.runtime.NexusPalette;

@DisplayName("Area Network Handler")
class AreaNetworkHandlerTest {

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    @Test
    @DisplayName("sanitizeDefinition rejects PATH without waypoint NBT")
    void sanitizeDefinition_rejectsPathWithoutNbt() throws Exception {
        ResourceLocation dimensionId = ResourceLocation.withDefaultNamespace("overworld");
        AreaDefinition input = AreaDefinition.builder()
            .id(Objects.requireNonNull(UUID.randomUUID()))
            .name("Path Test")
            .generationType(Objects.requireNonNull(AreaGenerationType.CUSTOM))
            .shape(Objects.requireNonNull(AreaShape.PATH))
            .dimensions(new AreaDimensions(8, 8, 4, 64))
            .center(new BlockPos(0, 64, 0))
            .dimension(Objects.requireNonNull(dimensionId))
            .palette(Objects.requireNonNull(createValidPalette()))
            .options(Objects.requireNonNull(AreaOptions.DEFAULT))
            .customShapeNbt(null)
            .build();

        MinecraftServer server = mock(MinecraftServer.class);
        ServerLevel level = mock(ServerLevel.class);
        ServerPlayer player = mock(ServerPlayer.class);
        ResourceKey<Level> levelKey = Objects.requireNonNull(ResourceKey.create(Objects.requireNonNull(Registries.DIMENSION), dimensionId));

        when(player.getServer()).thenReturn(server);
        when(server.getLevel(Objects.requireNonNull(levelKey))).thenReturn(level);
        when(player.level()).thenReturn(level);
        when(level.dimension()).thenReturn(levelKey);
        when(level.getMinBuildHeight()).thenReturn(0);
        when(level.getMaxBuildHeight()).thenReturn(256);
        when(player.getName()).thenReturn(Component.literal("TestPlayer"));

        Method method = AreaNetworkHandler.class.getDeclaredMethod(
            "sanitizeDefinition", AreaDefinition.class, AreaDefinition.class, ServerPlayer.class, boolean.class);
        method.setAccessible(true);

        AreaDefinition sanitized = (AreaDefinition) method.invoke(null, input, null, player, true);

        assertNull(sanitized, "PATH definitions without NBT should be rejected");
    }

    private static AreaPalette createValidPalette() {
        return AreaPalette.empty("test")
            .withMaterial(Objects.requireNonNull(NexusPalette.Key.FLOOR.id()), "minecraft:stone")
            .withMaterial(Objects.requireNonNull(NexusPalette.Key.FLOOR_ALT.id()), "minecraft:cobblestone")
            .withMaterial(Objects.requireNonNull(NexusPalette.Key.WALL.id()), "minecraft:stone_bricks");
    }
}
