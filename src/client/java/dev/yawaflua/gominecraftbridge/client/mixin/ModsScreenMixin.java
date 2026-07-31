package dev.yawaflua.gominecraftbridge.client.mixin;

import dev.yawaflua.gominecraftbridge.client.GoMinecraftBridgeClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.terraformersmc.modmenu.gui.ModsScreen")
public abstract class ModsScreenMixin {
	@Inject(method = "init", at = @At("HEAD"))
	private void goMinecraftBridge$addNativePlugins(CallbackInfo callbackInfo) {
		GoMinecraftBridgeClient.runtime().refreshModMenu();
	}
}
