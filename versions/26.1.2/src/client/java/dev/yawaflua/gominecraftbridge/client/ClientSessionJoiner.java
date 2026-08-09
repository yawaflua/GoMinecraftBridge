package dev.yawaflua.gominecraftbridge.client;

import com.mojang.authlib.exceptions.AuthenticationException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

/** Minecraft 26.1 authenticated-session adapter. */
public final class ClientSessionJoiner {
	private ClientSessionJoiner() {
	}

	public static void join(Minecraft client, String serverId) throws AuthenticationException {
		User user = client.getUser();
		client.services().sessionService().joinServer(user.getProfileId(), user.getAccessToken(), serverId);
	}
}
