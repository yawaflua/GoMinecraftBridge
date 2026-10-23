package dev.yawaflua.gominecraftbridge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ClientVersionAdapter {
	private ClientVersionAdapter() {
	}

	public static void toast(Minecraft client, String title, String description, long durationMillis) {
		SystemToast.add(
				client.getToastManager(),
				new SystemToast.SystemToastId(durationMillis),
				Component.literal(title),
				description == null || description.isBlank() ? null : Component.literal(description)
		);
	}

	public static void playSound(Minecraft client, String soundId, float volume, float pitch) {
		Identifier id = parse(soundId);
		SoundEvent sound = SoundEvent.createVariableRangeEvent(id);
		client.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
	}

	public static ItemStack itemStack(String itemId, int count) {
		Identifier id = Identifier.tryParse(itemId == null ? "" : itemId);
		Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(id);
		return new ItemStack(item, Math.max(1, count));
	}

	static Identifier parse(String value) {
		Identifier id = Identifier.tryParse(value == null ? "" : value);
		if (id == null) {
			throw new IllegalArgumentException("Invalid resource id: " + value);
		}
		return id;
	}
}
