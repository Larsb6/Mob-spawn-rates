package com.larsb6.mobspawnrates.paper;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class MobSpawnRatesPlugin extends JavaPlugin {
	private RatesStore store;
	private SliderBridge bridge;

	@Override
	public void onEnable() {
		store = new RatesStore(new File(getDataFolder(), "rates.yml"), getLogger());
		store.load();
		bridge = new SliderBridge(this, store);
		bridge.register();

		getServer().getPluginManager().registerEvents(new SpawnListener(this, store), this);
		getServer().getPluginManager().registerEvents(new LootListener(store, getLogger()), this);
		getServer().getPluginManager().registerEvents(new RatesGui.ClickListener(), this);

		getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
				event.registrar().register(LootCommand.build(this),
						"Mob spawn & loot rates GUI (/loot gui); other /loot arguments run the vanilla command"));
	}

	@Override
	public void onDisable() {
		if (store != null) {
			store.save();
		}
	}

	public RatesStore store() {
		return store;
	}

	public SliderBridge bridge() {
		return bridge;
	}
}
