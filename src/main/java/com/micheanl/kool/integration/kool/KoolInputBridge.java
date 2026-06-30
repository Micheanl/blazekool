package com.micheanl.kool.integration.kool;

import de.fabmax.kool.KoolSystem;
import de.fabmax.kool.input.KeyCode;
import de.fabmax.kool.input.KeyEvent;
import de.fabmax.kool.input.KeyboardInput;
import de.fabmax.kool.input.LocalKeyCode;
import de.fabmax.kool.input.PointerInput;
import de.fabmax.kool.input.UniversalKeyCode;
import net.minecraft.client.input.InputQuirks;
import org.lwjgl.glfw.GLFW;

public final class KoolInputBridge {
	private KoolInputBridge() {
	}

	public static void handleMouseMove(double x, double y, float scale) {
		if (!KoolSystem.INSTANCE.isInitialized()) {
			return;
		}
		PointerInput.INSTANCE.handleMouseMove$kool_core((float)x * scale, (float)y * scale);
	}

	public static void handleMouseButton(int button, int action) {
		if (!KoolSystem.INSTANCE.isInitialized()) {
			return;
		}
		if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_RELEASE) {
			PointerInput.INSTANCE.handleMouseButtonEvent$kool_core(button, action == GLFW.GLFW_PRESS);
		}
	}

	public static void handleMouseScroll(double xOffset, double yOffset) {
		if (!KoolSystem.INSTANCE.isInitialized()) {
			return;
		}
		PointerInput.INSTANCE.handleMouseScroll$kool_core((float)xOffset, (float)yOffset);
	}

	public static void handleMouseExit() {
		if (!KoolSystem.INSTANCE.isInitialized()) {
			return;
		}
		PointerInput.INSTANCE.handleMouseExit$kool_core();
	}

	public static void handleKey(int key, int scancode, int action, int modifiers) {
		if (!KoolSystem.INSTANCE.isInitialized()) {
			return;
		}
		int event = toKoolKeyEvent(action);
		if (event == 0) {
			return;
		}

		KeyCode keyCode = toKoolKeyCode(key);
		KeyCode localKeyCode = new LocalKeyCode(scancode != 0 ? scancode : key, null);
		int koolModifiers = toKoolModifiers(modifiers);
		koolModifiers = updateModifierKeyMask(key, event, koolModifiers);
		KeyboardInput.INSTANCE.handleKeyEvent(new KeyEvent(keyCode, localKeyCode, event, koolModifiers, (char)0));
	}

	public static void handleCharTyped(int codepoint) {
		if (!KoolSystem.INSTANCE.isInitialized()) {
			return;
		}
		if (Character.isValidCodePoint(codepoint) && !Character.isSupplementaryCodePoint(codepoint)) {
			KeyboardInput.INSTANCE.handleCharTyped((char)codepoint);
		}
	}

	private static int toKoolKeyEvent(int action) {
		return switch (action) {
			case GLFW.GLFW_PRESS -> KeyboardInput.KEY_EV_DOWN;
			case GLFW.GLFW_REPEAT -> KeyboardInput.KEY_EV_DOWN | KeyboardInput.KEY_EV_REPEATED;
			case GLFW.GLFW_RELEASE -> KeyboardInput.KEY_EV_UP;
			default -> 0;
		};
	}

	private static int toKoolModifiers(int modifiers) {
		int koolModifiers = 0;
		if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
			koolModifiers |= KeyboardInput.KEY_MOD_SHIFT;
		}
		if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 || (modifiers & InputQuirks.EDIT_SHORTCUT_KEY_MODIFIER) != 0) {
			koolModifiers |= KeyboardInput.KEY_MOD_CTRL;
		}
		if ((modifiers & GLFW.GLFW_MOD_ALT) != 0) {
			koolModifiers |= KeyboardInput.KEY_MOD_ALT;
		}
		if ((modifiers & GLFW.GLFW_MOD_SUPER) != 0) {
			koolModifiers |= KeyboardInput.KEY_MOD_SUPER;
		}
		return koolModifiers;
	}

	private static int updateModifierKeyMask(int key, int event, int modifiers) {
		return switch (key) {
			case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT -> updateDownMask(modifiers, KeyboardInput.KEY_MOD_SHIFT, event);
			case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL -> updateDownMask(modifiers, KeyboardInput.KEY_MOD_CTRL, event);
			case GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT -> updateDownMask(modifiers, KeyboardInput.KEY_MOD_ALT, event);
			case GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER -> updateDownMask(modifiers, KeyboardInput.KEY_MOD_SUPER, event);
			default -> modifiers;
		};
	}

	private static int updateDownMask(int mask, int bit, int event) {
		if ((event & KeyboardInput.KEY_EV_DOWN) != 0) {
			return mask | bit;
		}
		return mask & ~bit;
	}

	private static KeyCode toKoolKeyCode(int key) {
		return switch (key) {
			case GLFW.GLFW_KEY_LEFT_CONTROL -> KeyboardInput.INSTANCE.getKEY_CTRL_LEFT();
			case GLFW.GLFW_KEY_RIGHT_CONTROL -> KeyboardInput.INSTANCE.getKEY_CTRL_RIGHT();
			case GLFW.GLFW_KEY_LEFT_SHIFT -> KeyboardInput.INSTANCE.getKEY_SHIFT_LEFT();
			case GLFW.GLFW_KEY_RIGHT_SHIFT -> KeyboardInput.INSTANCE.getKEY_SHIFT_RIGHT();
			case GLFW.GLFW_KEY_LEFT_ALT -> KeyboardInput.INSTANCE.getKEY_ALT_LEFT();
			case GLFW.GLFW_KEY_RIGHT_ALT -> KeyboardInput.INSTANCE.getKEY_ALT_RIGHT();
			case GLFW.GLFW_KEY_LEFT_SUPER -> KeyboardInput.INSTANCE.getKEY_SUPER_LEFT();
			case GLFW.GLFW_KEY_RIGHT_SUPER -> KeyboardInput.INSTANCE.getKEY_SUPER_RIGHT();
			case GLFW.GLFW_KEY_ESCAPE -> KeyboardInput.INSTANCE.getKEY_ESC();
			case GLFW.GLFW_KEY_MENU -> KeyboardInput.INSTANCE.getKEY_MENU();
			case GLFW.GLFW_KEY_ENTER -> KeyboardInput.INSTANCE.getKEY_ENTER();
			case GLFW.GLFW_KEY_KP_ENTER -> KeyboardInput.INSTANCE.getKEY_NP_ENTER();
			case GLFW.GLFW_KEY_KP_DIVIDE -> KeyboardInput.INSTANCE.getKEY_NP_DIV();
			case GLFW.GLFW_KEY_KP_MULTIPLY -> KeyboardInput.INSTANCE.getKEY_NP_MUL();
			case GLFW.GLFW_KEY_KP_ADD -> KeyboardInput.INSTANCE.getKEY_NP_PLUS();
			case GLFW.GLFW_KEY_KP_SUBTRACT -> KeyboardInput.INSTANCE.getKEY_NP_MINUS();
			case GLFW.GLFW_KEY_KP_DECIMAL -> KeyboardInput.INSTANCE.getKEY_NP_DECIMAL();
			case GLFW.GLFW_KEY_BACKSPACE -> KeyboardInput.INSTANCE.getKEY_BACKSPACE();
			case GLFW.GLFW_KEY_TAB -> KeyboardInput.INSTANCE.getKEY_TAB();
			case GLFW.GLFW_KEY_DELETE -> KeyboardInput.INSTANCE.getKEY_DEL();
			case GLFW.GLFW_KEY_INSERT -> KeyboardInput.INSTANCE.getKEY_INSERT();
			case GLFW.GLFW_KEY_HOME -> KeyboardInput.INSTANCE.getKEY_HOME();
			case GLFW.GLFW_KEY_END -> KeyboardInput.INSTANCE.getKEY_END();
			case GLFW.GLFW_KEY_PAGE_UP -> KeyboardInput.INSTANCE.getKEY_PAGE_UP();
			case GLFW.GLFW_KEY_PAGE_DOWN -> KeyboardInput.INSTANCE.getKEY_PAGE_DOWN();
			case GLFW.GLFW_KEY_LEFT -> KeyboardInput.INSTANCE.getKEY_CURSOR_LEFT();
			case GLFW.GLFW_KEY_RIGHT -> KeyboardInput.INSTANCE.getKEY_CURSOR_RIGHT();
			case GLFW.GLFW_KEY_UP -> KeyboardInput.INSTANCE.getKEY_CURSOR_UP();
			case GLFW.GLFW_KEY_DOWN -> KeyboardInput.INSTANCE.getKEY_CURSOR_DOWN();
			case GLFW.GLFW_KEY_F1 -> KeyboardInput.INSTANCE.getKEY_F1();
			case GLFW.GLFW_KEY_F2 -> KeyboardInput.INSTANCE.getKEY_F2();
			case GLFW.GLFW_KEY_F3 -> KeyboardInput.INSTANCE.getKEY_F3();
			case GLFW.GLFW_KEY_F4 -> KeyboardInput.INSTANCE.getKEY_F4();
			case GLFW.GLFW_KEY_F5 -> KeyboardInput.INSTANCE.getKEY_F5();
			case GLFW.GLFW_KEY_F6 -> KeyboardInput.INSTANCE.getKEY_F6();
			case GLFW.GLFW_KEY_F7 -> KeyboardInput.INSTANCE.getKEY_F7();
			case GLFW.GLFW_KEY_F8 -> KeyboardInput.INSTANCE.getKEY_F8();
			case GLFW.GLFW_KEY_F9 -> KeyboardInput.INSTANCE.getKEY_F9();
			case GLFW.GLFW_KEY_F10 -> KeyboardInput.INSTANCE.getKEY_F10();
			case GLFW.GLFW_KEY_F11 -> KeyboardInput.INSTANCE.getKEY_F11();
			case GLFW.GLFW_KEY_F12 -> KeyboardInput.INSTANCE.getKEY_F12();
			default -> new UniversalKeyCode(key, null);
		};
	}
}
