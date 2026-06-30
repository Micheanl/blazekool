package com.micheanl.kool.integration.kool

import de.fabmax.kool.DragAndDropListener
import de.fabmax.kool.LoadableFile
import de.fabmax.kool.LoadableFileImpl
import de.fabmax.kool.ScaleChangeListener
import de.fabmax.kool.WindowCapabilities
import de.fabmax.kool.WindowCloseListener
import de.fabmax.kool.WindowFlags
import de.fabmax.kool.WindowFlagsListener
import de.fabmax.kool.WindowResizeListener
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.modules.ui2.UiScale
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
import java.nio.file.Path
import kotlin.math.roundToInt

class MinecraftKoolWindow(
	private val client: Minecraft
) : KoolWindowJvm {
	private var currentTitle: String = "BlazeKool"
	private var currentParentScreenScale: Float = readParentScreenScale()
	private var currentFramebufferSize: Vec2i = readFramebufferSize()
	private var currentSize: Vec2i = scaledRenderSize(currentFramebufferSize)

	override val parentScreenScale: Float
		get() = currentParentScreenScale

	override var positionInScreen: Vec2i
		get() = Vec2i(client.window.x, client.window.y)
		set(value) {
			executeOnClientThread {
				GLFW.glfwSetWindowPos(client.window.handle(), value.x, value.y)
				sync()
			}
		}

	override var sizeOnScreen: Vec2i
		get() = Vec2i(client.window.screenWidth, client.window.screenHeight)
		set(value) {
			executeOnClientThread {
				GLFW.glfwSetWindowSize(client.window.handle(), value.x.coerceAtLeast(1), value.y.coerceAtLeast(1))
				sync()
			}
		}

	override var renderResolutionFactor: Float = 1.0f
		set(value) {
			val nextValue = value.coerceAtLeast(MIN_RENDER_RESOLUTION_FACTOR)
			if (nextValue != field) {
				field = nextValue
				updateSizesAndScales(readFramebufferSize(), readParentScreenScale())
			}
		}

	override val framebufferSize: Vec2i
		get() = currentFramebufferSize

	override val size: Vec2i
		get() = currentSize

	override val renderScale: Float
		get() = parentScreenScale * renderResolutionFactor

	override var title: String
		get() = currentTitle
		set(value) {
			currentTitle = value
			executeOnClientThread {
				client.window.setTitle(value)
			}
		}

	override var flags: WindowFlags = readFlags()
		private set(value) {
			if (value != field) {
				val oldFlags = field
				field = value
				flagListeners.update()
				for (index in flagListeners.indices) {
					flagListeners[index].onFlagsChanged(oldFlags, value)
				}
			}
		}

	override val capabilities: WindowCapabilities = WindowCapabilities(
		canSetSize = true,
		canSetPosition = true,
		canSetFullscreen = true,
		canMaximize = true,
		canMinimize = true,
		canSetVisibility = true,
		canSetTitle = true,
		canHideTitleBar = true
	)

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

	init {
		KoolWindowBridge.setWindow(this)
		UiScale.updateUiScaleFromWindowScale(renderScale)
	}

	override fun setFullscreen(flag: Boolean) {
		executeOnClientThread {
			if (client.window.isFullscreen != flag) {
				client.window.toggleFullScreen()
				client.options.fullscreen().set(client.window.isFullscreen)
				client.options.save()
			}
			sync()
		}
	}

	override fun setMaximized(flag: Boolean) {
		executeOnClientThread {
			if (flag) {
				GLFW.glfwMaximizeWindow(client.window.handle())
			} else {
				GLFW.glfwRestoreWindow(client.window.handle())
			}
			sync()
		}
	}

	override fun setMinimized(flag: Boolean) {
		executeOnClientThread {
			if (flag) {
				GLFW.glfwIconifyWindow(client.window.handle())
			} else {
				GLFW.glfwRestoreWindow(client.window.handle())
			}
			sync()
		}
	}

	override fun setVisible(flag: Boolean) {
		executeOnClientThread {
			if (flag) {
				GLFW.glfwShowWindow(client.window.handle())
			} else {
				GLFW.glfwHideWindow(client.window.handle())
			}
			sync()
		}
	}

	override fun setFocused(flag: Boolean) {
		if (flag) {
			executeOnClientThread {
				GLFW.glfwFocusWindow(client.window.handle())
				sync()
			}
		}
	}

	override fun setTitleBarVisibility(flag: Boolean) {
		executeOnClientThread {
			GLFW.glfwSetWindowAttrib(
				client.window.handle(),
				GLFW.GLFW_DECORATED,
				if (flag) GLFW.GLFW_TRUE else GLFW.GLFW_FALSE
			)
			sync()
		}
	}

	override fun close() {
		closeListeners.update()
		for (index in closeListeners.indices) {
			if (!closeListeners[index].onCloseRequest()) {
				return
			}
		}
		executeOnClientThread {
			GLFW.glfwSetWindowShouldClose(client.window.handle(), true)
		}
	}

	override fun pollEvents() {
		GLFW.glfwPollEvents()
		sync()
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
		updateSizesAndScales(readFramebufferSize(), readParentScreenScale())
		flags = readFlags()
	}

	fun handleFileDrop(paths: List<Path>) {
		if (paths.isEmpty()) {
			return
		}
		val files = ArrayList<LoadableFile>(paths.size)
		for (path in paths) {
			files += LoadableFileImpl(path.toFile())
		}
		dragAndDropListeners.update()
		for (index in dragAndDropListeners.indices) {
			dragAndDropListeners[index].onFileDrop(files)
		}
	}

	fun shutdown() {
		KoolWindowBridge.clearWindow(this)
	}

	private fun updateSizesAndScales(nextFramebufferSize: Vec2i, nextParentScreenScale: Float) {
		val oldRenderScale = renderScale
		val nextSize = scaledRenderSize(nextFramebufferSize)
		val sizeChanged = nextFramebufferSize != currentFramebufferSize || nextSize != currentSize
		currentFramebufferSize = nextFramebufferSize
		currentSize = nextSize
		currentParentScreenScale = nextParentScreenScale
		if (sizeChanged) {
			resizeListeners.update()
			for (index in resizeListeners.indices) {
				resizeListeners[index].onResize(currentSize)
			}
		}
		if (oldRenderScale != renderScale) {
			UiScale.updateUiScaleFromWindowScale(renderScale)
			scaleChangeListeners.update()
			for (index in scaleChangeListeners.indices) {
				scaleChangeListeners[index].onScaleChanged(renderScale)
			}
		}
	}

	private fun readFramebufferSize(): Vec2i {
		val renderTarget = client.gameRenderer.mainRenderTarget()
		return Vec2i(renderTarget.width.coerceAtLeast(1), renderTarget.height.coerceAtLeast(1))
	}

	private fun readParentScreenScale(): Float {
		return client.window.guiScale.toFloat().coerceAtLeast(1.0f)
	}

	private fun scaledRenderSize(framebufferSize: Vec2i): Vec2i {
		return Vec2i(
			(framebufferSize.x * renderResolutionFactor).roundToInt().coerceAtLeast(1),
			(framebufferSize.y * renderResolutionFactor).roundToInt().coerceAtLeast(1)
		)
	}

	private fun readFlags(): WindowFlags {
		val handle = client.window.handle()
		return WindowFlags(
			isFullscreen = client.window.isFullscreen,
			isMaximized = GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE,
			isMinimized = client.window.isMinimized || GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE,
			isVisible = GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_VISIBLE) == GLFW.GLFW_TRUE,
			isFocused = client.window.isFocused,
			isHiddenTitleBar = GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_DECORATED) != GLFW.GLFW_TRUE
		)
	}

	private fun executeOnClientThread(action: () -> Unit) {
		if (client.isSameThread) {
			action()
		} else {
			client.execute(action)
		}
	}

	private companion object {
		const val MIN_RENDER_RESOLUTION_FACTOR: Float = 0.05f
	}
}
