package com.micheanl.kool.integration.kool

import com.micheanl.kool.api.KoolSceneRegistry
import com.micheanl.kool.render.blaze3d.BlazeKoolRenderBridge
import com.micheanl.kool.render.blaze3d.Blaze3DKoolBackend
import de.fabmax.kool.FrameData
import de.fabmax.kool.KoolContext
import de.fabmax.kool.KoolSystem
import de.fabmax.kool.scene.Scene
import kotlinx.coroutines.runBlocking
import net.minecraft.client.Minecraft
import java.awt.Desktop
import java.net.URI

class MinecraftKoolContext(
	private val client: Minecraft,
	private val renderBridge: BlazeKoolRenderBridge,
	@Suppress("UNUSED_PARAMETER")
	private val initialized: Unit = initializeKoolSystem(client)
) : KoolContext(), KoolSceneRegistry {
	override val backend: Blaze3DKoolBackend = Blaze3DKoolBackend(renderBridge)
	override val window: MinecraftKoolWindow = MinecraftKoolWindow(client)

	private val sysInfos = ArrayList<String>(4)

	init {
		KoolSystemBridge.onContextCreated(this)
		MinecraftKoolControllerBridge.initialize()
	}

	override fun openUrl(url: String, sameWindow: Boolean) {
		Desktop.getDesktop().browse(URI(url))
	}

	override fun run() {
	}

	override fun getSysInfos(): List<String> {
		sysInfos.clear()
		sysInfos += "Minecraft ${client.launchedVersion}"
		sysInfos += backend.deviceName
		return sysInfos
	}

	override fun registerScene(scene: Scene) {
		addScene(scene)
	}

	override fun unregisterScene(scene: Scene) {
		removeScene(scene)
	}

	fun renderIntoMinecraft() {
		window.sync()
		val frameData = runBlocking { render() }
		syncForBackend(frameData)
		incrementFrameTime()
		backend.renderFrame(frameData, this)
	}

	fun shutdown() {
		onShutdown.update()
		for (index in onShutdown.indices) {
			onShutdown[index](this)
		}
		window.shutdown()
		MinecraftKoolControllerBridge.shutdown()
		backend.cleanup(this)
	}

	private fun syncForBackend(frameData: FrameData) {
		frameData.syncData()
	}

	companion object {
		private fun initializeKoolSystem(client: Minecraft) {
			if (!KoolSystem.isInitialized) {
				KoolSystem.initialize(MinecraftKoolConfig.create(client))
			}
		}
	}
}
