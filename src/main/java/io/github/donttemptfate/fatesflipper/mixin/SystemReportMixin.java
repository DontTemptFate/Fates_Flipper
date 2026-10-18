package io.github.donttemptfate.fatesflipper.mixin;

import io.github.donttemptfate.fatesflipper.FatesFlipperClient;
import io.github.donttemptfate.fatesflipper.FlipService;
import io.github.donttemptfate.fatesflipper.core.ModConfig;
import net.minecraft.CrashReportDetail;
import net.minecraft.SystemReport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A "Fates Flipper" section in crash reports (audit B-4): version, Minecraft build, which features
 * were on and the connection state, so a crash log says enough to reproduce it. Never the token or
 * the server address.
 */
@Mixin(SystemReport.class)
public abstract class SystemReportMixin {
	@Shadow
	public abstract void setDetail(String key, CrashReportDetail<Object> valueSupplier);

	@Inject(at = @At("RETURN"), method = "<init>")
	private void fatesflipper$details(CallbackInfo info) {
		setDetail("Fates Flipper", () -> {
			FlipService s = FatesFlipperClient.service();
			if (s == null) return "not started";
			ModConfig c = s.config();
			return s.modVersion() + " for Minecraft " + io.github.donttemptfate.fatesflipper.compat.Compat.MINECRAFT
					+ "; set up " + c.isReady() + ", on Hypixel " + s.onHypixel()
					+ ", stream " + s.streamState() + (s.updateRequired() != null ? " (update required)" : "")
					+ "; alerts " + c.alerts + ", skyblock-only " + c.onlyInSkyBlock + ", tooltips " + c.tooltips
					+ ", overlay " + c.auctionOverlay + ", look " + c.fatesLook + ", fates tooltips " + c.fatesTooltips
					+ ", chakra " + c.chakraFont + ", cursor assist " + c.cursorAssist + ", close key " + c.pressAgainToClose;
		});
	}
}
