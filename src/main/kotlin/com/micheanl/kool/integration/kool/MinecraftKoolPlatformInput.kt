package com.micheanl.kool.integration.kool

import com.mojang.blaze3d.platform.cursor.CursorType
import com.mojang.blaze3d.platform.cursor.CursorTypes
import de.fabmax.kool.input.CursorMode
import de.fabmax.kool.input.CursorShape
import de.fabmax.kool.input.PlatformInput
import net.minecraft.client.Minecraft

class MinecraftKoolPlatformInput(
	private val client: Minecraft
) : PlatformInput {
	override fun setCursorMode(cursorMode: CursorMode) {
		executeOnClientThread {
			when (cursorMode) {
				CursorMode.NORMAL -> client.mouseHandler.releaseMouse()
				CursorMode.LOCKED -> client.mouseHandler.grabMouse()
			}
		}
	}

	override fun applyCursorShape(cursorShape: CursorShape) {
		executeOnClientThread {
			client.window.selectCursor(toMinecraftCursor(cursorShape))
		}
	}

	private fun executeOnClientThread(action: () -> Unit) {
		if (client.isSameThread) {
			action()
		} else {
			client.execute(action)
		}
	}

	private fun toMinecraftCursor(cursorShape: CursorShape): CursorType {
		return when (cursorShape) {
			CursorShape.DEFAULT -> CursorTypes.ARROW
			CursorShape.TEXT -> CursorTypes.IBEAM
			CursorShape.CROSSHAIR -> CursorTypes.CROSSHAIR
			CursorShape.HAND -> CursorTypes.POINTING_HAND
			CursorShape.NOT_ALLOWED -> CursorTypes.NOT_ALLOWED
			CursorShape.MOVE -> CursorTypes.RESIZE_ALL
			CursorShape.RESIZE_E -> CursorTypes.RESIZE_EW
			CursorShape.RESIZE_W -> CursorTypes.RESIZE_EW
			CursorShape.RESIZE_N -> CursorTypes.RESIZE_NS
			CursorShape.RESIZE_S -> CursorTypes.RESIZE_NS
			CursorShape.RESIZE_NW -> CursorTypes.RESIZE_ALL
			CursorShape.RESIZE_SE -> CursorTypes.RESIZE_ALL
			CursorShape.RESIZE_NE -> CursorTypes.RESIZE_ALL
			CursorShape.RESIZE_SW -> CursorTypes.RESIZE_ALL
		}
	}
}
