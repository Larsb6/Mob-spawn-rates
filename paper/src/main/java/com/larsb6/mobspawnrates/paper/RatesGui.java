package com.larsb6.mobspawnrates.paper;

import com.larsb6.mobspawnrates.paper.RatesStore.Kind;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Chest GUI: a mob list with pages, and an edit page with buttons for the spawn and loot rate.
 */
public final class RatesGui implements InventoryHolder {
	public static final String PERMISSION = "mobspawnrates.admin";

	private static final int SIZE = 54;
	private static final int MOBS_PER_PAGE = 45;

	private static final int SLOT_PREV = 45;
	private static final int SLOT_GLOBAL = 47;
	private static final int SLOT_CLOSE = 49;
	private static final int SLOT_RESET_ALL = 51;
	private static final int SLOT_NEXT = 53;

	private static final int SLOT_ICON = 4;
	private static final int SPAWN_ROW = 9;
	private static final int LOOT_ROW = 27;
	private static final int SLOT_BACK = 45;
	private static final int SLOT_RESET_MOB = 53;

	private static final List<EntityType> MOBS = Arrays.stream(EntityType.values())
			.filter(t -> t != EntityType.UNKNOWN && t.isSpawnable() && t.getEntityClass() != null
					&& Mob.class.isAssignableFrom(t.getEntityClass()))
			.sorted(Comparator.comparing(t -> t.getKey().getKey()))
			.toList();

	private final RatesStore store;
	private final Inventory inventory;
	private int page = 0;
	private boolean editing = false;
	/** Mob being edited, or {@code null} for the global multipliers. */
	private EntityType target;

	private RatesGui(RatesStore store) {
		this.store = store;
		this.inventory = Bukkit.createInventory(this, SIZE, Component.text("Mob Spawn & Loot Rates"));
		render();
	}

	public static void open(Player player, RatesStore store) {
		player.openInventory(new RatesGui(store).inventory);
	}

	@Override
	public Inventory getInventory() {
		return inventory;
	}

	/** Cancels every interaction with the GUI and turns clicks into button presses. */
	public static final class ClickListener implements Listener {
		@EventHandler(ignoreCancelled = false)
		public void onClick(InventoryClickEvent event) {
			if (!(event.getView().getTopInventory().getHolder(false) instanceof RatesGui gui)) {
				return;
			}
			event.setCancelled(true);
			if (!(event.getWhoClicked() instanceof Player player)) {
				return;
			}
			if (!player.hasPermission(PERMISSION)) {
				player.closeInventory();
				return;
			}
			int slot = event.getRawSlot();
			if (slot < 0 || slot >= SIZE || !(event.getClick().isLeftClick() || event.getClick().isRightClick())) {
				return;
			}
			if (gui.handleClick(player, slot, event.getClick().isShiftClick())) {
				player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4F, 1.0F);
			}
			gui.render();
		}

		@EventHandler
		public void onDrag(InventoryDragEvent event) {
			if (event.getView().getTopInventory().getHolder(false) instanceof RatesGui) {
				event.setCancelled(true);
			}
		}
	}

	private boolean handleClick(Player player, int slot, boolean shift) {
		return editing ? handleEditClick(player, slot) : handleListClick(player, slot, shift);
	}

	private int pageCount() {
		return Math.max(1, (MOBS.size() + MOBS_PER_PAGE - 1) / MOBS_PER_PAGE);
	}

	private boolean handleListClick(Player player, int slot, boolean shift) {
		if (slot < MOBS_PER_PAGE) {
			int index = page * MOBS_PER_PAGE + slot;
			if (index < MOBS.size()) {
				target = MOBS.get(index);
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
				player.closeInventory();
				return true;
			}
			case SLOT_RESET_ALL -> {
				if (shift) {
					store.resetAll();
					player.sendMessage(Component.text("All spawn and loot rates were reset to 1x.", NamedTextColor.YELLOW));
					return true;
				}
			}
			default -> {
			}
		}
		return false;
	}

	private boolean handleEditClick(Player player, int slot) {
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
				player.closeInventory();
				return true;
			}
			case SLOT_RESET_MOB -> {
				if (target != null) {
					store.resetMob(target);
				} else {
					store.setGlobal(Kind.SPAWN, 1.0);
					store.setGlobal(Kind.LOOT, 1.0);
				}
				return true;
			}
			default -> {
			}
		}
		return false;
	}

	private double get(Kind kind) {
		return target == null ? store.getGlobal(kind) : store.getMob(target, kind);
	}

	private void set(Kind kind, double value) {
		if (target == null) {
			store.setGlobal(kind, value);
		} else {
			store.setMob(target, kind, value);
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
		inventory.clear();
		if (editing) {
			renderEdit();
		} else {
			renderList();
		}
	}

	private void renderList() {
		int start = page * MOBS_PER_PAGE;
		for (int i = 0; i < MOBS_PER_PAGE && start + i < MOBS.size(); i++) {
			EntityType type = MOBS.get(start + i);
			double spawn = store.getMob(type, Kind.SPAWN);
			double loot = store.getMob(type, Kind.LOOT);
			ItemStack stack = button(iconFor(type), mobName(type),
					rateLine("Spawn rate", spawn, store.effective(type, Kind.SPAWN)),
					rateLine("Loot rate", loot, store.effective(type, Kind.LOOT)),
					Component.empty(),
					hint("Click to edit"));
			if (spawn != 1.0 || loot != 1.0) {
				stack.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
			}
			inventory.setItem(i, stack);
		}
		for (int i = MOBS_PER_PAGE; i < SIZE; i++) {
			inventory.setItem(i, filler());
		}
		if (page > 0) {
			inventory.setItem(SLOT_PREV, button(Material.ARROW, text("Previous page", NamedTextColor.YELLOW), pageLine()));
		}
		if (page < pageCount() - 1) {
			inventory.setItem(SLOT_NEXT, button(Material.ARROW, text("Next page", NamedTextColor.YELLOW), pageLine()));
		}
		inventory.setItem(SLOT_GLOBAL, button(Material.NETHER_STAR, text("Global multipliers", NamedTextColor.AQUA),
				rateLine("Spawn rate", store.getGlobal(Kind.SPAWN), Double.NaN),
				rateLine("Loot rate", store.getGlobal(Kind.LOOT), Double.NaN),
				Component.empty(),
				hint("Applies to every mob, multiplied"),
				hint("with the per-mob rate."),
				hint("Click to edit")));
		inventory.setItem(SLOT_CLOSE, button(Material.BARRIER, text("Close", NamedTextColor.RED)));
		inventory.setItem(SLOT_RESET_ALL, button(Material.TNT, text("Reset everything", NamedTextColor.RED),
				hint("Shift-click to reset all"),
				hint("rates back to 1x")));
	}

	private void renderEdit() {
		for (int i = 0; i < SIZE; i++) {
			inventory.setItem(i, filler());
		}
		Component name = target == null ? text("Global multipliers", NamedTextColor.AQUA) : mobName(target);
		inventory.setItem(SLOT_ICON, button(target == null ? Material.NETHER_STAR : iconFor(target), name,
				target == null ? hint("Applies to every mob") : hint(target.getKey().toString())));

		renderRow(SPAWN_ROW, Kind.SPAWN, Material.SPAWNER, "Spawn rate",
				"How many of this mob spawn naturally.", "0x = never, 2x = twice as many");
		renderRow(LOOT_ROW, Kind.LOOT, Material.CHEST, "Loot rate",
				"How often the loot table drops on death.", "0x = no drops, 2x = double drops");

		inventory.setItem(SLOT_BACK, button(Material.ARROW, text("Back", NamedTextColor.YELLOW)));
		inventory.setItem(SLOT_CLOSE, button(Material.BARRIER, text("Close", NamedTextColor.RED)));
		inventory.setItem(SLOT_RESET_MOB, button(Material.WATER_BUCKET, text("Reset to 1x", NamedTextColor.YELLOW),
				hint("Resets both spawn and loot rate")));
	}

	private void renderRow(int row, Kind kind, Material icon, String label, String desc1, String desc2) {
		double value = get(kind);
		double effective = target == null ? Double.NaN : store.effective(target, kind);
		inventory.setItem(row, button(Material.BLACK_CONCRETE, text("Set to 0x", NamedTextColor.DARK_GRAY), hint("Disable completely")));
		inventory.setItem(row + 1, button(Material.RED_CONCRETE, text("-1.0", NamedTextColor.RED)));
		inventory.setItem(row + 2, button(Material.PINK_CONCRETE, text("-0.1", NamedTextColor.RED)));
		ItemStack display = button(icon, text(label, NamedTextColor.GOLD),
				rateLine("Current", value, effective),
				Component.empty(),
				hint(desc1),
				hint(desc2),
				hint("Range: " + format(RatesStore.MIN) + " - " + format(RatesStore.MAX)));
		display.setAmount(Math.max(1, Math.min(64, (int) Math.round(value))));
		inventory.setItem(row + 4, display);
		inventory.setItem(row + 6, button(Material.LIME_CONCRETE, text("+0.1", NamedTextColor.GREEN)));
		inventory.setItem(row + 7, button(Material.GREEN_CONCRETE, text("+1.0", NamedTextColor.GREEN)));
		inventory.setItem(row + 8, button(Material.WHITE_CONCRETE, text("Reset to 1x", NamedTextColor.WHITE)));
	}

	// ---------------------------------------------------------------- item helpers

	private static Material iconFor(EntityType type) {
		Material egg = Bukkit.getItemFactory().getSpawnEgg(type);
		return egg != null ? egg : Material.NAME_TAG;
	}

	private static Component mobName(EntityType type) {
		return Component.translatable(type.translationKey(), NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false);
	}

	private static ItemStack filler() {
		return button(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "));
	}

	private static ItemStack button(Material material, Component name, Component... lore) {
		ItemStack stack = new ItemStack(material);
		ItemMeta meta = stack.getItemMeta();
		meta.itemName(name);
		if (lore.length > 0) {
			List<Component> lines = new ArrayList<>();
			for (Component line : lore) {
				lines.add(line.decoration(TextDecoration.ITALIC, false));
			}
			meta.lore(lines);
		}
		stack.setItemMeta(meta);
		return stack;
	}

	private static TextComponent text(String text, NamedTextColor color) {
		return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
	}

	private static Component hint(String text) {
		return text(text, NamedTextColor.GRAY);
	}

	private Component pageLine() {
		return hint("Page " + (page + 1) + " / " + pageCount());
	}

	private static Component rateLine(String label, double value, double effective) {
		NamedTextColor color = value == 1.0 ? NamedTextColor.WHITE : value < 1.0 ? NamedTextColor.RED : NamedTextColor.GREEN;
		TextComponent line = text(label + ": ", NamedTextColor.GRAY).append(text("x" + format(value), color));
		if (!Double.isNaN(effective) && effective != value) {
			line = line.append(text(" (x" + format(effective) + " with global)", NamedTextColor.DARK_GRAY));
		}
		return line;
	}

	static String format(double value) {
		return BigDecimal.valueOf(Math.round(value * 100.0) / 100.0).stripTrailingZeros().toPlainString();
	}
}
