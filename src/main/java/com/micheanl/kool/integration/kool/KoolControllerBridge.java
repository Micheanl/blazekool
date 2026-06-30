package com.micheanl.kool.integration.kool;

import de.fabmax.kool.input.Controller;
import de.fabmax.kool.input.ControllerInput;

public final class KoolControllerBridge {
	private KoolControllerBridge() {
	}

	public static void add(Controller controller) {
		ControllerInput.INSTANCE.addController$kool_core(controller);
	}

	public static void remove(int controllerId) {
		ControllerInput.INSTANCE.removeController$kool_core(controllerId);
	}
}
