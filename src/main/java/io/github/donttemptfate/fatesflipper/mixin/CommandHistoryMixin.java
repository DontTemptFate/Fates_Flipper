package io.github.donttemptfate.fatesflipper.mixin;

import net.minecraft.client.CommandHistory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code /ff token <token>} is never written to Minecraft's saved command history
 * ({@code command_history.txt}) either (audit F-S2).
 */
@Mixin(CommandHistory.class)
public abstract class CommandHistoryMixin {
	@Inject(method = "addCommand", at = @At("HEAD"), cancellable = true)
	private void fatesflipper$skipToken(String command, CallbackInfo ci) {
		if (io.github.donttemptfate.fatesflipper.core.ServerAddress.isTokenCommand(command)) ci.cancel();
	}
}
