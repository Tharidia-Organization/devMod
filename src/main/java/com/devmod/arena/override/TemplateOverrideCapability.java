package com.devmod.arena.override;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class TemplateOverrideCapability {

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
     * Data class for template override storage
     */
    public static class TemplateOverrideData {
        public static final com.mojang.serialization.Codec<TemplateOverrideData> CODEC =
                com.mojang.serialization.codecs.RecordCodecBuilder.create(instance ->
                        instance.group(
                                ResourceLocation.CODEC.optionalFieldOf("templateId").forGetter(d -> Optional.ofNullable(d.templateId)),
                                com.mojang.serialization.Codec.LONG.optionalFieldOf("expiryEpoch", 0L).forGetter(d -> d.expiryEpoch),
                                com.mojang.serialization.Codec.BOOL.optionalFieldOf("persistent", false).forGetter(d -> d.persistent)
                        ).apply(instance, (templateIdOpt, expiry, persistent) ->
                                new TemplateOverrideData(templateIdOpt.orElse(null), expiry, persistent)
                        )
                );

        @Nullable
        private ResourceLocation templateId;
        private long expiryEpoch;
        private boolean persistent;

        public TemplateOverrideData() {
            this.templateId = null;
            this.expiryEpoch = 0;
            this.persistent = false;
        }

        public TemplateOverrideData(@Nullable ResourceLocation templateId, long expiryEpoch, boolean persistent) {
            this.templateId = templateId;
            this.expiryEpoch = expiryEpoch;
            this.persistent = persistent;
        }

        public void setOverride(TemplateOverrideManager.TemplateOverride override) {
            this.templateId = override.templateId();
            this.expiryEpoch = override.expiry().getEpochSecond();
            this.persistent = override.persistent();
        }

        public Optional<TemplateOverrideManager.TemplateOverride> getOverride() {
            if (templateId == null) {
                return Optional.empty();
            }
            return Optional.of(new TemplateOverrideManager.TemplateOverride(
                    templateId,
                    java.time.Instant.ofEpochSecond(expiryEpoch),
                    persistent
            ));
        }

        public void clearOverride() {
            this.templateId = null;
            this.expiryEpoch = 0;
            this.persistent = false;
        }

        public boolean hasOverride() {
            return templateId != null;
        }
    }
}
