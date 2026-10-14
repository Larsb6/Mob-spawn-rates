package com.larsb6.mobspawnrates;

import com.larsb6.mobspawnrates.config.RatesConfig;
import com.larsb6.mobspawnrates.gui.RatesMenu;
import com.larsb6.mobspawnrates.network.RatesNetworking;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class LootCommand {
	private LootCommand() {
	}

	/**
	 * Adds {@code /loot gui}, {@code /loot gui chest} and {@code /loot reload}.
	 * {@code /loot gui} opens the slider screen if the player's client has the mod, otherwise the chest GUI. Vanilla already owns {@code /loot},
	 * so Brigadier merges these into the existing node and the vanilla subcommands keep working.
	 * Both require operator permissions (level 2, same as vanilla /loot).
	 */
	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("loot")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("gui")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(context -> {
							ServerPlayer player = context.getSource().getPlayerOrException();
							if (RatesNetworking.canUseSliderScreen(player)) {
								RatesNetworking.sendSync(player, true);
							} else {
								RatesMenu.open(player);
							}
							return 1;
						})
						.then(Commands.literal("chest")
								.executes(context -> {
									RatesMenu.open(context.getSource().getPlayerOrException());
									return 1;
								})))
				.then(Commands.literal("reload")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(context -> {
							RatesConfig.load();
							context.getSource().sendSuccess(() -> Component.literal("Mob spawn & loot rates reloaded from config."), true);
							return 1;
						})));
	}
}
