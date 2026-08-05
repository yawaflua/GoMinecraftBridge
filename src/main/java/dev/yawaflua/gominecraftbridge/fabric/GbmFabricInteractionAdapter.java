package dev.yawaflua.gominecraftbridge.fabric;

import dev.yawaflua.gominecraftbridge.host.MinecraftSnapshotFactory;
import dev.yawaflua.gominecraftbridge.protocol.InteractionEvent;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.time.Instant;

public final class GbmFabricInteractionAdapter {
	private final MinecraftSnapshotFactory snapshots = new MinecraftSnapshotFactory();
	private final boolean clientSide;
	private final Sink sink;

	private GbmFabricInteractionAdapter(boolean clientSide, Sink sink) {
		this.clientSide = clientSide;
		this.sink = sink;
	}

	public static void register(boolean clientSide, Sink sink) {
		GbmFabricInteractionAdapter adapter = new GbmFabricInteractionAdapter(clientSide, sink);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() == adapter.clientSide) {
				Vec3 location = hit.getLocation();
				adapter.sink.accept(new InteractionEvent(
						"use_block", hand(hand), player.isShiftKeyDown(), adapter.snapshots.entity(player),
						adapter.snapshots.block(level, hit.getBlockPos()), null,
						hit.getDirection().getSerializedName(), location.x, location.y, location.z,
						Instant.now().toEpochMilli()
				), player);
			}
			return InteractionResult.PASS;
		});
		AttackBlockCallback.EVENT.register((player, level, hand, position, direction) -> {
			if (level.isClientSide() == adapter.clientSide) {
				adapter.sink.accept(new InteractionEvent(
						"attack_block", hand(hand), player.isShiftKeyDown(), adapter.snapshots.entity(player),
						adapter.snapshots.block(level, position), null, direction.getSerializedName(),
						null, null, null, Instant.now().toEpochMilli()
				), player);
			}
			return InteractionResult.PASS;
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() == adapter.clientSide) {
				adapter.entity("use_entity", player, hand, entity, hit);
			}
			return InteractionResult.PASS;
		});
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() == adapter.clientSide) {
				adapter.entity("attack_entity", player, hand, entity, hit);
			}
			return InteractionResult.PASS;
		});
	}

	private void entity(
			String action,
			Player player,
			InteractionHand hand,
			net.minecraft.world.entity.Entity target,
			EntityHitResult hit
	) {
		Vec3 location = hit == null ? null : hit.getLocation();
		this.sink.accept(new InteractionEvent(
				action, hand(hand), player.isShiftKeyDown(), this.snapshots.entity(player), null,
				this.snapshots.entity(target), null,
				location == null ? null : location.x,
				location == null ? null : location.y,
				location == null ? null : location.z,
				Instant.now().toEpochMilli()
		), player);
	}

	private static String hand(InteractionHand hand) {
		return hand == InteractionHand.MAIN_HAND ? "main_hand" : "off_hand";
	}

	@FunctionalInterface
	public interface Sink {
		void accept(InteractionEvent event, Player player);
	}
}
