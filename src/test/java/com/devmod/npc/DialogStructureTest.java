package com.devmod.npc;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.npc.dialog.DialogLimits;
import com.devmod.npc.dialog.DialogNode;
import com.devmod.npc.dialog.DialogOption;
import com.devmod.npc.dialog.DialogSchedule;
import com.devmod.npc.dialog.DialogSet;
import com.devmod.npc.dialog.action.DialogAction;
import com.devmod.npc.dialog.group.GroupDialogNode;
import com.devmod.npc.dialog.group.SpeakerLine;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for dialog structure classes: DialogNode, DialogOption, DialogSet,
 * DialogLimits, DialogSchedule, SpeakerLine, GroupDialogNode.
 */
class DialogStructureTest {

    // ========================================================================
    // DialogNode tests
    // ========================================================================

    @Nested
    @DisplayName("DialogNode")
    class DialogNodeTests {

        @Test
        @DisplayName("terminal creates a node with no options")
        void terminalCreatesNoOptions() {
            DialogNode node = DialogNode.terminal("node_1", "Hello", "World");
            assertEquals("node_1", node.id());
            assertEquals(List.of("Hello", "World"), node.lines());
            assertTrue(node.isTerminal());
            assertTrue(node.options().isEmpty());
            assertFalse(node.hasCondition());
        }

        @Test
        @DisplayName("withOptions factory creates node with options")
        void withOptionsFactoryCreatesNode() {
            DialogOption opt = DialogOption.close("opt1", "Goodbye", "");
            DialogNode node = DialogNode.withOptions("node_1", List.of("Hi"), List.of(opt));
            assertFalse(node.isTerminal());
            assertEquals(1, node.options().size());
        }

        @Test
        @DisplayName("getCombinedText joins lines with newline")
        void getCombinedTextJoinsLines() {
            DialogNode node = DialogNode.terminal("n", "Line 1", "Line 2", "Line 3");
            assertEquals("Line 1\nLine 2\nLine 3", node.getCombinedText());
        }

        @Test
        @DisplayName("getOption finds option by ID")
        void getOptionFindsById() {
            DialogOption opt = DialogOption.close("close_btn", "Close", "x");
            DialogNode node = DialogNode.withOptions("n", List.of("text"), List.of(opt));
            Optional<DialogOption> found = node.getOption("close_btn");
            assertTrue(found.isPresent());
            assertEquals("Close", found.get().label());
        }

        @Test
        @DisplayName("getOption returns empty for missing ID")
        void getOptionEmptyForMissing() {
            DialogNode node = DialogNode.terminal("n", "text");
            assertTrue(node.getOption("nonexistent").isEmpty());
        }

        @Test
        @DisplayName("withLines creates copy with new lines")
        void withLinesCreatesNewCopy() {
            DialogNode original = DialogNode.terminal("n", "Original");
            DialogNode modified = original.withLines(List.of("New line"));
            assertEquals(List.of("New line"), modified.lines());
            assertEquals("n", modified.id());
        }

        @Test
        @DisplayName("withAddedLine appends a line")
        void withAddedLineAppends() {
            DialogNode node = DialogNode.terminal("n", "Line 1");
            DialogNode modified = node.withAddedLine("Line 2");
            assertEquals(2, modified.lines().size());
            assertEquals("Line 2", modified.lines().get(1));
        }

        @Test
        @DisplayName("withAddedOption appends an option")
        void withAddedOptionAppends() {
            DialogNode node = DialogNode.terminal("n", "text");
            DialogOption opt = DialogOption.close("o1", "OK", "");
            DialogNode modified = node.withAddedOption(opt);
            assertEquals(1, modified.options().size());
            assertFalse(modified.isTerminal());
        }
    }

    // ========================================================================
    // DialogOption tests
    // ========================================================================

    @Nested
    @DisplayName("DialogOption")
    class DialogOptionTests {

        @Test
        @DisplayName("goTo factory creates GoToNode action")
        void goToCreatesGoToAction() {
            DialogOption opt = DialogOption.goTo("opt1", "Continue", ">", "next_node");
            assertEquals("opt1", opt.id());
            assertEquals("Continue", opt.label());
            assertEquals(">", opt.icon());
            assertNull(opt.showCondition());
            assertFalse(opt.hasCondition());
            assertInstanceOf(DialogAction.GoToNode.class, opt.action());
            assertEquals("next_node", ((DialogAction.GoToNode) opt.action()).nodeId());
        }

        @Test
        @DisplayName("close factory creates CloseDialog action")
        void closeCreatesCloseAction() {
            DialogOption opt = DialogOption.close("bye", "Bye", "x");
            assertInstanceOf(DialogAction.CloseDialog.class, opt.action());
        }

        @Test
        @DisplayName("withLabel creates copy with new label")
        void withLabelCreatesCopy() {
            DialogOption original = DialogOption.close("id", "Old", "");
            DialogOption modified = original.withLabel("New");
            assertEquals("New", modified.label());
            assertEquals("id", modified.id());
        }

        @Test
        @DisplayName("withIcon creates copy with new icon")
        void withIconCreatesCopy() {
            DialogOption original = DialogOption.close("id", "Label", "old");
            DialogOption modified = original.withIcon("new_icon");
            assertEquals("new_icon", modified.icon());
        }

        @Test
        @DisplayName("withAction creates copy with new action")
        void withActionCreatesCopy() {
            DialogOption original = DialogOption.close("id", "Label", "");
            DialogOption modified = original.withAction(new DialogAction.GoToNode("target"));
            assertInstanceOf(DialogAction.GoToNode.class, modified.action());
        }
    }

    // ========================================================================
    // DialogSet tests
    // ========================================================================

    @Nested
    @DisplayName("DialogSet")
    class DialogSetTests {

        @Test
        @DisplayName("createEmpty produces empty dialog set")
        void createEmptyProducesEmptySet() {
            UUID owner = UUID.randomUUID();
            DialogSet set = DialogSet.createEmpty("set1", "My Dialog", owner);
            assertEquals("set1", set.id());
            assertEquals("My Dialog", set.name());
            assertTrue(set.nodes().isEmpty());
            assertEquals("", set.entryNodeId());
            assertFalse(set.isPreset());
            assertTrue(set.isModifiable());
            assertEquals(owner, set.ownerUUID());
            assertNull(set.schedule());
            assertEquals(0, set.revision());
        }

        @Test
        @DisplayName("createPreset produces non-modifiable dialog set")
        void createPresetIsNotModifiable() {
            DialogNode node = DialogNode.terminal("intro", "Hello!");
            DialogSet set = DialogSet.createPreset("preset1", "Greeting", Map.of("intro", node), "intro");
            assertTrue(set.isPreset());
            assertFalse(set.isModifiable());
            assertNull(set.ownerUUID());
        }

        @Test
        @DisplayName("getEntryNode returns correct node")
        void getEntryNodeReturnsCorrectNode() {
            DialogNode node = DialogNode.terminal("start", "Welcome");
            DialogSet set = DialogSet.createPreset("s", "n", Map.of("start", node), "start");
            Optional<DialogNode> entry = set.getEntryNode();
            assertTrue(entry.isPresent());
            assertEquals("start", entry.get().id());
        }

        @Test
        @DisplayName("getEntryNode returns empty when entry node missing")
        void getEntryNodeEmptyWhenMissing() {
            DialogSet set = DialogSet.createEmpty("s", "n", null);
            assertTrue(set.getEntryNode().isEmpty());
        }

        @Test
        @DisplayName("getNode finds node by ID")
        void getNodeFindsById() {
            DialogNode n1 = DialogNode.terminal("n1", "Text 1");
            DialogNode n2 = DialogNode.terminal("n2", "Text 2");
            DialogSet set = DialogSet.createPreset("s", "n", Map.of("n1", n1, "n2", n2), "n1");
            assertTrue(set.getNode("n2").isPresent());
            assertEquals("Text 2", set.getNode("n2").get().getCombinedText());
        }

        @Test
        @DisplayName("withNode adds or updates a node")
        void withNodeAddsNode() {
            DialogSet set = DialogSet.createEmpty("s", "n", null);
            DialogNode node = DialogNode.terminal("added", "Hello");
            DialogSet updated = set.withNode(node);
            assertEquals(1, updated.getNodeCount());
            assertTrue(updated.getNode("added").isPresent());
            assertEquals(set.revision() + 1, updated.revision());
        }

        @Test
        @DisplayName("withoutNode removes a node")
        void withoutNodeRemovesNode() {
            DialogNode node = DialogNode.terminal("n1", "Text");
            DialogSet set = DialogSet.createPreset("s", "n", Map.of("n1", node), "n1");
            DialogSet updated = set.withoutNode("n1");
            assertEquals(0, updated.getNodeCount());
        }

        @Test
        @DisplayName("withEntryNode changes the entry node ID")
        void withEntryNodeChangesId() {
            DialogSet set = DialogSet.createEmpty("s", "n", null);
            DialogSet updated = set.withEntryNode("new_entry");
            assertEquals("new_entry", updated.entryNodeId());
        }

        @Test
        @DisplayName("withName changes the dialog set name")
        void withNameChangesName() {
            DialogSet set = DialogSet.createEmpty("s", "Old Name", null);
            DialogSet updated = set.withName("New Name");
            assertEquals("New Name", updated.name());
        }

        @Test
        @DisplayName("isOwnedBy returns true for matching owner")
        void isOwnedByMatchesOwner() {
            UUID owner = UUID.randomUUID();
            DialogSet set = DialogSet.createEmpty("s", "n", owner);
            assertTrue(set.isOwnedBy(owner));
            assertFalse(set.isOwnedBy(UUID.randomUUID()));
        }

        @Test
        @DisplayName("isOwnedBy returns false when no owner")
        void isOwnedByFalseWhenNoOwner() {
            DialogSet set = DialogSet.createEmpty("s", "n", null);
            assertFalse(set.isOwnedBy(UUID.randomUUID()));
        }

        @Test
        @DisplayName("validate detects empty entry node ID")
        void validateDetectsEmptyEntry() {
            DialogSet set = DialogSet.createEmpty("s", "n", null);
            List<String> errors = set.validate();
            assertTrue(errors.stream().anyMatch(e -> e.contains("Entry node ID is not set")));
        }

        @Test
        @DisplayName("validate detects missing entry node reference")
        void validateDetectsMissingEntryNode() {
            DialogNode node = DialogNode.terminal("n1", "Text");
            DialogSet set = new DialogSet("s", "n", Map.of("n1", node), "missing_node",
                0, 0, false, null, null);
            List<String> errors = set.validate();
            assertTrue(errors.stream().anyMatch(e -> e.contains("does not exist")));
        }

        @Test
        @DisplayName("validate reports no errors for valid dialog set")
        void validateReportsNoErrorsWhenValid() {
            DialogOption opt = DialogOption.close("close", "Close", "");
            DialogNode node = DialogNode.withOptions("intro", List.of("Hello"), List.of(opt));
            DialogSet set = DialogSet.createPreset("s", "n", Map.of("intro", node), "intro");
            List<String> errors = set.validate();
            assertTrue(errors.isEmpty(), "Expected no errors but got: " + errors);
        }

        @Test
        @DisplayName("validate detects orphan GoToNode references")
        void validateDetectsOrphanGoToReferences() {
            DialogOption opt = DialogOption.goTo("go", "Go", "", "nonexistent_node");
            DialogNode node = DialogNode.withOptions("start", List.of("Hi"), List.of(opt));
            DialogSet set = DialogSet.createPreset("s", "n", Map.of("start", node), "start");
            List<String> errors = set.validate();
            assertTrue(errors.stream().anyMatch(e -> e.contains("non-existent node")));
        }

        @Test
        @DisplayName("hasSchedule returns false when no schedule")
        void hasScheduleFalseWhenNone() {
            DialogSet set = DialogSet.createEmpty("s", "n", null);
            assertFalse(set.hasSchedule());
        }

        @Test
        @DisplayName("hasSchedule returns false for ALWAYS schedule")
        void hasScheduleFalseForAlways() {
            DialogSet set = DialogSet.createPresetWithSchedule("s", "n",
                Map.of(), "", DialogSchedule.ALWAYS);
            assertFalse(set.hasSchedule());
        }

        @Test
        @DisplayName("hasSchedule returns true for restrictive schedule")
        void hasScheduleTrueForRestrictive() {
            DialogSet set = DialogSet.createPresetWithSchedule("s", "n",
                Map.of(), "", DialogSchedule.DAYTIME_ONLY);
            assertTrue(set.hasSchedule());
        }
    }

    // ========================================================================
    // DialogLimits tests
    // ========================================================================

    @Nested
    @DisplayName("DialogLimits")
    class DialogLimitsTests {

        @Test
        @DisplayName("truncate returns empty for null")
        void truncateReturnsEmptyForNull() {
            assertEquals("", DialogLimits.truncate(null, 10));
        }

        @Test
        @DisplayName("truncate returns original when within limit")
        void truncateReturnsOriginalWhenShort() {
            assertEquals("hello", DialogLimits.truncate("hello", 10));
        }

        @Test
        @DisplayName("truncate trims when exceeding limit")
        void truncateTrimsWhenLong() {
            String result = DialogLimits.truncate("abcdefghij", 5);
            assertEquals("abcde", result);
            assertEquals(5, result.length());
        }

        @Test
        @DisplayName("clampLines returns empty for null")
        void clampLinesEmptyForNull() {
            assertEquals(List.of(), DialogLimits.clampLines(null));
        }

        @Test
        @DisplayName("clampLines returns empty for empty list")
        void clampLinesEmptyForEmptyList() {
            assertEquals(List.of(), DialogLimits.clampLines(List.of()));
        }

        @Test
        @DisplayName("clampLines truncates long lines")
        void clampLinesTruncatesLongLines() {
            String longLine = "X".repeat(DialogLimits.MAX_LINE_LENGTH + 100);
            List<String> result = DialogLimits.clampLines(List.of(longLine));
            assertEquals(1, result.size());
            assertEquals(DialogLimits.MAX_LINE_LENGTH, result.get(0).length());
        }

        @Test
        @DisplayName("Constants reference expected NetworkConstants values")
        void constantsMatchNetworkConstants() {
            assertEquals(64, DialogLimits.MAX_DIALOG_ID_LENGTH);
            assertEquals(64, DialogLimits.MAX_DIALOG_NAME_LENGTH);
            assertEquals(64, DialogLimits.MAX_NODE_ID_LENGTH);
            assertEquals(128, DialogLimits.MAX_OPTION_LABEL_LENGTH);
            assertEquals(32, DialogLimits.MAX_OPTION_ICON_LENGTH);
            assertEquals(20, DialogLimits.MAX_LINES_PER_NODE);
            assertEquals(512, DialogLimits.MAX_LINE_LENGTH);
            assertEquals(10, DialogLimits.MAX_OPTIONS_PER_NODE);
            assertEquals(200, DialogLimits.MAX_NODES);
        }
    }

    // ========================================================================
    // DialogSchedule tests
    // ========================================================================

    @Nested
    @DisplayName("DialogSchedule")
    class DialogScheduleTests {

        @Test
        @DisplayName("ALWAYS schedule is always available")
        void alwaysScheduleIsAlwaysAvailable() {
            assertTrue(DialogSchedule.ALWAYS.isAlwaysAvailable());
            assertTrue(DialogSchedule.ALWAYS.checkGameTime(0));
            assertTrue(DialogSchedule.ALWAYS.checkGameTime(12000));
            assertTrue(DialogSchedule.ALWAYS.checkRealWorldDay(DayOfWeek.MONDAY));
            assertTrue(DialogSchedule.ALWAYS.checkDateRange(LocalDate.of(2020, 1, 1)));
        }

        @Test
        @DisplayName("DAYTIME_ONLY passes for daytime ticks")
        void daytimeOnlyPassesForDaytime() {
            assertTrue(DialogSchedule.DAYTIME_ONLY.checkGameTime(0));
            assertTrue(DialogSchedule.DAYTIME_ONLY.checkGameTime(6000));
            assertTrue(DialogSchedule.DAYTIME_ONLY.checkGameTime(11999));
            assertFalse(DialogSchedule.DAYTIME_ONLY.checkGameTime(12000));
            assertFalse(DialogSchedule.DAYTIME_ONLY.checkGameTime(18000));
        }

        @Test
        @DisplayName("NIGHTTIME_ONLY passes for nighttime ticks")
        void nighttimeOnlyPassesForNighttime() {
            assertTrue(DialogSchedule.NIGHTTIME_ONLY.checkGameTime(12000));
            assertTrue(DialogSchedule.NIGHTTIME_ONLY.checkGameTime(18000));
            assertTrue(DialogSchedule.NIGHTTIME_ONLY.checkGameTime(23999));
            assertFalse(DialogSchedule.NIGHTTIME_ONLY.checkGameTime(0));
            assertFalse(DialogSchedule.NIGHTTIME_ONLY.checkGameTime(6000));
        }

        @Test
        @DisplayName("WEEKENDS_ONLY passes only on Saturday and Sunday")
        void weekendsOnlyPassesCorrectDays() {
            assertTrue(DialogSchedule.WEEKENDS_ONLY.checkRealWorldDay(DayOfWeek.SATURDAY));
            assertTrue(DialogSchedule.WEEKENDS_ONLY.checkRealWorldDay(DayOfWeek.SUNDAY));
            assertFalse(DialogSchedule.WEEKENDS_ONLY.checkRealWorldDay(DayOfWeek.MONDAY));
            assertFalse(DialogSchedule.WEEKENDS_ONLY.checkRealWorldDay(DayOfWeek.FRIDAY));
        }

        @Test
        @DisplayName("Date range check works correctly")
        void dateRangeCheckWorks() {
            DialogSchedule schedule = DialogSchedule.builder()
                .dateRange(LocalDate.of(2025, 12, 20), LocalDate.of(2025, 12, 31))
                .build();
            assertFalse(schedule.checkDateRange(LocalDate.of(2025, 12, 19)));
            assertTrue(schedule.checkDateRange(LocalDate.of(2025, 12, 20)));
            assertTrue(schedule.checkDateRange(LocalDate.of(2025, 12, 25)));
            assertTrue(schedule.checkDateRange(LocalDate.of(2025, 12, 31)));
            assertFalse(schedule.checkDateRange(LocalDate.of(2026, 1, 1)));
        }

        @Test
        @DisplayName("Game time normalizes values over 24000")
        void gameTimeNormalizesOverflow() {
            DialogSchedule schedule = DialogSchedule.DAYTIME_ONLY;
            // 24000 + 3000 = 27000, normalized to 3000 which is daytime
            assertTrue(schedule.checkGameTime(27000));
        }

        @Test
        @DisplayName("isAvailable with explicit values checks all constraints")
        void isAvailableWithExplicitValues() {
            DialogSchedule schedule = DialogSchedule.builder()
                .gameTimeRange(0, 12000)
                .days(DayOfWeek.MONDAY)
                .build();
            assertTrue(schedule.isAvailable(6000, LocalDate.of(2026, 2, 16))); // Monday
            assertFalse(schedule.isAvailable(15000, LocalDate.of(2026, 2, 16))); // wrong time
            assertFalse(schedule.isAvailable(6000, LocalDate.of(2026, 2, 17))); // wrong day (Tuesday)
        }

        @Test
        @DisplayName("ticksToTimeString converts correctly")
        void ticksToTimeStringConverts() {
            // 0 ticks = 6:00 AM
            assertEquals("06:00", DialogSchedule.ticksToTimeString(0));
            // 6000 ticks = noon (12:00)
            assertEquals("12:00", DialogSchedule.ticksToTimeString(6000));
            // 12000 ticks = 18:00
            assertEquals("18:00", DialogSchedule.ticksToTimeString(12000));
            // 18000 ticks = midnight (00:00)
            assertEquals("00:00", DialogSchedule.ticksToTimeString(18000));
        }

        @Test
        @DisplayName("timeStringToTicks round-trips with ticksToTimeString")
        void timeStringToTicksRoundTrips() {
            // Test several tick values
            for (long ticks : new long[]{0, 6000, 12000, 18000}) {
                String timeStr = DialogSchedule.ticksToTimeString(ticks);
                long recovered = DialogSchedule.timeStringToTicks(timeStr);
                assertEquals(ticks, recovered,
                    "Round-trip failed for " + ticks + " ticks (str: " + timeStr + ")");
            }
        }

        @Test
        @DisplayName("Builder produces correct schedule")
        void builderProducesCorrect() {
            DialogSchedule schedule = DialogSchedule.builder()
                .minGameTime(1000)
                .maxGameTime(5000)
                .days(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .build();
            assertTrue(schedule.hasGameTimeRestriction());
            assertTrue(schedule.hasDayOfWeekRestriction());
            assertTrue(schedule.hasDateRangeRestriction());
            assertFalse(schedule.isAlwaysAvailable());
        }

        @Test
        @DisplayName("Description methods return non-null for restricted schedules")
        void descriptionMethodsReturnNonNull() {
            DialogSchedule schedule = DialogSchedule.builder()
                .gameTimeRange(0, 12000)
                .days(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)
                .dateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30))
                .build();
            assertNotNull(schedule.getGameTimeDescription());
            assertNotNull(schedule.getDayOfWeekDescription());
            assertNotNull(schedule.getDateRangeDescription());
        }

        @Test
        @DisplayName("Description methods return null for unrestricted schedules")
        void descriptionMethodsReturnNullForUnrestricted() {
            assertNull(DialogSchedule.ALWAYS.getGameTimeDescription());
            assertNull(DialogSchedule.ALWAYS.getDayOfWeekDescription());
            assertNull(DialogSchedule.ALWAYS.getDateRangeDescription());
        }
    }

    // ========================================================================
    // SpeakerLine additional tests (beyond existing SpeakerLineTest)
    // ========================================================================

    @Nested
    @DisplayName("SpeakerLine (additional)")
    class SpeakerLineAdditionalTests {

        @Test
        @DisplayName("npc factory with emotion sets emotion correctly")
        void npcFactoryWithEmotion() {
            UUID npcId = UUID.randomUUID();
            SpeakerLine line = SpeakerLine.npc(npcId, "Angry text", NpcEmotion.ANGRY);
            assertTrue(line.hasEmotion());
            assertEquals(NpcEmotion.ANGRY, line.emotion());
            assertTrue(line.isNpc());
        }

        @Test
        @DisplayName("withEmotion creates copy with new emotion")
        void withEmotionCreatesCopy() {
            SpeakerLine line = SpeakerLine.player("text");
            SpeakerLine withEmo = line.withEmotion(NpcEmotion.HAPPY);
            assertTrue(withEmo.hasEmotion());
            assertEquals(NpcEmotion.HAPPY, withEmo.emotion());
            assertFalse(line.hasEmotion());
        }

        @Test
        @DisplayName("withNameOverride sets speaker name")
        void withNameOverrideSetsName() {
            SpeakerLine line = SpeakerLine.narrator("text");
            SpeakerLine withName = line.withNameOverride("Custom Name");
            assertTrue(withName.hasNameOverride());
            assertEquals("Custom Name", withName.speakerNameOverride());
        }

        @Test
        @DisplayName("hasNameOverride returns false for null and empty")
        void hasNameOverrideFalseForNullAndEmpty() {
            SpeakerLine line = SpeakerLine.player("text");
            assertFalse(line.hasNameOverride());
            SpeakerLine withEmpty = line.withNameOverride("");
            assertFalse(withEmpty.hasNameOverride());
        }

        @Test
        @DisplayName("withText creates copy with new text")
        void withTextCreatesCopy() {
            SpeakerLine line = SpeakerLine.player("old");
            SpeakerLine modified = line.withText("new");
            assertEquals("new", modified.text());
            assertEquals("old", line.text());
        }

        @Test
        @DisplayName("PLAYER_SPEAKER_ID and NARRATOR_SPEAKER_ID are distinct")
        void specialIdsAreDistinct() {
            assertNotEquals(SpeakerLine.PLAYER_SPEAKER_ID, SpeakerLine.NARRATOR_SPEAKER_ID);
        }
    }

    // ========================================================================
    // GroupDialogNode tests
    // ========================================================================

    @Nested
    @DisplayName("GroupDialogNode")
    class GroupDialogNodeTests {

        @Test
        @DisplayName("empty factory creates node with no lines or options")
        void emptyFactoryCreatesEmptyNode() {
            GroupDialogNode node = GroupDialogNode.empty("node1");
            assertEquals("node1", node.id());
            assertEquals(0, node.lineCount());
            assertEquals(0, node.optionCount());
            assertFalse(node.hasCondition());
        }

        @Test
        @DisplayName("single factory creates node with one line")
        void singleFactoryCreatesOneLine() {
            SpeakerLine line = SpeakerLine.player("Hello");
            GroupDialogNode node = GroupDialogNode.single("node1", line);
            assertEquals(1, node.lineCount());
            assertTrue(node.getLine(0).isPresent());
            assertEquals("Hello", node.getLine(0).get().text());
        }

        @Test
        @DisplayName("getLine returns empty for out-of-bounds index")
        void getLineEmptyForOutOfBounds() {
            GroupDialogNode node = GroupDialogNode.empty("n");
            assertTrue(node.getLine(0).isEmpty());
            assertTrue(node.getLine(-1).isEmpty());
            assertTrue(node.getLine(100).isEmpty());
        }

        @Test
        @DisplayName("withLine adds a line to the node")
        void withLineAddsLine() {
            GroupDialogNode node = GroupDialogNode.empty("n");
            SpeakerLine line = SpeakerLine.narrator("The story begins...");
            GroupDialogNode updated = node.withLine(line);
            assertEquals(1, updated.lineCount());
            assertEquals("The story begins...", updated.getLine(0).get().text());
        }

        @Test
        @DisplayName("withLineAt replaces a line at index")
        void withLineAtReplacesLine() {
            SpeakerLine line1 = SpeakerLine.player("Original");
            GroupDialogNode node = GroupDialogNode.single("n", line1);
            SpeakerLine replacement = SpeakerLine.player("Replaced");
            GroupDialogNode updated = node.withLineAt(0, replacement);
            assertEquals("Replaced", updated.getLine(0).get().text());
        }

        @Test
        @DisplayName("withoutLine removes a line at index")
        void withoutLineRemovesLine() {
            SpeakerLine line = SpeakerLine.player("Text");
            GroupDialogNode node = GroupDialogNode.single("n", line);
            GroupDialogNode updated = node.withoutLine(0);
            assertEquals(0, updated.lineCount());
        }

        @Test
        @DisplayName("withoutLine returns same node for invalid index")
        void withoutLineReturnsSameForInvalidIndex() {
            GroupDialogNode node = GroupDialogNode.empty("n");
            GroupDialogNode same = node.withoutLine(5);
            assertSame(node, same);
        }

        @Test
        @DisplayName("withOption adds an option")
        void withOptionAddsOption() {
            GroupDialogNode node = GroupDialogNode.empty("n");
            DialogOption opt = DialogOption.close("opt1", "OK", "");
            GroupDialogNode updated = node.withOption(opt);
            assertEquals(1, updated.optionCount());
        }

        @Test
        @DisplayName("getOption finds option by ID")
        void getOptionFindsById() {
            DialogOption opt = DialogOption.close("found", "Label", "");
            GroupDialogNode node = GroupDialogNode.empty("n").withOption(opt);
            assertTrue(node.getOption("found").isPresent());
            assertTrue(node.getOption("missing").isEmpty());
        }
    }
}
