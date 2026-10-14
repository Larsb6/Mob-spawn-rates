package com.larsb6.mobspawnrates.paper;

import com.larsb6.mobspawnrates.paper.RatesStore.Kind;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Applies the loot multiplier: the mob's loot table is rolled that many times.
 * 0x removes the drops entirely.
 */
public final class LootListener implements Listener {
	private final RatesStore store;
	private final Logger logger;
	private final Random random = new Random();
	private boolean warned = false;

	public LootListener(RatesStore store, Logger logger) {
		this.store = store;
		this.logger = logger;
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onDeath(EntityDeathEvent event) {
		LivingEntity entity = event.getEntity();
		if (entity instanceof Player) {
			return;
		}
		double multiplier = store.effective(entity.getType(), Kind.LOOT);
		if (multiplier == 1.0) {
			return;
		}
		int rolls = RatesStore.roll(multiplier);
		List<ItemStack> drops = event.getDrops();
		if (rolls == 0) {
			drops.clear();
			return;
		}
		if (rolls == 1) {
			return;
		}
		List<ItemStack> original = new ArrayList<>();
		for (ItemStack drop : drops) {
			original.add(drop.clone());
		}
		for (int i = 1; i < rolls; i++) {
			Collection<ItemStack> extra = rollLootTable(entity);
			if (extra != null) {
				drops.addAll(extra);
			} else {
				// No usable loot table: repeat the drops this death produced.
				for (ItemStack drop : original) {
					drops.add(drop.clone());
				}
			}
		}
	}

	private Collection<ItemStack> rollLootTable(LivingEntity entity) {
		if (!(entity instanceof Mob mob)) {
			return null;
		}
		LootTable table = mob.getLootTable();
		if (table == null) {
			return null;
		}
		try {
			Player killer = entity.getKiller();
			LootContext.Builder context = new LootContext.Builder(entity.getLocation()).lootedEntity(entity);
			if (killer != null) {
				context.killer(killer);
			}
			return table.populateLoot(random, context.build());
		} catch (RuntimeException e) {
			if (!warned) {
				warned = true;
				logger.log(Level.WARNING, "Could not roll loot table for " + entity.getType() + ", repeating drops instead", e);
			}
			return null;
		}
	}
}
