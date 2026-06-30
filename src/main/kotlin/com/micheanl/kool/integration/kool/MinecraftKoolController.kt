package com.micheanl.kool.integration.kool

import de.fabmax.kool.input.Controller
import de.fabmax.kool.input.ControllerAxis
import de.fabmax.kool.input.ControllerButton
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWGamepadState
import kotlin.math.min

class MinecraftKoolController(id: Int) : Controller(id) {
	private val gamepadState: GLFWGamepadState = GLFWGamepadState.calloc()

	override var isConnected: Boolean = true
		private set

	override val isGamepad: Boolean = GLFW.glfwJoystickIsGamepad(id)

	override val name: String = if (isGamepad) {
		GLFW.glfwGetGamepadName(id) ?: "minecraft-gamepad-$id"
	} else {
		GLFW.glfwGetJoystickName(id) ?: "minecraft-controller-$id"
	}

	override val buttonStates: BooleanArray = if (isGamepad) {
		BooleanArray(STANDARD_LAYOUT_NUM_BUTTONS)
	} else {
		BooleanArray(GLFW.glfwGetJoystickButtons(id)?.capacity() ?: 0)
	}

	override val axisStates: FloatArray = if (isGamepad) {
		FloatArray(STANDARD_LAYOUT_NUM_AXES)
	} else {
		FloatArray(GLFW.glfwGetJoystickAxes(id)?.capacity() ?: 0)
	}

	override fun updateState() {
		if (isGamepad && GLFW.glfwGetGamepadState(id, gamepadState)) {
			updateGamepadState()
		} else {
			updateJoystickState()
		}
	}

	override fun onDisconnect() {
		isConnected = false
		gamepadState.free()
		super.onDisconnect()
	}

	private fun updateGamepadState() {
		setButtonState(ControllerButton.A, gamepadState.buttons(GLFW.GLFW_GAMEPAD_BUTTON_A) == GLFW.GLFW_PRESS.toByte())
		setButtonState(ControllerButton.B, gamepadState.buttons(GLFW.GLFW_GAMEPAD_BUTTON_B) == GLFW.GLFW_PRESS.toByte())
		setButtonState(ControllerButton.X, gamepadState.buttons(GLFW.GLFW_GAMEPAD_BUTTON_X) == GLFW.GLFW_PRESS.toByte())
		setButtonState(ControllerButton.Y, gamepadState.buttons(GLFW.GLFW_GAMEPAD_BUTTON_Y) == GLFW.GLFW_PRESS.toByte())
		setButtonState(ControllerButton.DPAD_LEFT, gamepadState.buttons(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_LEFT) == GLFW.GLFW_PRESS.toByte())
		setButtonState(ControllerButton.DPAD_RIGHT, gamepadState.buttons(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_RIGHT) == GLFW.GLFW_PRESS.toByte())
		setButtonState(ControllerButton.DPAD_UP, gamepadState.buttons(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_UP) == GLFW.GLFW_PRESS.toByte())
		setButtonState(ControllerButton.DPAD_DOWN, gamepadState.buttons(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_DOWN) == GLFW.GLFW_PRESS.toByte())
		setButtonState(ControllerButton.SHOULDER_LEFT, gamepadState.buttons(GLFW.GLFW_GAMEPAD_BUTTON_LEFT_BUMPER) == GLFW.GLFW_PRESS.toByte())
		setButtonState(ControllerButton.SHOULDER_RIGHT, gamepadState.buttons(GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER) == GLFW.GLFW_PRESS.toByte())
		setButtonState(ControllerButton.THUMB_LEFT, gamepadState.buttons(GLFW.GLFW_GAMEPAD_BUTTON_LEFT_THUMB) == GLFW.GLFW_PRESS.toByte())
		setButtonState(ControllerButton.THUMB_RIGHT, gamepadState.buttons(GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_THUMB) == GLFW.GLFW_PRESS.toByte())
		setButtonState(ControllerButton.START, gamepadState.buttons(GLFW.GLFW_GAMEPAD_BUTTON_START) == GLFW.GLFW_PRESS.toByte())
		setButtonState(ControllerButton.BACK, gamepadState.buttons(GLFW.GLFW_GAMEPAD_BUTTON_BACK) == GLFW.GLFW_PRESS.toByte())
		setButtonState(ControllerButton.GUIDE, gamepadState.buttons(GLFW.GLFW_GAMEPAD_BUTTON_GUIDE) == GLFW.GLFW_PRESS.toByte())
		setAxisState(ControllerAxis.LEFT_X, gamepadState.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_X))
		setAxisState(ControllerAxis.LEFT_Y, gamepadState.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y))
		setAxisState(ControllerAxis.RIGHT_X, gamepadState.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X))
		setAxisState(ControllerAxis.RIGHT_Y, gamepadState.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y))
		setAxisState(ControllerAxis.TRIGGER_LEFT, gamepadState.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER) * 0.5f + 0.5f)
		setAxisState(ControllerAxis.TRIGGER_RIGHT, gamepadState.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER) * 0.5f + 0.5f)
	}

	private fun updateJoystickState() {
		GLFW.glfwGetJoystickButtons(id)?.let { buttons ->
			var index = 0
			val count = min(buttonStates.size, buttons.capacity())
			while (index < count) {
				buttonStates[index] = buttons[index] == GLFW.GLFW_PRESS.toByte()
				index++
			}
		}
		GLFW.glfwGetJoystickAxes(id)?.let { axes ->
			var index = 0
			val count = min(axisStates.size, axes.capacity())
			while (index < count) {
				axisStates[index] = axes[index]
				index++
			}
		}
	}
}
