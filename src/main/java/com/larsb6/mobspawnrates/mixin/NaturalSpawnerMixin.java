package com.larsb6.mobspawnrates.mixin;

import com.larsb6.mobspawnrates.config.RatesConfig;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerMixin {
	// ---- Regular (tick based) natural spawning ----

	/** Rate below 1: randomly reject the spawn before the mob is initialised. */
	@WrapOperation(
			method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/NaturalSpawner;isValidPositionForMob(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Mob;D)Z")
	)
	private static boolean mobspawnrates$reduceNatural(ServerLevel level, Mob mob, double distance, Operation<Boolean> original) {
		return original.call(level, mob, distance) && mobspawnrates$allow(mob, level);
	}

	/** Rate above 1: spawn extra copies next to the mob that just spawned. */
	@WrapOperation(
			method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)V")
	)
	private static void mobspawnrates$extraNatural(ServerLevel level, Entity entity, Operation<Void> original) {
		original.call(level, entity);
		mobspawnrates$spawnExtras(level, entity, EntitySpawnReason.NATURAL);
	}

	// ---- Mobs placed during world generation (animals in new chunks) ----

	@WrapOperation(
			method = "spawnMobsForChunkGeneration",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;checkSpawnRules(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/world/entity/EntitySpawnReason;)Z")
	)
	private static boolean mobspawnrates$reduceChunkGen(Mob mob, LevelAccessor level, EntitySpawnReason reason, Operation<Boolean> original) {
		return original.call(mob, level, reason) && mobspawnrates$allow(mob, level);
	}

	@WrapOperation(
			method = "spawnMobsForChunkGeneration",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/ServerLevelAccessor;addFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)V")
	)
	private static void mobspawnrates$extraChunkGen(ServerLevelAccessor level, Entity entity, Operation<Void> original) {
		original.call(level, entity);
		mobspawnrates$spawnExtras(level, entity, EntitySpawnReason.CHUNK_GENERATION);
	}

	// ---- Helpers ----

	@Unique
	private static boolean mobspawnrates$allow(Mob mob, LevelReader level) {
		double multiplier = RatesConfig.effective(mob.getType(), RatesConfig.Kind.SPAWN);
		return multiplier >= 1.0 || (multiplier > 0.0 && mob.getRandom().nextDouble() < multiplier);
	}

	@Unique
	private static void mobspawnrates$spawnExtras(ServerLevelAccessor level, Entity entity, EntitySpawnReason reason) {
		double multiplier = RatesConfig.effective(entity.getType(), RatesConfig.Kind.SPAWN);
		if (multiplier <= 1.0) {
			return;
		}
		int extras = RatesConfig.roll(multiplier - 1.0, entity.getRandom());
		for (int i = 0; i < extras; i++) {
			Entity created;
			try {
				created = entity.getType().create(level.getLevel(), reason);
			} catch (Exception e) {
				return;
			}
			if (!(created instanceof Mob extra)) {
				return;
			}
			extra.snapTo(entity.getX(), entity.getY(), entity.getZ(), level.getRandom().nextFloat() * 360.0F, 0.0F);
			if (!extra.checkSpawnObstruction(level)) {
				continue;
			}
			extra.finalizeSpawn(level, level.getCurrentDifficultyAt(extra.blockPosition()), reason, null);
			level.addFreshEntityWithPassengers(extra);
		}
	}
}
