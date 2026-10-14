package com.larsb6.mobspawnrates.mixin;

import com.larsb6.mobspawnrates.config.RatesConfig;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	/**
	 * Rolls the mob's loot table 0..n times depending on the configured loot multiplier.
	 */
	@WrapOperation(
			method = "dropAllDeathLoot",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;dropFromLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;Z)V")
	)
	private void mobspawnrates$multiplyLoot(LivingEntity entity, ServerLevel level, DamageSource source, boolean byPlayer, Operation<Void> original) {
		double multiplier = RatesConfig.effective(entity.getType(), RatesConfig.Kind.LOOT);
		if (multiplier == 1.0) {
			original.call(entity, level, source, byPlayer);
			return;
		}
		int rolls = RatesConfig.roll(multiplier, entity.getRandom());
		for (int i = 0; i < rolls; i++) {
			original.call(entity, level, source, byPlayer);
		}
	}
}
