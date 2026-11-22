package ru.incremental.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import ru.incremental.tas.TasFreeze;
import ru.incremental.tas.TasSession;

/**
 * Keeps the freeze state in sync with the game and stops everything when the world is left.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "setScreen", at = @At("TAIL"))
	private void incremental$onScreenChanged(Screen screen, CallbackInfo ci) {
		Minecraft client = (Minecraft) (Object) this;
		if (client.level == null || client.getSingleplayerServer() == null) {
			return;
		}
		TasFreeze.applyScreenState(screen instanceof ru.incremental.screen.TasScreen);
	}

	@Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"))
	private void incremental$onDisconnect(Screen screen, boolean keepResourcePacks, boolean stopSound, CallbackInfo ci) {
		TasFreeze.shutdown();
		TasSession.worldUnloaded();
	}
}
