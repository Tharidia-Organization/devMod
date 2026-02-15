package com.devmod.clone;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.clone.block.entity.ClonePulverizerBlockEntity;
import com.devmod.clone.block.entity.NeurocellAccess;
import com.devmod.clone.client.renderer.MannequinBillboardRegistry;
import com.devmod.clone.client.renderer.TelepadPortalGeometry;
import com.devmod.clone.data.BioscanData;
import com.devmod.clone.integration.EnduranceCloneWave;
import com.devmod.clone.network.CloneNetworkHandler;
import com.devmod.clone.network.MannequinRotationPayload;
import com.devmod.clone.network.MannequinSkinPayload;
import com.devmod.clone.network.TelepadConfigPayload;
import com.devmod.clone.network.TelepadOpenScreenPayload;
import com.devmod.clone.recipe.CentrifugingRecipe;
import com.devmod.clone.recipe.PulverizingRecipe;

/**
 * Structural/contract tests across the clone module.
 * Validates class invariants, singleton patterns, interface contracts, etc.
 */
class CloneModuleStructureTest {

    @Nested
    @DisplayName("Utility classes have private constructors")
    class UtilityClassTests {

        @Test
        @DisplayName("TelepadPortalGeometry is final with private constructor")
        void telepadPortalGeometry() throws Exception {
            assertTrue(Modifier.isFinal(TelepadPortalGeometry.class.getModifiers()));
            var ctor = TelepadPortalGeometry.class.getDeclaredConstructor();
            assertTrue(Modifier.isPrivate(ctor.getModifiers()));
        }

        @Test
        @DisplayName("CloneModule is final with private constructor")
        void cloneModule() throws Exception {
            assertTrue(Modifier.isFinal(CloneModule.class.getModifiers()));
            var ctor = CloneModule.class.getDeclaredConstructor();
            assertTrue(Modifier.isPrivate(ctor.getModifiers()));
        }
    }

    @Nested
    @DisplayName("Singleton patterns")
    class SingletonTests {

        @Test
        @DisplayName("CloneNetworkHandler.INSTANCE is not null")
        void networkHandlerInstance() {
            assertNotNull(CloneNetworkHandler.INSTANCE);
        }

        @Test
        @DisplayName("EnduranceCloneWave.INSTANCE is not null")
        void enduranceCloneWaveInstance() {
            assertNotNull(EnduranceCloneWave.INSTANCE);
        }

        @Test
        @DisplayName("CloneNetworkHandler is final")
        void networkHandlerIsFinal() {
            assertTrue(Modifier.isFinal(CloneNetworkHandler.class.getModifiers()));
        }

        @Test
        @DisplayName("EnduranceCloneWave is final")
        void enduranceCloneWaveIsFinal() {
            assertTrue(Modifier.isFinal(EnduranceCloneWave.class.getModifiers()));
        }
    }

    @Nested
    @DisplayName("NeurocellAccess interface")
    class NeurocellAccessTests {

        @Test
        @DisplayName("NeurocellAccess is an interface")
        void isInterface() {
            assertTrue(NeurocellAccess.class.isInterface());
        }

        @Test
        @DisplayName("Has isDataReady method")
        void hasIsDataReady() throws Exception {
            assertNotNull(NeurocellAccess.class.getMethod("isDataReady"));
        }

        @Test
        @DisplayName("Has hasEmptyBioscanner method")
        void hasHasEmptyBioscanner() throws Exception {
            assertNotNull(NeurocellAccess.class.getMethod("hasEmptyBioscanner"));
        }

        @Test
        @DisplayName("Has consumeData method")
        void hasConsumeData() throws Exception {
            assertNotNull(NeurocellAccess.class.getMethod("consumeData"));
        }

        @Test
        @DisplayName("Has fillBioscannerFromImprinter method")
        void hasFillBioscannerFromImprinter() throws Exception {
            assertNotNull(NeurocellAccess.class.getMethod("fillBioscannerFromImprinter", BioscanData.class));
        }
    }

    @Nested
    @DisplayName("Payload records are records")
    class PayloadRecordTests {

        @Test
        @DisplayName("MannequinRotationPayload is a record")
        void mannequinRotation() {
            assertTrue(MannequinRotationPayload.class.isRecord());
        }

        @Test
        @DisplayName("MannequinSkinPayload is a record")
        void mannequinSkin() {
            assertTrue(MannequinSkinPayload.class.isRecord());
        }

        @Test
        @DisplayName("TelepadConfigPayload is a record")
        void telepadConfig() {
            assertTrue(TelepadConfigPayload.class.isRecord());
        }

        @Test
        @DisplayName("TelepadOpenScreenPayload is a record")
        void telepadOpenScreen() {
            assertTrue(TelepadOpenScreenPayload.class.isRecord());
        }

        @Test
        @DisplayName("BioscanData is a record")
        void bioscanData() {
            assertTrue(BioscanData.class.isRecord());
        }
    }

    @Nested
    @DisplayName("Recipe constants")
    class RecipeConstantTests {

        @Test
        @DisplayName("CentrifugingRecipe.DEFAULT_PROCESSING_TIME is positive")
        void centrifugingTimePositive() {
            assertTrue(CentrifugingRecipe.DEFAULT_PROCESSING_TIME > 0);
        }

        @Test
        @DisplayName("PulverizingRecipe.DEFAULT_PROCESSING_TIME is positive")
        void pulverizingTimePositive() {
            assertTrue(PulverizingRecipe.DEFAULT_PROCESSING_TIME > 0);
        }

        @Test
        @DisplayName("Both recipe types have same default time (100 ticks)")
        void sameDefaultTime() {
            assertEquals(
                CentrifugingRecipe.DEFAULT_PROCESSING_TIME,
                PulverizingRecipe.DEFAULT_PROCESSING_TIME
            );
            assertEquals(100, CentrifugingRecipe.DEFAULT_PROCESSING_TIME);
        }
    }

    @Nested
    @DisplayName("Portal geometry constants")
    class PortalGeometryConstantTests {

        @Test
        @DisplayName("PORTAL_WIDTH is positive")
        void widthPositive() {
            assertTrue(TelepadPortalGeometry.PORTAL_WIDTH > 0);
        }

        @Test
        @DisplayName("PORTAL_HEIGHT is positive")
        void heightPositive() {
            assertTrue(TelepadPortalGeometry.PORTAL_HEIGHT > 0);
        }

        @Test
        @DisplayName("PORTAL_Y_OFFSET is non-negative")
        void yOffsetNonNegative() {
            assertTrue(TelepadPortalGeometry.PORTAL_Y_OFFSET >= 0);
        }

        @Test
        @DisplayName("Portal is taller than wide")
        void tallerThanWide() {
            assertTrue(TelepadPortalGeometry.PORTAL_HEIGHT > TelepadPortalGeometry.PORTAL_WIDTH);
        }
    }

    @Nested
    @DisplayName("ClonePulverizerBlockEntity inventory")
    class PulverizerInventoryTests {

        @Test
        @DisplayName("Four slots total (input, output, grinder_left, grinder_right)")
        void fourSlots() {
            // Slot indices: 0, 1, 2, 3
            assertEquals(0, ClonePulverizerBlockEntity.SLOT_INPUT);
            assertEquals(1, ClonePulverizerBlockEntity.SLOT_OUTPUT);
            assertEquals(2, ClonePulverizerBlockEntity.SLOT_GRINDER_LEFT);
            assertEquals(3, ClonePulverizerBlockEntity.SLOT_GRINDER_RIGHT);
        }
    }
}
