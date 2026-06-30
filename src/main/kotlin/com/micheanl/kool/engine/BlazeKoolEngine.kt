package com.micheanl.kool.engine

import com.micheanl.kool.api.KoolSceneRegistry
import com.micheanl.kool.api.geometry.BlazeKoolGeometry
import com.micheanl.kool.integration.kool.MinecraftKoolContext
import com.micheanl.kool.render.blaze3d.BlazeKoolRenderBridge
import com.mojang.blaze3d.systems.RenderSystem
import de.fabmax.kool.scene.Scene
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft

object BlazeKoolEngine {
	@Volatile
	private var device: BlazeKoolDevice? = null

	private val renderBridge = BlazeKoolRenderBridge()
	private var koolContext: MinecraftKoolContext? = null

	val currentDevice: BlazeKoolDevice?
		get() = device

	val sceneRegistry: KoolSceneRegistry?
		get() = koolContext

	fun start(client: Minecraft) {
		val gpuDevice = RenderSystem.tryGetDevice() ?: return
		device = BlazeKoolDevice.from(gpuDevice.getDeviceInfo())
		val renderTarget = client.gameRenderer.mainRenderTarget()
		renderBridge.resize(renderTarget.width, renderTarget.height)
		if (koolContext == null) {
			koolContext = MinecraftKoolContext(client, renderBridge)
		}
	}

	fun stop(client: Minecraft) {
		koolContext?.shutdown()
		koolContext = null
		renderBridge.clear()
		device = null
	}

	fun collectSubmits(context: LevelRenderContext) {
		if (device == null) {
			val gpuDevice = RenderSystem.tryGetDevice() ?: return
			device = BlazeKoolDevice.from(gpuDevice.getDeviceInfo())
		}

		koolContext?.renderIntoMinecraft()
		renderBridge.collectSubmits(context)
	}

	fun addGeometry(geometry: BlazeKoolGeometry) {
		renderBridge.add(geometry)
	}

	fun removeGeometry(geometry: BlazeKoolGeometry) {
		renderBridge.remove(geometry)
	}

	fun clearGeometry() {
		renderBridge.clear()
	}

	fun addScene(scene: Scene) {
		koolContext?.registerScene(scene)
	}

	fun removeScene(scene: Scene) {
		koolContext?.unregisterScene(scene)
	}
}
