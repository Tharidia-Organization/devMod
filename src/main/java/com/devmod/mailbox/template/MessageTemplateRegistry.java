package com.devmod.mailbox.template;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.mailbox.MailboxManager;
import com.devmod.mailbox.MessageType;

/**
 * Registry for message templates.
 *
 * Provides:
 * - Standard templates for common messages (welcome, rewards, events, etc.)
 * - Custom template creation and management
 * - Placeholder substitution with {key} syntax
 * - Sound associations for send/receive events
 * - Template categories for organization
 */
public final class MessageTemplateRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageTemplateRegistry.class);

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([a-zA-Z_][a-zA-Z0-9_]*)\\}");

    public static final MessageTemplateRegistry INSTANCE = new MessageTemplateRegistry();

    // All registered templates by ID
    private final Map<String, MessageTemplate> templates = new ConcurrentHashMap<>();

    // Templates by category
    private final Map<TemplateCategory, List<String>> templatesByCategory = new ConcurrentHashMap<>();

    private MessageTemplateRegistry() {
        // Register standard templates on construction
        registerStandardTemplates();
    }

    // ============================================================================
    // TEMPLATE REGISTRATION
    // ============================================================================

    /**
     * Register a new template.
     *
     * @param template the template to register
     * @return true if registered, false if ID already exists
     */
    public boolean register(MessageTemplate template) {
        if (templates.containsKey(template.id())) {
            LOGGER.warn("[Templates] Template with ID '{}' already exists", template.id());
            return false;
        }

        templates.put(template.id(), template);
        templatesByCategory
            .computeIfAbsent(template.category(), k -> Collections.synchronizedList(new ArrayList<>()))
            .add(template.id());

        LOGGER.info("[Templates] Registered template: {} ({})", template.id(), template.category());
        return true;
    }

    /**
     * Update an existing template.
     *
     * @param template the updated template
     * @return true if updated, false if template doesn't exist
     */
    public boolean update(MessageTemplate template) {
        MessageTemplate existing = templates.get(template.id());
        if (existing == null) {
            return false;
        }

        // Remove from old category if changed
        if (existing.category() != template.category()) {
            List<String> oldCategoryList = templatesByCategory.get(existing.category());
            if (oldCategoryList != null) {
                oldCategoryList.remove(template.id());
            }
            templatesByCategory
                .computeIfAbsent(template.category(), k -> Collections.synchronizedList(new ArrayList<>()))
                .add(template.id());
        }

        templates.put(template.id(), template);
        LOGGER.info("[Templates] Updated template: {}", template.id());
        return true;
    }

    /**
     * Unregister a template.
     *
     * @param templateId the template ID
     * @return true if removed, false if not found
     */
    public boolean unregister(String templateId) {
        MessageTemplate removed = templates.remove(templateId);
        if (removed != null) {
            List<String> categoryList = templatesByCategory.get(removed.category());
            if (categoryList != null) {
                categoryList.remove(templateId);
            }
            LOGGER.info("[Templates] Unregistered template: {}", templateId);
            return true;
        }
        return false;
    }

    /**
     * Get a template by ID.
     */
    public Optional<MessageTemplate> get(String templateId) {
        return Optional.ofNullable(templates.get(templateId));
    }

    /**
     * Get all templates.
     */
    public List<MessageTemplate> getAll() {
        return new ArrayList<>(templates.values());
    }

    /**
     * Get templates by category.
     */
    public List<MessageTemplate> getByCategory(TemplateCategory category) {
        List<String> ids = templatesByCategory.get(category);
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }

        List<MessageTemplate> result = new ArrayList<>();
        for (String id : ids) {
            MessageTemplate template = templates.get(id);
            if (template != null) {
                result.add(template);
            }
        }
        return result;
    }

    /**
     * Check if a template exists.
     */
    public boolean exists(String templateId) {
        return templates.containsKey(templateId);
    }

    // ============================================================================
    // TEMPLATE USAGE
    // ============================================================================

    /**
     * Send a message using a template.
     *
     * @param templateId the template ID
     * @param recipientUuid the recipient's UUID
     * @param placeholders placeholder values to substitute
     * @return CompletableFuture with the message ID, or empty if template not found
     */
    public CompletableFuture<Optional<UUID>> sendFromTemplate(
            String templateId,
            UUID recipientUuid,
            Map<String, String> placeholders) {
        return sendFromTemplate(templateId, recipientUuid, placeholders, null);
    }

    /**
     * Send a message using a template with optional attachment.
     *
     * @param templateId the template ID
     * @param recipientUuid the recipient's UUID
     * @param placeholders placeholder values to substitute
     * @param attachmentJson optional attachment JSON
     * @return CompletableFuture with the message ID, or empty if template not found
     */
    public CompletableFuture<Optional<UUID>> sendFromTemplate(
            String templateId,
            UUID recipientUuid,
            Map<String, String> placeholders,
            @Nullable String attachmentJson) {

        MessageTemplate template = templates.get(templateId);
        if (template == null) {
            LOGGER.warn("[Templates] Template not found: {}", templateId);
            return CompletableFuture.completedFuture(Optional.empty());
        }

        String subject = Objects.requireNonNullElse(
            substitutePlaceholders(template.subject(), placeholders),
            ""
        );
        String body = Objects.requireNonNullElse(
            substitutePlaceholders(template.body(), placeholders),
            ""
        );

        return MailboxManager.INSTANCE.sendSystemMessage(
            recipientUuid,
            subject,
            body,
            attachmentJson,
            template.expiresAfter()
        ).thenApply(Optional::of);
    }

    /**
     * Send a template message to multiple recipients (broadcast).
     *
     * @param templateId the template ID
     * @param recipientUuids the recipients' UUIDs
     * @param placeholders placeholder values (applied to all)
     * @return CompletableFuture with list of message IDs
     */
    public CompletableFuture<List<UUID>> broadcastFromTemplate(
            String templateId,
            List<UUID> recipientUuids,
            Map<String, String> placeholders) {

        MessageTemplate template = templates.get(templateId);
        if (template == null) {
            LOGGER.warn("[Templates] Template not found for broadcast: {}", templateId);
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        String subject = Objects.requireNonNullElse(
            substitutePlaceholders(template.subject(), placeholders),
            ""
        );
        String body = Objects.requireNonNullElse(
            substitutePlaceholders(template.body(), placeholders),
            ""
        );

        List<CompletableFuture<UUID>> futures = new ArrayList<>();
        for (UUID recipient : recipientUuids) {
            futures.add(MailboxManager.INSTANCE.sendSystemMessage(
                recipient,
                subject,
                body,
                null,
                template.expiresAfter()
            ));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .toList());
    }

    /**
     * Preview a template with placeholders substituted.
     *
     * @param templateId the template ID
     * @param placeholders placeholder values
     * @return the preview, or empty if template not found
     */
    public Optional<TemplatePreview> preview(String templateId, Map<String, String> placeholders) {
        MessageTemplate template = templates.get(templateId);
        if (template == null) {
            return Optional.empty();
        }

        return Optional.of(new TemplatePreview(
            Objects.requireNonNullElse(substitutePlaceholders(template.subject(), placeholders), ""),
            Objects.requireNonNullElse(substitutePlaceholders(template.body(), placeholders), ""),
            template.type(),
            template.sendSound(),
            template.receiveSound()
        ));
    }

    /**
     * Substitute placeholders in text.
     */
    @Nullable
    public String substitutePlaceholders(@Nullable String text, @Nullable Map<String, String> placeholders) {
        if (text == null || placeholders == null || placeholders.isEmpty()) {
            return text;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = placeholders.getOrDefault(key, matcher.group(0));
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Extract placeholder keys from template text.
     */
    public static List<String> extractPlaceholders(@Nullable String text) {
        if (text == null) {
            return new ArrayList<>();
        }

        Set<String> keys = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        while (matcher.find()) {
            String key = matcher.group(1);
            keys.add(key);
        }
        return new ArrayList<>(keys);
    }

    // ============================================================================
    // STANDARD TEMPLATES
    // ============================================================================

    private void registerStandardTemplates() {
        // ========== WELCOME CATEGORY ==========
        register(MessageTemplate.builder("welcome.new_player")
            .category(TemplateCategory.WELCOME)
            .subject("Benvenuto su {server_name}!")
            .body("""
                Ciao {player_name}!

                Benvenuto su {server_name}! Siamo felici di averti con noi.

                Ecco alcune informazioni utili per iniziare:
                - Usa /help per vedere i comandi disponibili
                - Visita lo spawn per trovare i vari portali
                - Unisciti al nostro Discord: {discord_link}

                Buon divertimento!
                Lo Staff di {server_name}
                """)
            .type(MessageType.SYSTEM)
            .receiveSound(MessageSound.WELCOME_CHIME)
            .expiresAfter(Duration.ofDays(30))
            .build());

        register(MessageTemplate.builder("welcome.returning_player")
            .category(TemplateCategory.WELCOME)
            .subject("Bentornato, {player_name}!")
            .body("""
                Ciao {player_name}!

                È passato un po' di tempo dall'ultima volta che ti abbiamo visto.
                Ecco cosa è cambiato da allora:

                {changelog}

                Buon ritorno!
                """)
            .type(MessageType.SYSTEM)
            .receiveSound(MessageSound.WELCOME_CHIME)
            .expiresAfter(Duration.ofDays(7))
            .build());

        // ========== REWARD CATEGORY ==========
        register(MessageTemplate.builder("reward.daily_login")
            .category(TemplateCategory.REWARD)
            .subject("Ricompensa Giornaliera!")
            .body("""
                Complimenti {player_name}!

                Hai effettuato il login per {streak_days} giorni consecutivi!

                La tua ricompensa di oggi:
                {reward_description}

                Continua così per sbloccare ricompense ancora migliori!
                """)
            .type(MessageType.REWARD)
            .sendSound(MessageSound.REWARD_SENT)
            .receiveSound(MessageSound.REWARD_RECEIVED)
            .expiresAfter(Duration.ofDays(3))
            .build());

        register(MessageTemplate.builder("reward.achievement")
            .category(TemplateCategory.REWARD)
            .subject("Achievement Sbloccato: {achievement_name}")
            .body("""
                Fantastico {player_name}!

                Hai sbloccato l'achievement: {achievement_name}

                {achievement_description}

                Ricompensa: {reward_description}
                """)
            .type(MessageType.REWARD)
            .sendSound(MessageSound.ACHIEVEMENT)
            .receiveSound(MessageSound.ACHIEVEMENT)
            .expiresAfter(Duration.ofDays(7))
            .build());

        register(MessageTemplate.builder("reward.event_participation")
            .category(TemplateCategory.REWARD)
            .subject("Grazie per aver partecipato a {event_name}!")
            .body("""
                Caro {player_name},

                Grazie per aver partecipato all'evento "{event_name}"!

                La tua posizione: {position}
                Punti ottenuti: {points}

                Ecco la tua ricompensa:
                {reward_description}

                Alla prossima!
                """)
            .type(MessageType.REWARD)
            .receiveSound(MessageSound.REWARD_RECEIVED)
            .expiresAfter(Duration.ofDays(14))
            .build());

        // ========== EVENT CATEGORY ==========
        register(MessageTemplate.builder("event.announcement")
            .category(TemplateCategory.EVENT)
            .subject("[EVENTO] {event_name}")
            .body("""
                Attenzione {player_name}!

                Un nuovo evento sta per iniziare!

                {event_name}
                Inizio: {start_time}
                Fine: {end_time}

                {event_description}

                Non mancare!
                """)
            .type(MessageType.ADMIN)
            .sendSound(MessageSound.EVENT_ANNOUNCEMENT)
            .receiveSound(MessageSound.EVENT_ANNOUNCEMENT)
            .expiresAfter(Duration.ofDays(1))
            .build());

        register(MessageTemplate.builder("event.reminder")
            .category(TemplateCategory.EVENT)
            .subject("[PROMEMORIA] {event_name} tra poco!")
            .body("""
                Hey {player_name}!

                L'evento "{event_name}" inizia tra {time_remaining}!

                Preparati e non perdertelo!
                """)
            .type(MessageType.SYSTEM)
            .receiveSound(MessageSound.NOTIFICATION)
            .expiresAfter(Duration.ofHours(2))
            .build());

        register(MessageTemplate.builder("event.ended")
            .category(TemplateCategory.EVENT)
            .subject("[EVENTO CONCLUSO] {event_name}")
            .body("""
                L'evento "{event_name}" si è concluso!

                Classifica finale:
                {leaderboard}

                La tua posizione: {player_position}

                Grazie a tutti i partecipanti!
                """)
            .type(MessageType.SYSTEM)
            .expiresAfter(Duration.ofDays(3))
            .build());

        // ========== ADMIN CATEGORY ==========
        register(MessageTemplate.builder("admin.warning")
            .category(TemplateCategory.ADMIN)
            .subject("[AVVISO] Attenzione!")
            .body("""
                {player_name},

                Questo è un avviso ufficiale dallo staff.

                Motivo: {reason}

                Ti preghiamo di rispettare le regole del server.
                Ulteriori violazioni potrebbero comportare sanzioni più severe.

                Staff di {server_name}
                """)
            .type(MessageType.ADMIN)
            .sendSound(MessageSound.WARNING)
            .receiveSound(MessageSound.WARNING)
            .expiresAfter(Duration.ofDays(30))
            .build());

        register(MessageTemplate.builder("admin.maintenance")
            .category(TemplateCategory.ADMIN)
            .subject("[MANUTENZIONE] Server offline tra {time_remaining}")
            .body("""
                Attenzione {player_name},

                Il server sarà offline per manutenzione programmata.

                Inizio: {start_time}
                Durata stimata: {duration}

                Motivo: {reason}

                Ci scusiamo per il disagio.
                """)
            .type(MessageType.ADMIN)
            .sendSound(MessageSound.ALERT)
            .receiveSound(MessageSound.ALERT)
            .expiresAfter(Duration.ofDays(1))
            .build());

        register(MessageTemplate.builder("system.admin_alert")
            .category(TemplateCategory.ADMIN)
            .subject("[ALERT] {alert_type} - {severity}")
            .body("""
                System Alert

                Type: {alert_type}
                Severity: {severity}

                Message:
                {message}

                Details:
                {details}

                This is an automated alert from the DevMod system.
                """)
            .type(MessageType.ADMIN)
            .sendSound(MessageSound.ALERT)
            .receiveSound(MessageSound.ALERT)
            .expiresAfter(Duration.ofDays(7))
            .build());

        register(MessageTemplate.builder("admin.broadcast")
            .category(TemplateCategory.ADMIN)
            .subject("[ANNUNCIO] {title}")
            .body("""
                {content}

                -- Staff di {server_name}
                """)
            .type(MessageType.ADMIN)
            .receiveSound(MessageSound.NOTIFICATION)
            .expiresAfter(Duration.ofDays(7))
            .build());

        // ========== SOCIAL CATEGORY ==========
        register(MessageTemplate.builder("social.friend_request")
            .category(TemplateCategory.SOCIAL)
            .subject("{sender_name} vuole essere tuo amico!")
            .body("""
                Ciao {player_name}!

                {sender_name} ti ha inviato una richiesta di amicizia.

                Usa /friend accept {sender_name} per accettare
                oppure /friend deny {sender_name} per rifiutare.
                """)
            .type(MessageType.PLAYER)
            .receiveSound(MessageSound.SOCIAL)
            .expiresAfter(Duration.ofDays(7))
            .build());

        register(MessageTemplate.builder("social.party_invite")
            .category(TemplateCategory.SOCIAL)
            .subject("Invito al party di {leader_name}")
            .body("""
                {player_name}, sei stato invitato al party di {leader_name}!

                Membri attuali: {member_count}

                Usa /party accept per unirti!
                """)
            .type(MessageType.PLAYER)
            .receiveSound(MessageSound.SOCIAL)
            .expiresAfter(Duration.ofMinutes(5))
            .build());

        register(MessageTemplate.builder("social.party_update")
            .category(TemplateCategory.SOCIAL)
            .subject("Party Update: {update_type}")
            .body("""
                {player_name},

                {details}

                {action_hint}
                """)
            .type(MessageType.PLAYER)
            .receiveSound(MessageSound.SOCIAL)
            .expiresAfter(Duration.ofDays(1))
            .build());

        register(MessageTemplate.builder("social.guild_invite")
            .category(TemplateCategory.SOCIAL)
            .subject("Invito alla gilda [{guild_tag}] {guild_name}")
            .body("""
                Ciao {player_name}!

                Sei stato invitato a unirti alla gilda "{guild_name}" [{guild_tag}]

                Invitato da: {inviter_name}
                Membri: {member_count}
                Livello: {guild_level}

                Usa /guild accept {guild_name} per accettare.
                """)
            .type(MessageType.PLAYER)
            .receiveSound(MessageSound.SOCIAL)
            .expiresAfter(Duration.ofDays(3))
            .build());

        // ========== TRANSACTION CATEGORY ==========
        register(MessageTemplate.builder("transaction.purchase_confirm")
            .category(TemplateCategory.TRANSACTION)
            .subject("Conferma Acquisto: {item_name}")
            .body("""
                Acquisto completato!

                Articolo: {item_name}
                Quantità: {quantity}
                Prezzo: {price} {currency}

                Saldo rimanente: {balance} {currency}

                Grazie per l'acquisto!
                """)
            .type(MessageType.SYSTEM)
            .sendSound(MessageSound.TRANSACTION)
            .receiveSound(MessageSound.TRANSACTION)
            .expiresAfter(Duration.ofDays(30))
            .build());

        register(MessageTemplate.builder("transaction.received_payment")
            .category(TemplateCategory.TRANSACTION)
            .subject("Hai ricevuto {amount} {currency}!")
            .body("""
                {player_name}, hai ricevuto un pagamento!

                Da: {sender_name}
                Importo: {amount} {currency}
                Motivo: {reason}

                Nuovo saldo: {balance} {currency}
                """)
            .type(MessageType.SYSTEM)
            .receiveSound(MessageSound.COINS)
            .expiresAfter(Duration.ofDays(14))
            .build());

        // ========== MODERATION CATEGORY ==========
        register(MessageTemplate.builder("moderation.report_received")
            .category(TemplateCategory.MODERATION)
            .subject("Report Ricevuto #{report_id}")
            .body("""
                Grazie per il tuo report, {player_name}.

                ID Report: #{report_id}
                Giocatore segnalato: {reported_player}
                Motivo: {reason}

                Il nostro staff esaminerà il report il prima possibile.
                Ti terremo aggiornato sull'esito.
                """)
            .type(MessageType.SYSTEM)
            .expiresAfter(Duration.ofDays(7))
            .build());

        register(MessageTemplate.builder("moderation.report_resolved")
            .category(TemplateCategory.MODERATION)
            .subject("Report #{report_id} - Esito")
            .body("""
                Ciao {player_name},

                Il tuo report #{report_id} è stato esaminato.

                Esito: {outcome}

                {additional_info}

                Grazie per aver contribuito a mantenere il server sicuro!
                """)
            .type(MessageType.SYSTEM)
            .expiresAfter(Duration.ofDays(14))
            .build());

        LOGGER.info("[Templates] Registered {} standard templates", templates.size());
    }

    // ============================================================================
    // BUILDER FOR CUSTOM TEMPLATES
    // ============================================================================

    /**
     * Create a template builder for custom templates.
     */
    public static MessageTemplate.Builder builder(String id) {
        return MessageTemplate.builder(id);
    }

    // ============================================================================
    // TYPES
    // ============================================================================

    /**
     * Template categories for organization.
     */
    public enum TemplateCategory {
        WELCOME("Welcome & Onboarding"),
        REWARD("Rewards & Achievements"),
        EVENT("Events & Announcements"),
        ADMIN("Admin & Staff"),
        SOCIAL("Social & Friends"),
        TRANSACTION("Transactions & Economy"),
        MODERATION("Moderation & Reports"),
        CUSTOM("Custom Templates");

        private final String displayName;

        TemplateCategory(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Sound effects for messages.
     */
    public enum MessageSound {
        // General
        NONE("none", null),
        NOTIFICATION("notification", "minecraft:block.note_block.pling"),
        ALERT("alert", "minecraft:block.note_block.bell"),
        WARNING("warning", "minecraft:entity.experience_orb.pickup"),

        // Welcome
        WELCOME_CHIME("welcome_chime", "minecraft:entity.player.levelup"),

        // Rewards
        REWARD_SENT("reward_sent", "minecraft:entity.arrow.hit_player"),
        REWARD_RECEIVED("reward_received", "minecraft:entity.player.levelup"),
        ACHIEVEMENT("achievement", "minecraft:ui.toast.challenge_complete"),
        COINS("coins", "minecraft:block.amethyst_block.chime"),

        // Social
        SOCIAL("social", "minecraft:entity.experience_orb.pickup"),

        // Events
        EVENT_ANNOUNCEMENT("event", "minecraft:block.bell.use"),

        // Transactions
        TRANSACTION("transaction", "minecraft:entity.villager.yes"),

        // Custom (for user-defined sounds)
        CUSTOM("custom", null);

        private final String id;
        @Nullable
        private final String minecraftSound;

        MessageSound(String id, @Nullable String minecraftSound) {
            this.id = id;
            this.minecraftSound = minecraftSound;
        }

        public String getId() {
            return id;
        }

        @Nullable
        public String getMinecraftSound() {
            return minecraftSound;
        }

        public static MessageSound fromId(String id) {
            for (MessageSound sound : values()) {
                if (sound.id.equals(id)) {
                    return sound;
                }
            }
            return NONE;
        }
    }

    /**
     * Preview result for template.
     */
    public record TemplatePreview(
        String subject,
        String body,
        MessageType type,
        @Nullable MessageSound sendSound,
        @Nullable MessageSound receiveSound
    ) {}
}
