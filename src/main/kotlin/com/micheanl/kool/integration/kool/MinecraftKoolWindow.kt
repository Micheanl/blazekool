package com.micheanl.kool.integration.kool

import de.fabmax.kool.DragAndDropListener
import de.fabmax.kool.ScaleChangeListener
import de.fabmax.kool.WindowCapabilities
import de.fabmax.kool.WindowCloseListener
import de.fabmax.kool.WindowFlags
import de.fabmax.kool.WindowFlagsListener
import de.fabmax.kool.WindowResizeListener
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.platform.KoolWindowJvm
import de.fabmax.kool.util.BufferedList
import de.fabmax.kool.util.WindowTitleHoverHandler
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.KHRSurface
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VkInstance

class MinecraftKoolWindow(
	private val client: Minecraft
) : KoolWindowJvm {
	private var currentTitle: String = "BlazeKool"

	override val parentScreenScale: Float
		get() = client.window.guiScale.toFloat()

	override var positionInScreen: Vec2i
		get() = Vec2i(client.window.x, client.window.y)
		set(value) {
			GLFW.glfwSetWindowPos(client.window.handle(), value.x, value.y)
		}

	override var sizeOnScreen: Vec2i
		get() = Vec2i(client.window.width, client.window.height)
		set(value) {
			GLFW.glfwSetWindowSize(client.window.handle(), value.x, value.y)
		}

	override var renderResolutionFactor: Float = 1.0f

	override val framebufferSize: Vec2i
		get() = Vec2i(client.gameRenderer.mainRenderTarget().width, client.gameRenderer.mainRenderTarget().height)

	override val size: Vec2i
		get() = framebufferSize

	override val renderScale: Float
		get() = parentScreenScale / renderResolutionFactor

	override var title: String
		get() = currentTitle
		set(value) {
			currentTitle = value
			client.window.setTitle(value)
		}

	override val flags: WindowFlags
		get() = WindowFlags(
			isFullscreen = client.window.isFullscreen,
			isMaximized = false,
			isMinimized = client.window.isMinimized,
			isVisible = true,
			isFocused = client.window.isFocused,
			isHiddenTitleBar = false
		)

	override val capabilities: WindowCapabilities = WindowCapabilities.NONE
	override val resizeListeners: BufferedList<WindowResizeListener> = BufferedList()
	override val scaleChangeListeners: BufferedList<ScaleChangeListener> = BufferedList()
	override val flagListeners: BufferedList<WindowFlagsListener> = BufferedList()
	override val closeListeners: BufferedList<WindowCloseListener> = BufferedList()
	override val dragAndDropListeners: BufferedList<DragAndDropListener> = BufferedList()
	override var windowTitleHoverHandler: WindowTitleHoverHandler = WindowTitleHoverHandler()

	override val isMouseOverWindow: Boolean
		get() {
			val cursorX = DoubleArray(1)
			val cursorY = DoubleArray(1)
			GLFW.glfwGetCursorPos(client.window.handle(), cursorX, cursorY)
			return cursorX[0] >= 0.0 &&
				cursorY[0] >= 0.0 &&
				cursorX[0] < client.window.screenWidth &&
				cursorY[0] < client.window.screenHeight
		}

	override fun close() {
		closeListeners.update()
		for (index in closeListeners.indices) {
			if (!closeListeners[index].onCloseRequest()) {
				return
			}
		}
	}

	override fun pollEvents() {
		GLFW.glfwPollEvents()
	}

	override fun createVulkanSurface(instance: VkInstance): Long {
		MemoryStack.stackPush().use { stack ->
			val surface = stack.mallocLong(1)
			val result = GLFWVulkan.glfwCreateWindowSurface(instance, client.window.handle(), null, surface)
			check(result == VK10.VK_SUCCESS) { "Failed to create Vulkan surface for Minecraft window: $result" }
			return surface.get(0)
		}
	}

	override fun destroyVulkanSurface(surface: Long, instance: VkInstance) {
		KHRSurface.vkDestroySurfaceKHR(instance, surface, null)
	}

	override fun swapBuffers() {
		GLFW.glfwSwapBuffers(client.window.handle())
	}

	fun sync() {
		val currentSize = framebufferSize
		resizeListeners.update()
		for (index in resizeListeners.indices) {
			resizeListeners[index].onResize(currentSize)
		}
	}
}
