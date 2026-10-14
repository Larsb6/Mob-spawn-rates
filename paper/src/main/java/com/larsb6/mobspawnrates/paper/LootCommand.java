package com.larsb6.mobspawnrates.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /loot gui}, {@code /loot gui chest} and {@code /loot reload}. Paper replaces the vanilla
 * {@code /loot} command when a plugin registers the same name, so every other {@code /loot ...}
 * is forwarded to {@code /minecraft:loot} and keeps working.
 */
public final class LootCommand {
	private static final String VANILLA_PERMISSION = "minecraft.command.loot";

	private LootCommand() {
	}

	public static LiteralCommandNode<CommandSourceStack> build(MobSpawnRatesPlugin plugin) {
		return Commands.literal("loot")
				.requires(source -> isAdmin(source.getSender()) || source.getSender().hasPermission(VANILLA_PERMISSION))
				.then(Commands.literal("gui")
						.requires(source -> isAdmin(source.getSender()))
						.executes(context -> {
							if (!(context.getSource().getExecutor() instanceof Player player)) {
								context.getSource().getSender().sendMessage(Component.text("A player is required to run this command here", NamedTextColor.RED));
								return 0;
							}
							if (plugin.bridge().canUseSliderScreen(player)) {
								plugin.bridge().sendSync(player, true);
							} else {
								RatesGui.open(player, plugin.store());
							}
							return Command.SINGLE_SUCCESS;
						})
						.then(Commands.literal("chest")
								.executes(context -> {
									if (!(context.getSource().getExecutor() instanceof Player player)) {
										context.getSource().getSender().sendMessage(Component.text("A player is required to run this command here", NamedTextColor.RED));
										return 0;
									}
									RatesGui.open(player, plugin.store());
									return Command.SINGLE_SUCCESS;
								})))
				.then(Commands.literal("reload")
						.requires(source -> isAdmin(source.getSender()))
						.executes(context -> {
							plugin.store().load();
							context.getSource().getSender().sendMessage(Component.text("Mob spawn & loot rates reloaded from rates.yml.", NamedTextColor.GREEN));
							return Command.SINGLE_SUCCESS;
						}))
				.then(Commands.argument("vanilla", StringArgumentType.greedyString())
						.requires(source -> source.getSender().hasPermission(VANILLA_PERMISSION))
						.suggests((context, builder) -> {
							String remaining = builder.getRemainingLowerCase();
							for (String option : new String[]{"spawn", "give", "insert", "replace"}) {
								if (option.startsWith(remaining)) {
									builder.suggest(option);
								}
							}
							return builder.buildFuture();
						})
						.executes(context -> {
							CommandSender sender = context.getSource().getSender();
							String args = StringArgumentType.getString(context, "vanilla");
							return Bukkit.dispatchCommand(sender, "minecraft:loot " + args) ? Command.SINGLE_SUCCESS : 0;
						}))
				.build();
	}

	private static boolean isAdmin(CommandSender sender) {
		return sender.hasPermission(RatesGui.PERMISSION);
	}
}
