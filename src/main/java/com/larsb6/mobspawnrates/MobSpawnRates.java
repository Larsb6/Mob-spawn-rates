package com.larsb6.mobspawnrates;

import com.larsb6.mobspawnrates.config.RatesConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MobSpawnRates implements ModInitializer {
	public static final String MOD_ID = "mobspawnrates";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		RatesConfig.load();
		ServerLifecycleEvents.SERVER_STARTING.register(server -> RatesConfig.load());
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> LootCommand.register(dispatcher));
	}
}
