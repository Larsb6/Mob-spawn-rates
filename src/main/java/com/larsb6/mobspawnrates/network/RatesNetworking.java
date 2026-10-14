package com.larsb6.mobspawnrates.network;

import com.larsb6.mobspawnrates.MobSpawnRates;
import com.larsb6.mobspawnrates.config.RatesConfig;
import com.larsb6.mobspawnrates.config.RatesConfig.Kind;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Packets for the optional client-side slider screen. Clients without the mod never
 * receive these; they get the chest GUI instead.
 */
public final class RatesNetworking {
	/** Target id meaning "the global multipliers". */
	public static final String GLOBAL = "";
	/** Reset target meaning "everything". */
	public static final String ALL = "*";

	private RatesNetworking() {
	}

	public record MobEntry(String id, double spawn, double loot) {
		public static final StreamCodec<ByteBuf, MobEntry> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, MobEntry::id,
				ByteBufCodecs.DOUBLE, MobEntry::spawn,
				ByteBufCodecs.DOUBLE, MobEntry::loot,
				MobEntry::new);
	}

	/** Server -> client: current rates. {@code open} asks the client to open the screen. */
	public record SyncPayload(boolean open, double globalSpawn, double globalLoot, List<MobEntry> mobs) implements CustomPacketPayload {
		public static final Type<SyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MobSpawnRates.MOD_ID, "sync"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SyncPayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.BOOL, SyncPayload::open,
				ByteBufCodecs.DOUBLE, SyncPayload::globalSpawn,
				ByteBufCodecs.DOUBLE, SyncPayload::globalLoot,
				MobEntry.CODEC.apply(ByteBufCodecs.list()), SyncPayload::mobs,
				SyncPayload::new);

		@Override
		public Type<SyncPayload> type() {
			return TYPE;
		}
	}

	/** Client -> server: set one rate. {@code target} is a mob id or {@link #GLOBAL}. */
	public record SetRatePayload(String target, boolean loot, double value) implements CustomPacketPayload {
		public static final Type<SetRatePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MobSpawnRates.MOD_ID, "set_rate"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SetRatePayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, SetRatePayload::target,
				ByteBufCodecs.BOOL, SetRatePayload::loot,
				ByteBufCodecs.DOUBLE, SetRatePayload::value,
				SetRatePayload::new);

		@Override
		public Type<SetRatePayload> type() {
			return TYPE;
		}
	}

	/** Client -> server: reset a mob, the globals ({@link #GLOBAL}) or everything ({@link #ALL}). */
	public record ResetPayload(String target) implements CustomPacketPayload {
		public static final Type<ResetPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MobSpawnRates.MOD_ID, "reset"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ResetPayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, ResetPayload::target,
				ResetPayload::new);

		@Override
		public Type<ResetPayload> type() {
			return TYPE;
		}
	}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(SyncPayload.TYPE, SyncPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SetRatePayload.TYPE, SetRatePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ResetPayload.TYPE, ResetPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(SetRatePayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (!isOperator(player)) {
				return;
			}
			Kind kind = payload.loot() ? Kind.LOOT : Kind.SPAWN;
			if (GLOBAL.equals(payload.target())) {
				RatesConfig.setGlobal(kind, payload.value());
			} else {
				EntityType<?> type = parseMob(payload.target());
				if (type != null) {
					RatesConfig.setMob(type, kind, payload.value());
				}
			}
		});

		ServerPlayNetworking.registerGlobalReceiver(ResetPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (!isOperator(player)) {
				return;
			}
			switch (payload.target()) {
				case ALL -> RatesConfig.resetAll();
				case GLOBAL -> {
					RatesConfig.setGlobal(Kind.SPAWN, 1.0);
					RatesConfig.setGlobal(Kind.LOOT, 1.0);
				}
				default -> {
					EntityType<?> type = parseMob(payload.target());
					if (type != null) {
						RatesConfig.resetMob(type);
					}
				}
			}
			sendSync(player, false);
		});
	}

	public static boolean isOperator(ServerPlayer player) {
		return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}

	/** True if the player's client has this mod and can show the slider screen. */
	public static boolean canUseSliderScreen(ServerPlayer player) {
		return ServerPlayNetworking.canSend(player, SyncPayload.TYPE);
	}

	public static void sendSync(ServerPlayer player, boolean open) {
		List<MobEntry> mobs = new ArrayList<>();
		RatesConfig.snapshotMobs().forEach((id, rates) -> mobs.add(new MobEntry(id, rates[0], rates[1])));
		ServerPlayNetworking.send(player, new SyncPayload(open,
				RatesConfig.getGlobal(Kind.SPAWN), RatesConfig.getGlobal(Kind.LOOT), mobs));
	}

	@Nullable
	private static EntityType<?> parseMob(String id) {
		Identifier identifier = Identifier.tryParse(id);
		if (identifier == null) {
			return null;
		}
		Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(identifier);
		return type.orElse(null);
	}
}
