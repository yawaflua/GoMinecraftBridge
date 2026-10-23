package dev.yawaflua.gominecraftbridge.client;

import dev.yawaflua.gominecraftbridge.compat.MinecraftVersionAdapter;
import dev.yawaflua.gominecraftbridge.protocol.ClientItem;
import dev.yawaflua.gominecraftbridge.protocol.ClientMovementKeys;
import dev.yawaflua.gominecraftbridge.protocol.ClientPlayerState;
import dev.yawaflua.gominecraftbridge.protocol.ClientScreenState;
import dev.yawaflua.gominecraftbridge.protocol.ClientTarget;
import dev.yawaflua.gominecraftbridge.protocol.ClientTickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.time.Instant;

final class ClientTickSnapshots {
	private ClientTickSnapshots() {
	}

	static ClientTickEvent create(Minecraft client, long tick, ClientScreenController screens) {
		boolean connected = client.getConnection() != null;
		String address = connected
				? String.valueOf(client.getConnection().getConnection().getRemoteAddress())
				: null;
		LocalPlayer player = client.player;
		String dimension = client.level == null ? null : MinecraftVersionAdapter.dimension(client.level);
		long dayTime = client.level == null ? 0L : MinecraftVersionAdapter.clientDayTime(client.level);
		return new ClientTickEvent(
				tick, Instant.now().toEpochMilli(), connected, address,
				player == null ? null : player.getUUID().toString(),
				player == null ? null : player.getName().getString(),
				dimension, player != null,
				player == null ? 0.0 : player.getX(),
				player == null ? 0.0 : player.getY(),
				player == null ? 0.0 : player.getZ(),
				dayTime, client.getFps(),
				client.getWindow().getGuiScaledWidth(),
				client.getWindow().getGuiScaledHeight(),
				screen(client.screen, screens),
				target(client, player),
				keys(client.options),
				player(player)
		);
	}

	private static ClientScreenState screen(Screen screen, ClientScreenController screens) {
		if (screen == null) {
			return null;
		}
		if (screen instanceof ClientFormScreen form) {
			return new ClientScreenState("plugin", screens.activePluginId(), form.screenId());
		}
		String kind;
		if (screen instanceof ChatScreen) {
			kind = "chat";
		} else if (screen instanceof AbstractContainerScreen<?>) {
			kind = "container";
		} else if (screen instanceof PauseScreen) {
			kind = "pause";
		} else if (screen instanceof TitleScreen) {
			kind = "title";
		} else {
			kind = "other";
		}
		return new ClientScreenState(kind, null, null);
	}

	private static ClientTarget target(Minecraft client, LocalPlayer player) {
		HitResult hit = client.hitResult;
		if (player == null || hit == null || hit.getType() == HitResult.Type.MISS) {
			return null;
		}
		Vec3 location = hit.getLocation();
		double distance = player.getEyePosition().distanceTo(location);
		if (hit instanceof BlockHitResult block && client.level != null) {
			BlockPos pos = block.getBlockPos();
			String blockId = BuiltInRegistries.BLOCK.getKey(client.level.getBlockState(pos).getBlock()).toString();
			return new ClientTarget(
					"block", distance, location.x, location.y, location.z,
					pos.getX(), pos.getY(), pos.getZ(), blockId, block.getDirection().getSerializedName(),
					null, null, null, false
			);
		}
		if (hit instanceof EntityHitResult entityHit) {
			Entity entity = entityHit.getEntity();
			return new ClientTarget(
					"entity", distance, entity.getX(), entity.getY(), entity.getZ(),
					0, 0, 0, null, null,
					entity.getStringUUID(),
					BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),
					entity.getName().getString(),
					entity instanceof Player
			);
		}
		return null;
	}

	private static ClientMovementKeys keys(Options options) {
		if (options == null) {
			return ClientMovementKeys.NONE;
		}
		return new ClientMovementKeys(
				options.keyUp.isDown(), options.keyDown.isDown(), options.keyLeft.isDown(), options.keyRight.isDown(),
				options.keyJump.isDown(), options.keyShift.isDown(), options.keySprint.isDown(),
				options.keyAttack.isDown(), options.keyUse.isDown()
		);
	}

	private static ClientPlayerState player(LocalPlayer player) {
		if (player == null) {
			return null;
		}
		return new ClientPlayerState(
				player.getHealth(), player.getMaxHealth(), player.getFoodData().getFoodLevel(),
				player.getYRot(), player.getXRot(), player.isShiftKeyDown(), player.isSprinting(),
				item(player.getMainHandItem()), item(player.getOffhandItem())
		);
	}

	static ClientItem item(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return ClientItem.EMPTY;
		}
		return new ClientItem(
				BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
				stack.getCount(),
				stack.getHoverName().getString()
		);
	}
}
