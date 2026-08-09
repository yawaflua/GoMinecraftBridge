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
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
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
						"use_block", hand(hand), player.isShiftKeyDown(), player.isSprinting(), adapter.snapshots.entity(player),
						adapter.snapshots.block(level, hit.getBlockPos()), null,
						hit.getDirection().getSerializedName(), location.x, location.y, location.z,
						targetTexts(level.getBlockEntity(hit.getBlockPos())), Instant.now().toEpochMilli()
				), player);
			}
			return InteractionResult.PASS;
		});
		AttackBlockCallback.EVENT.register((player, level, hand, position, direction) -> {
			if (level.isClientSide() == adapter.clientSide) {
				adapter.sink.accept(new InteractionEvent(
						"attack_block", hand(hand), player.isShiftKeyDown(), player.isSprinting(), adapter.snapshots.entity(player),
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
				action, hand(hand), player.isShiftKeyDown(), player.isSprinting(), this.snapshots.entity(player), null,
				this.snapshots.entity(target), null,
				location == null ? null : location.x,
				location == null ? null : location.y,
				location == null ? null : location.z,
				targetTexts(target), Instant.now().toEpochMilli()
		), player);
	}

	private static List<String> targetTexts(Object target) {
		List<String> texts = new ArrayList<>();
		if (target instanceof SignBlockEntity sign) {
			for (Component line : sign.getFrontText().getMessages(false)) {
				texts.add(line.getString());
			}
			for (Component line : sign.getBackText().getMessages(false)) {
				texts.add(line.getString());
			}
		} else if (target instanceof ItemFrame frame && !frame.getItem().isEmpty()) {
			texts.add(frame.getItem().getHoverName().getString());
		}
		return texts;
	}

	private static String hand(InteractionHand hand) {
		return hand == InteractionHand.MAIN_HAND ? "main_hand" : "off_hand";
	}

	@FunctionalInterface
	public interface Sink {
		void accept(InteractionEvent event, Player player);
	}
}
