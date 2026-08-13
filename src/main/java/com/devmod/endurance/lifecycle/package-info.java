/**
 * Quest lifecycle event system for decoupling subsystems.
 *
 * <p>This package provides an event-driven architecture for quest lifecycle
 * management, replacing the direct INSTANCE calls in EnduranceEventHandler
 * with a publish-subscribe pattern.</p>
 *
 * <h2>Architecture Overview</h2>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────┐
 * │                    EnduranceEventHandler                        │
 * │  (NeoForge Event Subscriber - receives Minecraft events)        │
 * └──────────────────────────┬──────────────────────────────────────┘
 *                            │
 *                            ▼
 * ┌─────────────────────────────────────────────────────────────────┐
 * │                       QuestEventBus                              │
 * │  (Central dispatcher - manages listeners, publishes events)     │
 * └──────────────────────────┬──────────────────────────────────────┘
 *                            │
 *          ┌─────────────────┼─────────────────┐
 *          │                 │                 │
 *          ▼                 ▼                 ▼
 * ┌─────────────┐   ┌─────────────┐   ┌─────────────┐
 * │ ComboSystem │   │ PerkSystem  │   │ TideManager │
 * │ (listener)  │   │ (listener)  │   │ (listener)  │
 * └─────────────┘   └─────────────┘   └─────────────┘
 * </pre>
 *
 * <h2>Event Hierarchy</h2>
 * <ul>
 *   <li>{@link QuestLifecycleEvent} - Sealed base interface
 *     <ul>
 *       <li>{@link QuestLifecycleEvent.QuestStarted} - Quest initialized</li>
 *       <li>{@link QuestLifecycleEvent.QuestEnded} - Quest completed/failed</li>
 *       <li>{@link QuestLifecycleEvent.WaveStarted} - Wave begins</li>
 *       <li>{@link QuestLifecycleEvent.WaveCompleted} - Wave cleared</li>
 *       <li>{@link QuestLifecycleEvent.PlayerJoinedQuest} - Late join</li>
 *       <li>{@link QuestLifecycleEvent.PlayerLeftQuest} - Disconnect/death</li>
 *       <li>{@link QuestLifecycleEvent.CheckpointReached} - Progress saved</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h2>Context Objects</h2>
 * <ul>
 *   <li>{@link QuestContext} - Immutable quest state snapshot</li>
 *   <li>{@link WaveContext} - Wave state with combat stats</li>
 * </ul>
 *
 * <h2>Migration Guide</h2>
 * <p>To convert a system from direct INSTANCE calls to event-driven:</p>
 * <ol>
 *   <li>Implement {@link QuestLifecycleListener}</li>
 *   <li>Override the event methods you need (onQuestStarted, etc.)</li>
 *   <li>Set priority based on execution order needs</li>
 *   <li>Register with {@code QuestEventBus.INSTANCE.register(this)}</li>
 *   <li>Remove the direct call from EnduranceEventHandler</li>
 * </ol>
 *
 * <h2>Example Migration</h2>
 * <pre>{@code
 * // BEFORE (in EnduranceEventHandler.onQuestStart):
 * ComboSystem.INSTANCE.startSession(playerId, questId);
 *
 * // AFTER (in ComboSystem):
 * public class ComboSystem implements QuestLifecycleListener {
 *     @Override
 *     public void onQuestStarted(QuestStarted event) {
 *         startSession(event.context().playerId(), event.questId());
 *     }
 *
 *     @Override
 *     public int getPriority() {
 *         return 1000; // Critical - runs first
 *     }
 * }
 *
 * // Registration (in CommonModEvents.onServerStarting):
 * QuestEventBus.INSTANCE.register(ComboSystem.INSTANCE);
 * }</pre>
 *
 * @since 1.0
 * @see QuestEventBus
 * @see QuestLifecycleListener
 * @see QuestLifecycleEvent
 */
@ParametersAreNonnullByDefault
package com.devmod.endurance.lifecycle;

import javax.annotation.ParametersAreNonnullByDefault;
