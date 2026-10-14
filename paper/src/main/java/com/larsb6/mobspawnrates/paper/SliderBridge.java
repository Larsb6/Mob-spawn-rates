package com.larsb6.mobspawnrates.paper;

import com.larsb6.mobspawnrates.paper.RatesStore.Kind;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Lets players who have the Fabric mod on their client use its slider screen on this Paper server.
 * The byte format matches the Fabric mod's packets (mobspawnrates:sync / set_rate / reset).
 */
public final class SliderBridge implements PluginMessageListener {
	public static final String SYNC = "mobspawnrates:sync";
	public static final String SET_RATE = "mobspawnrates:set_rate";
	public static final String RESET = "mobspawnrates:reset";

	private static final String GLOBAL = "";
	private static final String ALL = "*";

	private final Plugin plugin;
	private final RatesStore store;

	public SliderBridge(Plugin plugin, RatesStore store) {
		this.plugin = plugin;
		this.store = store;
	}

	public void register() {
		var messenger = plugin.getServer().getMessenger();
		messenger.registerOutgoingPluginChannel(plugin, SYNC);
		messenger.registerIncomingPluginChannel(plugin, SET_RATE, this);
		messenger.registerIncomingPluginChannel(plugin, RESET, this);
	}

	/** True if the player's client has the Fabric mod and listens for the slider screen packet. */
	public boolean canUseSliderScreen(Player player) {
		return player.getListeningPluginChannels().contains(SYNC);
	}

	public void sendSync(Player player, boolean open) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(open ? 1 : 0);
		writeDouble(out, store.getGlobal(Kind.SPAWN));
		writeDouble(out, store.getGlobal(Kind.LOOT));
		Map<String, double[]> mobs = store.snapshotMobs();
		writeVarInt(out, mobs.size());
		mobs.forEach((id, rates) -> {
			writeString(out, id);
			writeDouble(out, rates[0]);
			writeDouble(out, rates[1]);
		});
		player.sendPluginMessage(plugin, SYNC, out.toByteArray());
	}

	@Override
	public void onPluginMessageReceived(String channel, Player player, byte[] message) {
		if (!player.hasPermission(RatesGui.PERMISSION)) {
			return;
		}
		ByteBuffer in = ByteBuffer.wrap(message);
		try {
			if (SET_RATE.equals(channel)) {
				String target = readString(in);
				Kind kind = in.get() != 0 ? Kind.LOOT : Kind.SPAWN;
				double value = in.getDouble();
				if (GLOBAL.equals(target)) {
					store.setGlobal(kind, value);
				} else {
					EntityType type = parseMob(target);
					if (type != null) {
						store.setMob(type, kind, value);
					}
				}
			} else if (RESET.equals(channel)) {
				String target = readString(in);
				switch (target) {
					case ALL -> store.resetAll();
					case GLOBAL -> {
						store.setGlobal(Kind.SPAWN, 1.0);
						store.setGlobal(Kind.LOOT, 1.0);
					}
					default -> {
						EntityType type = parseMob(target);
						if (type != null) {
							store.resetMob(type);
						}
					}
				}
				sendSync(player, false);
			}
		} catch (RuntimeException e) {
			plugin.getLogger().warning("Ignoring malformed " + channel + " packet from " + player.getName());
		}
	}

	private static EntityType parseMob(String id) {
		NamespacedKey key = NamespacedKey.fromString(id);
		return key == null ? null : Registry.ENTITY_TYPE.get(key);
	}

	// Minecraft network primitives (VarInt-prefixed UTF-8 strings, big-endian doubles).

	private static void writeVarInt(ByteArrayOutputStream out, int value) {
		while ((value & ~0x7F) != 0) {
			out.write((value & 0x7F) | 0x80);
			value >>>= 7;
		}
		out.write(value);
	}

	private static int readVarInt(ByteBuffer in) {
		int value = 0;
		int shift = 0;
		byte b;
		do {
			if (shift >= 35) {
				throw new IllegalArgumentException("VarInt too big");
			}
			b = in.get();
			value |= (b & 0x7F) << shift;
			shift += 7;
		} while ((b & 0x80) != 0);
		return value;
	}

	private static void writeString(ByteArrayOutputStream out, String value) {
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		writeVarInt(out, bytes.length);
		out.writeBytes(bytes);
	}

	private static String readString(ByteBuffer in) {
		int length = readVarInt(in);
		if (length < 0 || length > 1024) {
			throw new IllegalArgumentException("Bad string length");
		}
		byte[] bytes = new byte[length];
		in.get(bytes);
		return new String(bytes, StandardCharsets.UTF_8);
	}

	private static void writeDouble(ByteArrayOutputStream out, double value) {
		out.writeBytes(ByteBuffer.allocate(8).putDouble(value).array());
	}
}
