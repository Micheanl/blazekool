package com.micheanl.kool.integration.kool;

import de.fabmax.kool.KoolContext;
import de.fabmax.kool.KoolSystem;

public final class KoolSystemBridge {
	private KoolSystemBridge() {
	}

	public static void onContextCreated(KoolContext context) {
		KoolSystem.INSTANCE.onContextCreated$kool_core(context);
	}
}
