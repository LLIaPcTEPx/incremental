package ru.incremental.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;

import net.minecraft.client.gui.render.GuiRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import ru.incremental.tas.TasFreeze;
import ru.incremental.tas.TasStepKeys;

/**
 * While the world is frozen nothing ticks, so the frame-step hotkeys are polled once per rendered
 * frame here instead.
 */
@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {
	@Inject(method = "render", at = @At("HEAD"))
	private void incremental$pollWhileFrozen(GpuBufferSlice fogBuffer, CallbackInfo ci) {
		if (TasFreeze.isFrozen()) {
			TasStepKeys.poll(net.minecraft.client.Minecraft.getInstance());
		}
	}
}
