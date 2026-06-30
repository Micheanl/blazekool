package com.micheanl.kool.integration.kool

import org.lwjgl.glfw.GLFW

object MinecraftKoolControllerBridge {
	private var initialized = false

	fun initialize() {
		if (initialized) {
			return
		}
		initialized = true
		var joystickId = GLFW.GLFW_JOYSTICK_1
		while (joystickId <= GLFW.GLFW_JOYSTICK_LAST) {
			if (GLFW.glfwJoystickPresent(joystickId)) {
				KoolControllerBridge.add(MinecraftKoolController(joystickId))
			}
			joystickId++
		}
		GLFW.glfwSetJoystickCallback { id, event ->
			when (event) {
				GLFW.GLFW_CONNECTED -> KoolControllerBridge.add(MinecraftKoolController(id))
				GLFW.GLFW_DISCONNECTED -> KoolControllerBridge.remove(id)
			}
		}
	}

	fun shutdown() {
		if (!initialized) {
			return
		}
		GLFW.glfwSetJoystickCallback(null)
		var joystickId = GLFW.GLFW_JOYSTICK_1
		while (joystickId <= GLFW.GLFW_JOYSTICK_LAST) {
			KoolControllerBridge.remove(joystickId)
			joystickId++
		}
		initialized = false
	}
}
