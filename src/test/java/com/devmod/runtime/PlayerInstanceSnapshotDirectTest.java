package com.devmod.runtime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerInstanceSnapshotDirectTest {

    @Test
    @DisplayName("Snapshot round-trips through NBT")
    void snapshotRoundTripNbt() {
        SnapshotFixture fixture = SnapshotFixture.create();
        CompoundTag tag = fixture.snapshot.toNBT();
        PlayerInstanceSnapshot loaded = PlayerInstanceSnapshot.fromNBT(tag);

        assertEquals(fixture.playerId, loaded.getPlayerId());
        assertEquals(fixture.instanceId, loaded.getInstanceId());
        assertEquals(PlayerInstanceState.PREPARING, loaded.getState());
        assertEquals(fixture.dimension, loaded.getOriginalDimension());
        assertEquals(fixture.mobId, loaded.getMobId());
        assertEquals("endurance", loaded.getQuestType());
        assertEquals("arena_basic", loaded.getTemplateId());
        assertEquals("policy_default", loaded.getPolicyId());

        assertEquals(GameType.SURVIVAL, loaded.getOriginalGameMode());
        assertEquals(18.0f, loaded.getOriginalHealth(), 0.0001f);
        assertEquals(20.0f, loaded.getOriginalMaxHealth(), 0.0001f);

        CompoundTag inventoryNBT = loaded.getInventoryNBT();
        assertNotNull(inventoryNBT);
        assertEquals("inv", inventoryNBT.getString("marker"));

        CompoundTag potionEffectsNBT = loaded.getPotionEffectsNBT();
        assertNotNull(potionEffectsNBT);
        assertEquals("speed", potionEffectsNBT.getString("effect"));

        assertEquals(fixture.leaderId, loaded.getPartyLeaderId());
        assertEquals(2, loaded.getPartyMembers().size());
        assertTrue(loaded.getPartyMembers().containsAll(List.of(fixture.leaderId, fixture.memberId)));
    }

    @Test
    @DisplayName("Snapshot saves and loads from file")
    void snapshotSaveLoadFile() throws Exception {
        SnapshotFixture fixture = SnapshotFixture.create();
        Path tempDir = Files.createTempDirectory("devmod-snapshot");
        Path snapshotFile = tempDir.resolve("snapshot.dat");

        fixture.snapshot.saveToFile(snapshotFile);
        PlayerInstanceSnapshot loaded = PlayerInstanceSnapshot.loadFromFile(snapshotFile);

        assertEquals(fixture.playerId, loaded.getPlayerId());
        assertEquals(fixture.instanceId, loaded.getInstanceId());
        assertEquals(PlayerInstanceState.PREPARING, loaded.getState());
    }

    private static class SnapshotFixture {
        private final UUID playerId;
        private final UUID instanceId;
        private final UUID leaderId;
        private final UUID memberId;
        private final ResourceLocation dimension;
        private final ResourceLocation mobId;
        private final PlayerInstanceSnapshot snapshot;

        private SnapshotFixture(UUID playerId,
                                UUID instanceId,
                                UUID leaderId,
                                UUID memberId,
                                ResourceLocation dimension,
                                ResourceLocation mobId,
                                PlayerInstanceSnapshot snapshot) {
            this.playerId = playerId;
            this.instanceId = instanceId;
            this.leaderId = leaderId;
            this.memberId = memberId;
            this.dimension = dimension;
            this.mobId = mobId;
            this.snapshot = snapshot;
        }

        private static SnapshotFixture create() {
            UUID playerId = UUID.randomUUID();
            UUID instanceId = UUID.randomUUID();
            UUID leaderId = UUID.randomUUID();
            UUID memberId = UUID.randomUUID();

            ResourceLocation dimension = ResourceLocation.parse("minecraft:overworld");
            ResourceLocation mobId = ResourceLocation.parse("minecraft:zombie");

            CompoundTag inventory = new CompoundTag();
            inventory.putString("marker", "inv");
            CompoundTag effects = new CompoundTag();
            effects.putString("effect", "speed");

            PlayerInstanceSnapshot snapshot = new PlayerInstanceSnapshot(playerId)
                .withPosition(dimension, 1.5, 64.0, -3.25, 90.0f, 0.0f)
                .withInventory(inventory, null)
                .withGameMode(GameType.SURVIVAL)
                .withHealth(18.0f, 20.0f)
                .withFood(10, 5.0f, 0.2f)
                .withEffects(effects)
                .withExperience(5, 0.5f, 100)
                .withQuest("endurance", mobId, 12, true)
                .withParty(leaderId, List.of(leaderId, memberId))
                .withArenaTemplate("arena_basic", 2, "policy_default", 1);
            snapshot.setInstanceId(instanceId);
            snapshot.setState(PlayerInstanceState.PREPARING);

            return new SnapshotFixture(playerId, instanceId, leaderId, memberId, dimension, mobId, snapshot);
        }
    }
}
