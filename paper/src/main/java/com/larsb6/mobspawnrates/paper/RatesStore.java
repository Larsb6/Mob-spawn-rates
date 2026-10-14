package com.larsb6.mobspawnrates.paper;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Stores the spawn and loot multipliers in {@code plugins/MobSpawnRates/rates.yml}.
 * The effective multiplier for a mob is {@code global * perMob}.
 */
public final class RatesStore {
	public static final double MIN = 0.0;
	public static final double MAX = 20.0;

	public enum Kind {
		SPAWN, LOOT
	}

	private final File file;
	private final Logger logger;
	private double globalSpawn = 1.0;
	private double globalLoot = 1.0;
	/** Mob key (e.g. {@code minecraft:zombie}) -> [spawn, loot]. Only non-default mobs are stored. */
	private final Map<String, double[]> mobs = new TreeMap<>();

	public RatesStore(File file, Logger logger) {
		this.file = file;
		this.logger = logger;
	}

	public synchronized void load() {
		globalSpawn = 1.0;
		globalLoot = 1.0;
		mobs.clear();
		if (!file.exists()) {
			save();
			return;
		}
		YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
		globalSpawn = clamp(yaml.getDouble("global.spawn", 1.0));
		globalLoot = clamp(yaml.getDouble("global.loot", 1.0));
		ConfigurationSection section = yaml.getConfigurationSection("mobs");
		if (section != null) {
			for (String key : section.getKeys(false)) {
				double spawn = clamp(section.getDouble(key + ".spawn", 1.0));
				double loot = clamp(section.getDouble(key + ".loot", 1.0));
				if (spawn != 1.0 || loot != 1.0) {
					mobs.put(normalize(key), new double[]{spawn, loot});
				}
			}
		}
	}

	public synchronized void save() {
		YamlConfiguration yaml = new YamlConfiguration();
		yaml.options().setHeader(java.util.List.of(
				"Mob spawn & loot rate multipliers (0 - 20). Edit in-game with /loot gui.",
				"Effective rate of a mob = global * mob. Mob keys look like 'zombie' or 'minecraft:zombie'."));
		yaml.set("global.spawn", globalSpawn);
		yaml.set("global.loot", globalLoot);
		yaml.createSection("mobs");
		mobs.forEach((key, rates) -> {
			String path = "mobs." + key.replace("minecraft:", "");
			yaml.set(path + ".spawn", rates[0]);
			yaml.set(path + ".loot", rates[1]);
		});
		try {
			file.getParentFile().mkdirs();
			yaml.save(file);
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Failed to save " + file, e);
		}
	}

	private static String normalize(String key) {
		return key.contains(":") ? key : "minecraft:" + key;
	}

	public static String key(EntityType type) {
		return type.getKey().toString();
	}

	public static double clamp(double value) {
		if (Double.isNaN(value)) {
			return 1.0;
		}
		return Math.round(Math.max(MIN, Math.min(MAX, value)) * 100.0) / 100.0;
	}

	public synchronized double getGlobal(Kind kind) {
		return kind == Kind.SPAWN ? globalSpawn : globalLoot;
	}

	public synchronized void setGlobal(Kind kind, double value) {
		if (kind == Kind.SPAWN) {
			globalSpawn = clamp(value);
		} else {
			globalLoot = clamp(value);
		}
		save();
	}

	public synchronized double getMob(EntityType type, Kind kind) {
		double[] rates = mobs.get(key(type));
		return rates == null ? 1.0 : rates[kind.ordinal()];
	}

	public synchronized void setMob(EntityType type, Kind kind, double value) {
		String key = key(type);
		double[] rates = mobs.computeIfAbsent(key, k -> new double[]{1.0, 1.0});
		rates[kind.ordinal()] = clamp(value);
		if (rates[0] == 1.0 && rates[1] == 1.0) {
			mobs.remove(key);
		}
		save();
	}

	public synchronized void resetMob(EntityType type) {
		mobs.remove(key(type));
		save();
	}

	public synchronized void resetAll() {
		globalSpawn = 1.0;
		globalLoot = 1.0;
		mobs.clear();
		save();
	}

	public synchronized Map<String, double[]> snapshotMobs() {
		Map<String, double[]> copy = new TreeMap<>();
		mobs.forEach((k, v) -> copy.put(k, v.clone()));
		return copy;
	}

	public synchronized double effective(EntityType type, Kind kind) {
		return getGlobal(kind) * getMob(type, kind);
	}

	/**
	 * Turns a (possibly fractional) multiplier into a whole number of repetitions,
	 * e.g. 2.5 gives 2 or 3 with equal chance and 0.3 gives 1 with a 30% chance.
	 */
	public static int roll(double multiplier) {
		if (multiplier <= 0.0) {
			return 0;
		}
		int whole = (int) Math.floor(multiplier);
		double fraction = multiplier - whole;
		return whole + (fraction > 0.0 && ThreadLocalRandom.current().nextDouble() < fraction ? 1 : 0);
	}
}
