package ru.incremental.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import ru.incremental.tas.TasBlocks;

/** Single block update counterpart of {@link SectionBlocksUpdateMixin}. */
@Mixin(ClientPacketListener.class)
public abstract class BlockUpdateMixin {
	@Inject(method = "handleBlockUpdate", at = @At("HEAD"))
	private void incremental$captureBeforeBlockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || !TasBlocks.isCapturing()) {
			return;
		}
		BlockState before = client.level.getBlockState(packet.getPos());
		if (before != packet.getBlockState()) {
			TasBlocks.note(packet.getPos(), before);
		}
	}
}
