package dev.yawaflua.gominecraftbridge.network;

import dev.yawaflua.gominecraftbridge.GoMinecraftBridgeMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record AdminResponsePayload(String json) implements CustomPacketPayload {
	public static final int MAX_JSON_CHARS = 2 * 1024 * 1024;
	public static final Type<AdminResponsePayload> TYPE = new Type<>(
			Identifier.fromNamespaceAndPath(GoMinecraftBridgeMod.MOD_ID, "admin_response")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, AdminResponsePayload> CODEC =
			CustomPacketPayload.codec(AdminResponsePayload::write, AdminResponsePayload::new);

	public AdminResponsePayload(RegistryFriendlyByteBuf buffer) {
		this(buffer.readUtf(MAX_JSON_CHARS));
	}

	private void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeUtf(this.json, MAX_JSON_CHARS);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
