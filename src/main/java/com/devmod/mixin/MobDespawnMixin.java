package com.devmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;

/**
 * Mixin to prevent despawning of mobs spawned by our endurance quest system.
 *
 * Some modded mobs (like Graveyard's Acolyte) override isPersistenceRequired()
 * or have custom despawn logic that ignores setPersistenceRequired().
 *
 * This mixin checks for our endurance quest tag and cancels the despawn check entirely.
 */
@Mixin(Mob.class)
public abstract class MobDespawnMixin {

    @Inject(method = "checkDespawn", at = @At("HEAD"), cancellable = true)
    void devmod$preventEnduranceMobDespawn(CallbackInfo ci) {
        // In mixin context, 'this' is the target class (Mob), which extends Entity.
        // We cast to Mob (which this IS at runtime) to access getPersistentData() from Entity superclass.
        Mob self = (Mob) (Object) this;
        CompoundTag data = self.getPersistentData();

        // If this mob was spawned by our endurance quest system, never despawn
        if (data.contains("endurance_quest_id") || data.contains("endurance_arena_id")) {
            ci.cancel();
        }
    }
}
