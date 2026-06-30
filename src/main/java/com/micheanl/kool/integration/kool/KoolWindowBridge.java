package com.micheanl.kool.integration.kool;

import java.nio.file.Path;
import java.util.List;

public final class KoolWindowBridge {
	private static volatile MinecraftKoolWindow window;

	private KoolWindowBridge() {
	}

	public static void setWindow(MinecraftKoolWindow nextWindow) {
		window = nextWindow;
	}

	public static void clearWindow(MinecraftKoolWindow currentWindow) {
		if (window == currentWindow) {
			window = null;
		}
	}

	public static void handleFileDrop(List<Path> files) {
		MinecraftKoolWindow currentWindow = window;
		if (currentWindow != null) {
			currentWindow.handleFileDrop(files);
		}
	}
}
