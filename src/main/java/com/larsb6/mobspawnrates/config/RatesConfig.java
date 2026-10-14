package com.larsb6.mobspawnrates.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.larsb6.mobspawnrates.MobSpawnRates;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

/**
 * Stores the spawn and loot multipliers. The effective multiplier for a mob is
 * {@code global * perMob}. Only mobs that differ from the default are written to disk.
 */
public final class RatesConfig {
	public static final double MIN = 0.0;
	public static final double MAX = 20.0;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("mobspawnrates.json");

	private static Data data = new Data();

	private RatesConfig() {
	}

	public static final class Data {
		public double globalSpawn = 1.0;
		public double globalLoot = 1.0;
		public Map<String, MobRates> mobs = new TreeMap<>();
	}

	public static final class MobRates {
		public double spawn = 1.0;
		public double loot = 1.0;

		boolean isDefault() {
			return spawn == 1.0 && loot == 1.0;
		}
	}

	public enum Kind {
		SPAWN, LOOT
	}

	public static synchronized void load() {
		if (!Files.exists(FILE)) {
			data = new Data();
			save();
			return;
		}
		try (Reader reader = Files.newBufferedReader(FILE)) {
			Data loaded = GSON.fromJson(reader, Data.class);
			data = loaded != null ? loaded : new Data();
			if (data.mobs == null) {
				data.mobs = new TreeMap<>();
			} else {
				data.mobs = new TreeMap<>(data.mobs);
			}
			data.globalSpawn = clamp(data.globalSpawn);
			data.globalLoot = clamp(data.globalLoot);
			data.mobs.values().removeIf(r -> r == null);
			data.mobs.values().forEach(r -> {
				r.spawn = clamp(r.spawn);
				r.loot = clamp(r.loot);
			});
		} catch (Exception e) {
			MobSpawnRates.LOGGER.error("Failed to read {}, using defaults", FILE, e);
			data = new Data();
		}
	}

	public static synchronized void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(data, writer);
			}
		} catch (IOException e) {
			MobSpawnRates.LOGGER.error("Failed to write {}", FILE, e);
		}
	}

	public static double clamp(double value) {
		if (Double.isNaN(value)) {
			return 1.0;
		}
		return Math.round(Math.max(MIN, Math.min(MAX, value)) * 100.0) / 100.0;
	}

	private static String key(EntityType<?> type) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
	}

	public static synchronized double getGlobal(Kind kind) {
		return kind == Kind.SPAWN ? data.globalSpawn : data.globalLoot;
	}

	public static synchronized void setGlobal(Kind kind, double value) {
		if (kind == Kind.SPAWN) {
			data.globalSpawn = clamp(value);
		} else {
			data.globalLoot = clamp(value);
		}
		save();
	}

	public static synchronized double getMob(EntityType<?> type, Kind kind) {
		MobRates rates = data.mobs.get(key(type));
		if (rates == null) {
			return 1.0;
		}
		return kind == Kind.SPAWN ? rates.spawn : rates.loot;
	}

	public static synchronized void setMob(EntityType<?> type, Kind kind, double value) {
		String key = key(type);
		MobRates rates = data.mobs.computeIfAbsent(key, k -> new MobRates());
		if (kind == Kind.SPAWN) {
			rates.spawn = clamp(value);
		} else {
			rates.loot = clamp(value);
		}
		if (rates.isDefault()) {
			data.mobs.remove(key);
		}
		save();
	}

	public static synchronized void resetMob(EntityType<?> type) {
		data.mobs.remove(key(type));
		save();
	}

	public static synchronized void resetAll() {
		data = new Data();
		save();
	}

	/** Copy of the per-mob overrides as {@code id -> [spawn, loot]}. */
	public static synchronized Map<String, double[]> snapshotMobs() {
		Map<String, double[]> copy = new TreeMap<>();
		data.mobs.forEach((id, rates) -> copy.put(id, new double[]{rates.spawn, rates.loot}));
		return copy;
	}

	public static synchronized double effective(EntityType<?> type, Kind kind) {
		return getGlobal(kind) * getMob(type, kind);
	}

	/**
	 * Turns a (possibly fractional) multiplier into a whole number of repetitions,
	 * e.g. 2.5 gives 2 or 3 with equal chance and 0.3 gives 1 with a 30% chance.
	 */
	public static int roll(double multiplier, RandomSource random) {
		if (multiplier <= 0.0) {
			return 0;
		}
		int whole = (int) Math.floor(multiplier);
		double fraction = multiplier - whole;
		return whole + (fraction > 0.0 && random.nextDouble() < fraction ? 1 : 0);
	}
}
