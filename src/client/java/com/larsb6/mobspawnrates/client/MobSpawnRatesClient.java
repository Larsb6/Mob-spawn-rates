package com.larsb6.mobspawnrates.client;

import com.larsb6.mobspawnrates.network.RatesNetworking.SyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class MobSpawnRatesClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(SyncPayload.TYPE, (payload, context) -> {
			if (payload.open()) {
				context.client().setScreen(new RatesScreen(payload));
			} else if (context.client().screen instanceof RatesScreen screen) {
				screen.applySync(payload);
			}
		});
	}
}
