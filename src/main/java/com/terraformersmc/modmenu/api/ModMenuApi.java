package com.terraformersmc.modmenu.api;

/**
 * Compile-time copy of Mod Menu's entrypoint interface (same name and method), so the Flipper can
 * offer its settings screen without depending on Mod Menu. Excluded from the jar (build.gradle);
 * at run time the real interface comes with Mod Menu, and without Mod Menu this is never loaded.
 */
public interface ModMenuApi {
	default ConfigScreenFactory<?> getModConfigScreenFactory() {
		return screen -> null;
	}
}
