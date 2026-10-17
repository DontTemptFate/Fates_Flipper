package io.github.donttemptfate.fatesflipper.ui;

/** Implemented by a mixin on GuiGraphicsExtractor: sets this frame's tooltip (drawn last, on top). */
public interface DeferredTooltip {
	void fatesflipper$setDeferredTooltip(Runnable r);
}
