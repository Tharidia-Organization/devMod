package com.devmod.arena.override;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import javax.annotation.Nullable;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * DD29: Unified template override attachment for NeoForge 1.21+ Data Attachments.
 *
 * <p>Consolidates:
 * <ul>
 *   <li>TemplateOverrideCapability - NBT serialization</li>
 *   <li>ForceTemplateCapability - Session tracking</li>
 * </ul>
 *
 * <p>Provides persistence of template overrides across player relog.
 * Attached to players and serialized with player data.
 */
public class TemplateOverrideAttachment {

    public static final String MODID = "devmod";

    /** Deferred register for attachment types */
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(Objects.requireNonNull(NeoForgeRegistries.ATTACHMENT_TYPES), MODID);

    /** Resource location for attachment registration */
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(MODID, "template_override");

    /** Attachment type for template override storage */
    public static final Supplier<AttachmentType<TemplateOverrideData>> TEMPLATE_OVERRIDE =
            ATTACHMENT_TYPES.register("template_override", () ->
                    AttachmentType.builder(TemplateOverrideData::new)
                            .serialize(Objects.requireNonNull(TemplateOverrideData.CODEC))
                            .copyOnDeath()
                            .build()
            );

    /**
     * Data class for template override storage with full TemplateOverride support.
     */
    public static class TemplateOverrideData {
        public static final com.mojang.serialization.Codec<TemplateOverrideData> CODEC =
                com.mojang.serialization.codecs.RecordCodecBuilder.create(instance ->
                        instance.group(
                                com.mojang.serialization.Codec.STRING.optionalFieldOf("templateId").forGetter(d -> Optional.ofNullable(d.templateId)),
                                com.mojang.serialization.Codec.STRING.optionalFieldOf("policyId").forGetter(d -> Optional.ofNullable(d.policyId)),
                                com.mojang.serialization.Codec.STRING.optionalFieldOf("scope", "PLAYER").forGetter(d -> d.scope),
                                com.mojang.serialization.Codec.LONG.optionalFieldOf("createdAtEpoch", 0L).forGetter(d -> d.createdAtEpoch),
                                com.mojang.serialization.Codec.LONG.optionalFieldOf("expiresAtEpoch", 0L).forGetter(d -> d.expiresAtEpoch),
                                com.mojang.serialization.Codec.STRING.optionalFieldOf("source", "attachment").forGetter(d -> d.source)
                        ).apply(instance, (templateIdOpt, policyIdOpt, scope, createdAt, expiresAt, source) ->
                                new TemplateOverrideData(
                                        templateIdOpt.orElse(null),
                                        policyIdOpt.orElse(null),
                                        scope,
                                        createdAt,
                                        expiresAt,
                                        source
                                )
                        )
                );

        @Nullable
        private String templateId;
        @Nullable
        private String policyId;
        private String scope;
        private long createdAtEpoch;
        private long expiresAtEpoch;
        private String source;

        public TemplateOverrideData() {
            this.templateId = null;
            this.policyId = null;
            this.scope = "PLAYER";
            this.createdAtEpoch = 0;
            this.expiresAtEpoch = 0;
            this.source = "attachment";
        }

        public TemplateOverrideData(
                @Nullable String templateId,
                @Nullable String policyId,
                String scope,
                long createdAtEpoch,
                long expiresAtEpoch,
                String source) {
            this.templateId = templateId;
            this.policyId = policyId;
            this.scope = scope;
            this.createdAtEpoch = createdAtEpoch;
            this.expiresAtEpoch = expiresAtEpoch;
            this.source = source;
        }

        /**
         * Sets override from a TemplateOverride record.
         */
        public void setOverride(TemplateOverride override) {
            this.templateId = override.templateId();
            this.policyId = override.policyId();
            this.scope = override.scope().name();
            this.createdAtEpoch = override.createdAt().getEpochSecond();
            var expiresAt = override.expiresAt();
            this.expiresAtEpoch = expiresAt != null ? expiresAt.getEpochSecond() : 0;
            this.source = override.source();
        }

        /**
         * Gets the stored override as a TemplateOverride record.
         */
        public Optional<TemplateOverride> getOverride() {
            if (templateId == null) {
                return Optional.empty();
            }

            OverrideScope overrideScope;
            try {
                overrideScope = OverrideScope.valueOf(scope);
            } catch (IllegalArgumentException e) {
                overrideScope = OverrideScope.PLAYER;
            }

            return Optional.of(new TemplateOverride(
                    templateId,
                    policyId,
                    overrideScope,
                    Instant.ofEpochSecond(createdAtEpoch),
                    expiresAtEpoch > 0 ? Instant.ofEpochSecond(expiresAtEpoch) : null,
                    source
            ));
        }

        /**
         * Clears the stored override.
         */
        public void clearOverride() {
            this.templateId = null;
            this.policyId = null;
            this.scope = "PLAYER";
            this.createdAtEpoch = 0;
            this.expiresAtEpoch = 0;
            this.source = "attachment";
        }

        /**
         * Checks if an override is stored.
         */
        public boolean hasOverride() {
            return templateId != null;
        }

        /**
         * Checks if the stored override is expired.
         */
        public boolean isExpired() {
            if (expiresAtEpoch <= 0) {
                return false; // No expiry set
            }
            return Instant.now().getEpochSecond() > expiresAtEpoch;
        }

        /**
         * Gets remaining seconds until expiry.
         */
        public long getRemainingSeconds() {
            if (expiresAtEpoch <= 0) {
                return Long.MAX_VALUE; // No expiry
            }
            return Math.max(0, expiresAtEpoch - Instant.now().getEpochSecond());
        }
    }
}
