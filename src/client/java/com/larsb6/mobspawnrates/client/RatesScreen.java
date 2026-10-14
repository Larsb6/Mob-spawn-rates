package com.larsb6.mobspawnrates.client;

import com.larsb6.mobspawnrates.network.RatesNetworking;
import com.larsb6.mobspawnrates.network.RatesNetworking.MobEntry;
import com.larsb6.mobspawnrates.network.RatesNetworking.ResetPayload;
import com.larsb6.mobspawnrates.network.RatesNetworking.SetRatePayload;
import com.larsb6.mobspawnrates.network.RatesNetworking.SyncPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Client-side screen with a spawn and a loot slider for every mob. Only used when the
 * client has the mod installed; changes are sent to the server, which checks permissions.
 */
public class RatesScreen extends Screen {
	private static final Component TITLE = Component.literal("Mob Spawn & Loot Rates");

	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, 52, 33);
	/** Current values, keyed by mob id or {@link RatesNetworking#GLOBAL}: [spawn, loot]. */
	private final Map<String, double[]> values = new HashMap<>();
	/** Changes not yet sent to the server, keyed by target + kind. */
	private final Map<String, SetRatePayload> pending = new LinkedHashMap<>();
	private final List<EntityType<?>> mobs = new ArrayList<>();

	private EditBox search;
	private RatesList list;
	private Button resetAll;
	private boolean confirmReset = false;

	public RatesScreen(SyncPayload sync) {
		super(TITLE);
		for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
			if (type != EntityType.PLAYER && (SpawnEggItem.byId(type) != null || type.getCategory() != MobCategory.MISC)) {
				mobs.add(type);
			}
		}
		mobs.sort(Comparator.comparing(t -> t.getDescription().getString()));
		readSync(sync);
	}

	private void readSync(SyncPayload sync) {
		values.clear();
		values.put(RatesNetworking.GLOBAL, new double[]{sync.globalSpawn(), sync.globalLoot()});
		for (MobEntry entry : sync.mobs()) {
			values.put(entry.id(), new double[]{entry.spawn(), entry.loot()});
		}
	}

	/** Called when the server sends fresh values (after a reset). */
	public void applySync(SyncPayload sync) {
		pending.clear();
		readSync(sync);
		if (list != null) {
			list.children().forEach(RateRow::refresh);
		}
	}

	double get(String id, boolean loot) {
		double[] v = values.get(id);
		return v == null ? 1.0 : v[loot ? 1 : 0];
	}

	void set(String id, boolean loot, double value) {
		values.computeIfAbsent(id, k -> new double[]{1.0, 1.0})[loot ? 1 : 0] = value;
		pending.put(id + (loot ? "|loot" : "|spawn"), new SetRatePayload(id, loot, value));
		list.children().forEach(RateRow::refreshTooltips);
	}

	void reset(String target) {
		pending.clear();
		ClientPlayNetworking.send(new ResetPayload(target));
	}

	@Override
	protected void init() {
		LinearLayout header = layout.addToHeader(LinearLayout.vertical().spacing(4));
		header.defaultCellSetting().alignHorizontallyCenter();
		header.addChild(new net.minecraft.client.gui.components.StringWidget(TITLE, this.font));
		search = header.addChild(new EditBox(this.font, 200, 20, Component.literal("Search")));
		search.setHint(Component.literal("Search mobs...").withStyle(ChatFormatting.DARK_GRAY));
		search.setResponder(text -> list.filter(text));

		list = layout.addToContents(new RatesList());

		LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
		resetAll = footer.addChild(Button.builder(Component.literal("Reset all"), button -> {
			if (confirmReset) {
				reset(RatesNetworking.ALL);
				confirmReset = false;
				button.setMessage(Component.literal("Reset all"));
			} else {
				confirmReset = true;
				button.setMessage(Component.literal("Click again to confirm").withStyle(ChatFormatting.RED));
			}
		}).width(150).build());
		footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).width(150).build());

		layout.visitWidgets(this::addRenderableWidget);
		list.filter("");
		repositionElements();
	}

	@Override
	protected void setInitialFocus() {
		setInitialFocus(search);
	}

	@Override
	protected void repositionElements() {
		layout.arrangeElements();
		if (list != null) {
			list.updateSize(this.width, layout);
		}
	}

	@Override
	public void tick() {
		flush();
	}

	@Override
	public void onClose() {
		flush();
		super.onClose();
	}

	@Override
	public void removed() {
		flush();
		super.removed();
	}

	private void flush() {
		if (pending.isEmpty() || this.minecraft == null || this.minecraft.getConnection() == null) {
			return;
		}
		for (SetRatePayload payload : pending.values()) {
			ClientPlayNetworking.send(payload);
		}
		pending.clear();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ------------------------------------------------------------------ list

	class RatesList extends ContainerObjectSelectionList<RateRow> {
		private final List<RateRow> all = new ArrayList<>();

		RatesList() {
			super(RatesScreen.this.minecraft, RatesScreen.this.width, layout.getContentHeight(), layout.getHeaderHeight(), 24);
			all.add(new RateRow(RatesNetworking.GLOBAL, Component.literal("Global (all mobs)").withStyle(ChatFormatting.AQUA), new ItemStack(Items.NETHER_STAR)));
			for (EntityType<?> type : mobs) {
				SpawnEggItem egg = SpawnEggItem.byId(type);
				all.add(new RateRow(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(), type.getDescription(),
						new ItemStack(egg != null ? egg : Items.NAME_TAG)));
			}
		}

		void filter(String text) {
			String query = text.trim().toLowerCase(Locale.ROOT);
			List<RateRow> shown = new ArrayList<>();
			for (RateRow entry : all) {
				if (query.isEmpty() || entry.id.equals(RatesNetworking.GLOBAL)
						|| entry.name.getString().toLowerCase(Locale.ROOT).contains(query)
						|| entry.id.contains(query)) {
					shown.add(entry);
				}
			}
			replaceEntries(shown);
			setScrollAmount(0);
		}

		@Override
		public int getRowWidth() {
			return Math.min(440, RatesScreen.this.width - 20);
		}
	}

	class RateRow extends ContainerObjectSelectionList.Entry<RateRow> {
		final String id;
		final Component name;
		private final ItemStack icon;
		private final RateSlider spawn;
		private final RateSlider loot;
		private final Button resetButton;

		RateRow(String id, Component name, ItemStack icon) {
			this.id = id;
			this.name = name;
			this.icon = icon;
			this.spawn = new RateSlider(120, "Spawn", get(id, false), value -> set(id, false, value));
			this.loot = new RateSlider(120, "Loot", get(id, true), value -> set(id, true, value));
			this.resetButton = Button.builder(Component.literal("↺"), button -> reset(id)).width(20).build();
			this.resetButton.setTooltip(Tooltip.create(Component.literal("Reset to 1x")));
			refreshTooltips();
		}

		void refresh() {
			spawn.setRateSilently(get(id, false));
			loot.setRateSilently(get(id, true));
			refreshTooltips();
		}

		void refreshTooltips() {
			spawn.setTooltip(Tooltip.create(tooltip(false)));
			loot.setTooltip(Tooltip.create(tooltip(true)));
		}

		private Component tooltip(boolean isLoot) {
			String what = isLoot
					? "How often the loot table drops on death.\n0x = no drops, 2x = double drops."
					: "How many of this mob spawn naturally.\n0x = never, 2x = twice as many.";
			String keys = "\n\nDrag, or focus and use the arrow keys\n(±0.01, Shift: ±0.1).";
			if (id.equals(RatesNetworking.GLOBAL)) {
				return Component.literal(what + "\nMultiplies every mob's own rate." + keys);
			}
			double effective = get(id, isLoot) * get(RatesNetworking.GLOBAL, isLoot);
			return Component.literal(what + "\nWith global multiplier: x" + RateSlider.format(effective) + keys);
		}

		@Override
		public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float delta) {
			int x = getContentX();
			int y = getContentY();
			int right = getContentRight();
			int sliderWidth = Math.max(60, Math.min(120, (getContentWidth() - 150) / 2));
			spawn.setWidth(sliderWidth);
			loot.setWidth(sliderWidth);

			resetButton.setPosition(right - 20, y);
			loot.setPosition(resetButton.getX() - 4 - sliderWidth, y);
			spawn.setPosition(loot.getX() - 4 - sliderWidth, y);

			graphics.renderItem(icon, x, y + 2);
			int nameWidth = spawn.getX() - x - 24;
			Component shownName = name;
			if (font.width(name) > nameWidth) {
				shownName = Component.literal(font.plainSubstrByWidth(name.getString(), nameWidth - font.width("...")) + "...");
			}
			boolean modified = get(id, false) != 1.0 || get(id, true) != 1.0;
			graphics.drawString(font, shownName, x + 20, getContentYMiddle() - 4, modified ? 0xFFFFFF55 : 0xFFFFFFFF);

			resetButton.active = modified;
			spawn.render(graphics, mouseX, mouseY, delta);
			loot.render(graphics, mouseX, mouseY, delta);
			resetButton.render(graphics, mouseX, mouseY, delta);
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return List.of(spawn, loot, resetButton);
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return List.of(spawn, loot, resetButton);
		}
	}
}
