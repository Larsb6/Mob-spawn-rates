package com.larsb6.mobspawnrates.gui;

import com.larsb6.mobspawnrates.config.RatesConfig;
import com.larsb6.mobspawnrates.config.RatesConfig.Kind;
import com.larsb6.mobspawnrates.network.RatesNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Server-side chest GUI. Because it is a plain 9x6 chest menu, vanilla clients can use it,
 * so the mod only has to be installed on the server (or in singleplayer).
 */
public class RatesMenu extends ChestMenu {
	private static final int ROWS = 6;
	private static final int SIZE = ROWS * 9;
	private static final int MOBS_PER_PAGE = 45;

	// List page
	private static final int SLOT_PREV = 45;
	private static final int SLOT_GLOBAL = 47;
	private static final int SLOT_CLOSE = 49;
	private static final int SLOT_RESET_ALL = 51;
	private static final int SLOT_NEXT = 53;

	// Edit page
	private static final int SLOT_ICON = 4;
	private static final int SPAWN_ROW = 9;
	private static final int LOOT_ROW = 27;
	private static final int SLOT_BACK = 45;
	private static final int SLOT_RESET_MOB = 53;

	private final SimpleContainer gui;
	private final List<EntityType<?>> mobs;
	private int page = 0;
	private boolean editing = false;
	/** Mob being edited, or {@code null} for the global multipliers. */
	@Nullable
	private EntityType<?> target;

	public static void open(ServerPlayer player) {
		player.openMenu(new SimpleMenuProvider(
				(id, inventory, p) -> new RatesMenu(id, inventory, new SimpleContainer(SIZE)),
				Component.literal("Mob Spawn & Loot Rates")));
	}

	private RatesMenu(int id, Inventory inventory, SimpleContainer container) {
		super(MenuType.GENERIC_9x6, id, inventory, container, ROWS);
		this.gui = container;
		this.mobs = collectMobs();
		render();
	}

	private static List<EntityType<?>> collectMobs() {
		List<EntityType<?>> list = new ArrayList<>();
		for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
			if (type == EntityType.PLAYER) {
				continue;
			}
			if (SpawnEggItem.byId(type) != null || type.getCategory() != MobCategory.MISC) {
				list.add(type);
			}
		}
		list.sort(Comparator.comparing(t -> t.getDescription().getString()));
		return list;
	}

	private static boolean isOperator(Player player) {
		return player instanceof ServerPlayer serverPlayer && RatesNetworking.isOperator(serverPlayer);
	}

	@Override
	public boolean stillValid(Player player) {
		return isOperator(player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public void clicked(int slot, int button, ClickType clickType, Player player) {
		// Never let items move in or out of this menu; every click is a button press.
		if (player instanceof ServerPlayer serverPlayer && isOperator(player)
				&& slot >= 0 && slot < SIZE
				&& (clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE)) {
			if (handleClick(serverPlayer, slot, clickType == ClickType.QUICK_MOVE)) {
				serverPlayer.level().playSound(null, serverPlayer.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.MASTER, 0.4F, 1.0F);
			}
			render();
		}
		this.sendAllDataToRemote();
	}

	/** @return true if the click did something */
	private boolean handleClick(ServerPlayer player, int slot, boolean shift) {
		return editing ? handleEditClick(player, slot, shift) : handleListClick(player, slot, shift);
	}

	private int pageCount() {
		return Math.max(1, (mobs.size() + MOBS_PER_PAGE - 1) / MOBS_PER_PAGE);
	}

	private boolean handleListClick(ServerPlayer player, int slot, boolean shift) {
		if (slot < MOBS_PER_PAGE) {
			int index = page * MOBS_PER_PAGE + slot;
			if (index < mobs.size()) {
				target = mobs.get(index);
				editing = true;
				return true;
			}
			return false;
		}
		switch (slot) {
			case SLOT_PREV -> {
				if (page > 0) {
					page--;
					return true;
				}
			}
			case SLOT_NEXT -> {
				if (page < pageCount() - 1) {
					page++;
					return true;
				}
			}
			case SLOT_GLOBAL -> {
				target = null;
				editing = true;
				return true;
			}
			case SLOT_CLOSE -> {
				player.closeContainer();
				return true;
			}
			case SLOT_RESET_ALL -> {
				if (shift) {
					RatesConfig.resetAll();
					player.sendSystemMessage(Component.literal("All spawn and loot rates were reset to 1x.").withStyle(ChatFormatting.YELLOW));
					return true;
				}
			}
			default -> {
			}
		}
		return false;
	}

	private boolean handleEditClick(ServerPlayer player, int slot, boolean shift) {
		if (slot >= SPAWN_ROW && slot < SPAWN_ROW + 9) {
			return adjust(Kind.SPAWN, slot - SPAWN_ROW);
		}
		if (slot >= LOOT_ROW && slot < LOOT_ROW + 9) {
			return adjust(Kind.LOOT, slot - LOOT_ROW);
		}
		switch (slot) {
			case SLOT_BACK -> {
				editing = false;
				return true;
			}
			case SLOT_CLOSE -> {
				player.closeContainer();
				return true;
			}
			case SLOT_RESET_MOB -> {
				if (target != null) {
					RatesConfig.resetMob(target);
				} else {
					RatesConfig.setGlobal(Kind.SPAWN, 1.0);
					RatesConfig.setGlobal(Kind.LOOT, 1.0);
				}
				return true;
			}
			default -> {
			}
		}
		return false;
	}

	private double get(Kind kind) {
		return target == null ? RatesConfig.getGlobal(kind) : RatesConfig.getMob(target, kind);
	}

	private void set(Kind kind, double value) {
		if (target == null) {
			RatesConfig.setGlobal(kind, value);
		} else {
			RatesConfig.setMob(target, kind, value);
		}
	}

	private boolean adjust(Kind kind, int column) {
		double current = get(kind);
		double next;
		switch (column) {
			case 0 -> next = 0.0;
			case 1 -> next = current - 1.0;
			case 2 -> next = current - 0.1;
			case 6 -> next = current + 0.1;
			case 7 -> next = current + 1.0;
			case 8 -> next = 1.0;
			default -> {
				return false;
			}
		}
		set(kind, next);
		return true;
	}

	// ---------------------------------------------------------------- rendering

	private void render() {
		for (int i = 0; i < SIZE; i++) {
			gui.setItem(i, ItemStack.EMPTY);
		}
		if (editing) {
			renderEdit();
		} else {
			renderList();
		}
	}

	private void renderList() {
		int start = page * MOBS_PER_PAGE;
		for (int i = 0; i < MOBS_PER_PAGE && start + i < mobs.size(); i++) {
			EntityType<?> type = mobs.get(start + i);
			double spawn = RatesConfig.getMob(type, Kind.SPAWN);
			double loot = RatesConfig.getMob(type, Kind.LOOT);
			ItemStack stack = button(iconFor(type), type.getDescription().copy().withStyle(ChatFormatting.WHITE),
					rateLine("Spawn rate", spawn, RatesConfig.effective(type, Kind.SPAWN)),
					rateLine("Loot rate", loot, RatesConfig.effective(type, Kind.LOOT)),
					Component.empty(),
					hint("Click to edit"));
			if (spawn != 1.0 || loot != 1.0) {
				stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			}
			gui.setItem(i, stack);
		}
		for (int i = MOBS_PER_PAGE; i < SIZE; i++) {
			gui.setItem(i, filler());
		}
		if (page > 0) {
			gui.setItem(SLOT_PREV, button(Items.ARROW, text("Previous page", ChatFormatting.YELLOW), pageLine()));
		}
		if (page < pageCount() - 1) {
			gui.setItem(SLOT_NEXT, button(Items.ARROW, text("Next page", ChatFormatting.YELLOW), pageLine()));
		}
		gui.setItem(SLOT_GLOBAL, button(Items.NETHER_STAR, text("Global multipliers", ChatFormatting.AQUA),
				rateLine("Spawn rate", RatesConfig.getGlobal(Kind.SPAWN), Double.NaN),
				rateLine("Loot rate", RatesConfig.getGlobal(Kind.LOOT), Double.NaN),
				Component.empty(),
				hint("Applies to every mob, multiplied"),
				hint("with the per-mob rate."),
				hint("Click to edit")));
		gui.setItem(SLOT_CLOSE, button(Items.BARRIER, text("Close", ChatFormatting.RED)));
		gui.setItem(SLOT_RESET_ALL, button(Items.TNT, text("Reset everything", ChatFormatting.RED),
				hint("Shift-click to reset all"),
				hint("rates back to 1x")));
	}

	private void renderEdit() {
		for (int i = 0; i < SIZE; i++) {
			gui.setItem(i, filler());
		}
		Component name = target == null
				? text("Global multipliers", ChatFormatting.AQUA)
				: target.getDescription().copy().withStyle(ChatFormatting.WHITE);
		gui.setItem(SLOT_ICON, button(target == null ? Items.NETHER_STAR : iconFor(target), name,
				target == null ? hint("Applies to every mob") : hint(BuiltInRegistries.ENTITY_TYPE.getKey(target).toString())));

		renderRow(SPAWN_ROW, Kind.SPAWN, Items.SPAWNER, "Spawn rate",
				"How many of this mob spawn naturally.", "0x = never, 2x = twice as many");
		renderRow(LOOT_ROW, Kind.LOOT, Items.CHEST, "Loot rate",
				"How often the loot table drops on death.", "0x = no drops, 2x = double drops");

		gui.setItem(SLOT_BACK, button(Items.ARROW, text("Back", ChatFormatting.YELLOW)));
		gui.setItem(SLOT_CLOSE, button(Items.BARRIER, text("Close", ChatFormatting.RED)));
		gui.setItem(SLOT_RESET_MOB, button(Items.WATER_BUCKET, text("Reset to 1x", ChatFormatting.YELLOW),
				hint("Resets both spawn and loot rate")));
	}

	private void renderRow(int row, Kind kind, Item icon, String label, String desc1, String desc2) {
		double value = get(kind);
		double effective = target == null ? Double.NaN : RatesConfig.effective(target, kind);
		gui.setItem(row, button(Items.BLACK_CONCRETE, text("Set to 0x", ChatFormatting.DARK_GRAY), hint("Disable completely")));
		gui.setItem(row + 1, button(Items.RED_CONCRETE, text("-1.0", ChatFormatting.RED)));
		gui.setItem(row + 2, button(Items.PINK_CONCRETE, text("-0.1", ChatFormatting.RED)));
		ItemStack display = button(icon, text(label, ChatFormatting.GOLD),
				rateLine("Current", value, effective),
				Component.empty(),
				hint(desc1),
				hint(desc2),
				hint("Range: " + format(RatesConfig.MIN) + " - " + format(RatesConfig.MAX)));
		display.setCount(Math.max(1, Math.min(64, (int) Math.round(value))));
		gui.setItem(row + 4, display);
		gui.setItem(row + 6, button(Items.LIME_CONCRETE, text("+0.1", ChatFormatting.GREEN)));
		gui.setItem(row + 7, button(Items.GREEN_CONCRETE, text("+1.0", ChatFormatting.GREEN)));
		gui.setItem(row + 8, button(Items.WHITE_CONCRETE, text("Reset to 1x", ChatFormatting.WHITE)));
	}

	// ---------------------------------------------------------------- item helpers

	private static Item iconFor(EntityType<?> type) {
		SpawnEggItem egg = SpawnEggItem.byId(type);
		return egg != null ? egg : Items.NAME_TAG;
	}

	private static ItemStack filler() {
		ItemStack stack = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
		stack.set(DataComponents.ITEM_NAME, Component.literal(" "));
		return stack;
	}

	private static ItemStack button(Item item, Component name, Component... lore) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.ITEM_NAME, name);
		if (lore.length > 0) {
			stack.set(DataComponents.LORE, new ItemLore(List.of(lore)));
		}
		return stack;
	}

	private static MutableComponent text(String text, ChatFormatting color) {
		return Component.literal(text).withStyle(style -> style.withColor(color).withItalic(false));
	}

	private static Component hint(String text) {
		return text(text, ChatFormatting.GRAY);
	}

	private Component pageLine() {
		return hint("Page " + (page + 1) + " / " + pageCount());
	}

	private static Component rateLine(String label, double value, double effective) {
		ChatFormatting color = value == 1.0 ? ChatFormatting.WHITE : value < 1.0 ? ChatFormatting.RED : ChatFormatting.GREEN;
		MutableComponent line = text(label + ": ", ChatFormatting.GRAY).append(text("x" + format(value), color));
		if (!Double.isNaN(effective) && effective != value) {
			line.append(text(" (x" + format(effective) + " with global)", ChatFormatting.DARK_GRAY));
		}
		return line;
	}

	private static String format(double value) {
		return BigDecimal.valueOf(Math.round(value * 100.0) / 100.0).stripTrailingZeros().toPlainString();
	}
}
