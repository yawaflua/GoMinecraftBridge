package dev.yawaflua.gominecraftbridge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class ClientChatDisplay {
	private ClientChatDisplay() {
	}

	public static void display(Minecraft client, Component message) {
		client.player.sendSystemMessage(message);
	}
}
