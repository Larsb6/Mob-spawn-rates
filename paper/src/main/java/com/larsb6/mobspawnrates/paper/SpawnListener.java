package com.larsb6.mobspawnrates.paper;

import com.larsb6.mobspawnrates.paper.RatesStore.Kind;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Applies the spawn multiplier to natural spawns and to the animals placed when new chunks generate.
 * Below 1x spawns are randomly cancelled; above 1x extra copies are spawned next to the mob.
 */
public final class SpawnListener implements Listener {
	private final Plugin plugin;
	private final RatesStore store;
	private boolean spawningExtras = false;

	public SpawnListener(Plugin plugin, RatesStore store) {
		this.plugin = plugin;
		this.store = store;
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onSpawn(CreatureSpawnEvent event) {
		if (spawningExtras || event.getSpawnReason() != SpawnReason.NATURAL || !(event.getEntity() instanceof Mob mob)) {
			return;
		}
		if (!apply(mob)) {
			event.setCancelled(true);
		}
	}

	/**
	 * Paper doesn't fire spawn events for animals created with a new chunk; they are already in the
	 * chunk when it loads, with spawn reason NATURAL. (Structure mobs such as villagers use DEFAULT.)
	 */
	@EventHandler(priority = EventPriority.HIGH)
	public void onNewChunk(ChunkLoadEvent event) {
		if (!event.isNewChunk()) {
			return;
		}
		for (Entity entity : event.getChunk().getEntities()) {
			if (entity instanceof Mob mob && mob.getEntitySpawnReason() == SpawnReason.NATURAL && !apply(mob)) {
				mob.remove();
			}
		}
	}

	/**
	 * Rolls the spawn multiplier for a mob and schedules any extra copies.
	 *
	 * @return false if the mob should not spawn
	 */
	private boolean apply(Mob mob) {
		double multiplier = store.effective(mob.getType(), Kind.SPAWN);
		if (multiplier == 1.0) {
			return true;
		}
		if (multiplier < 1.0) {
			return multiplier > 0.0 && ThreadLocalRandom.current().nextDouble() < multiplier;
		}
		int extras = RatesStore.roll(multiplier - 1.0);
		if (extras > 0) {
			scheduleExtras(mob.getType(), mob.getLocation().clone(), extras, 0);
		}
		return true;
	}

	private void scheduleExtras(EntityType type, Location location, int count, int attempt) {
		plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
			World world = location.getWorld();
			if (world == null) {
				return;
			}
			if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
				if (attempt < 10) {
					scheduleExtras(type, location, count, attempt + 1);
				}
				return;
			}
			spawningExtras = true;
			try {
				for (int i = 0; i < count; i++) {
					Location at = location.clone();
					at.setYaw(ThreadLocalRandom.current().nextFloat() * 360.0F);
					world.spawnEntity(at, type, SpawnReason.NATURAL);
				}
			} finally {
				spawningExtras = false;
			}
		}, 1L);
	}
}
