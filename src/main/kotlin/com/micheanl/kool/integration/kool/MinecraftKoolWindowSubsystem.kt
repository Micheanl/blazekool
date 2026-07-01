package com.micheanl.kool.integration.kool

import de.fabmax.kool.input.PlatformInput
import de.fabmax.kool.platform.ClientApi
import de.fabmax.kool.platform.GlWindowCallbacks
import de.fabmax.kool.platform.KoolWindowJvm
import de.fabmax.kool.platform.Lwjgl3Context
import de.fabmax.kool.platform.WindowSubsystem
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.system.MemoryUtil

class MinecraftKoolWindowSubsystem(
	private val client: Minecraft,
	private val minecraftWindow: MinecraftKoolWindow
) : WindowSubsystem {
	override val isCloseRequested: Boolean
		get() = client.window.shouldClose() || !client.isRunning

	override val input: PlatformInput = MinecraftKoolPlatformInput(client)

	override fun queryRequiredVkExtensions(): List<String> {
		val glfwExtensions = checkNotNull(GLFWVulkan.glfwGetRequiredInstanceExtensions()) {
			"GLFW did not provide Vulkan instance extensions."
		}
		val extensions = ArrayList<String>(glfwExtensions.limit())
		var index = 0
		while (index < glfwExtensions.limit()) {
			extensions += MemoryUtil.memASCII(glfwExtensions.get(index))
			index++
		}
		return extensions
	}

	override fun createWindow(clientApi: ClientApi, glCallbacks: GlWindowCallbacks?, ctx: Lwjgl3Context): KoolWindowJvm {
		if (clientApi == ClientApi.OPEN_GL) {
			glCallbacks?.initGl()
		}
		return minecraftWindow
	}

	override fun onEarlyInit() {
	}

	override fun onBackendCreated(ctx: Lwjgl3Context) {
	}

	override fun runRenderLoop() {
		while (!isCloseRequested) {
			GLFW.glfwPollEvents()
			Thread.sleep(1L)
		}
	}
}
