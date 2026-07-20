package dev.yawaflua.gominecraftbridge.network;

import dev.yawaflua.gominecraftbridge.GoMinecraftBridgeMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AdminRequestPayload(String action, String pluginId) implements CustomPacketPayload {
	public static final Type<AdminRequestPayload> TYPE = new Type<>(
			ResourceLocation.fromNamespaceAndPath(GoMinecraftBridgeMod.MOD_ID, "admin_request")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, AdminRequestPayload> CODEC =
			CustomPacketPayload.codec(AdminRequestPayload::write, AdminRequestPayload::new);

	public AdminRequestPayload(RegistryFriendlyByteBuf buffer) {
		this(buffer.readUtf(32), buffer.readUtf(64));
	}

	private void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeUtf(this.action, 32);
		buffer.writeUtf(this.pluginId == null ? "" : this.pluginId, 64);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
