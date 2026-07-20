package dev.yawaflua.gominecraftbridge.network;

import dev.yawaflua.gominecraftbridge.GoMinecraftBridgeMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AdminResponsePayload(String json) implements CustomPacketPayload {
	// 1.21.1 rejects a custom payload at 1 MiB. Leave room for the channel,
	// packet framing, VarInts and UTF-8 expansion.
	public static final int MAX_JSON_CHARS = 900 * 1024;
	public static final Type<AdminResponsePayload> TYPE = new Type<>(
			ResourceLocation.fromNamespaceAndPath(GoMinecraftBridgeMod.MOD_ID, "admin_response")
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
