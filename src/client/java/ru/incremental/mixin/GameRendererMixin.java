package ru.incremental.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import ru.incremental.tas.TasFreeze;

/**
 * Marks the beginning of a rendered frame so frame stepping only advances once per frame.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "render", at = @At("HEAD"))
	private void incremental$beginFrame(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
		TasFreeze.beginRender();
	}
}
