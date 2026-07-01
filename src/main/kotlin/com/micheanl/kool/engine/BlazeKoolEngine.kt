package com.micheanl.kool.engine

import com.micheanl.kool.api.KoolSceneRegistry
import com.micheanl.kool.api.geometry.BlazeKoolGeometry
import com.micheanl.kool.integration.kool.MinecraftKoolContext
import com.micheanl.kool.render.blaze3d.BlazeKoolRenderBridge
import com.mojang.blaze3d.systems.RenderSystem
import de.fabmax.kool.scene.Scene
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import java.util.concurrent.CopyOnWriteArrayList

object BlazeKoolEngine {
	@Volatile
	private var device: BlazeKoolDevice? = null

	private val renderBridge = BlazeKoolRenderBridge()
	private val pendingScenes = CopyOnWriteArrayList<Scene>()
	private var koolContext: MinecraftKoolContext? = null

	val currentDevice: BlazeKoolDevice?
		get() = device

	val sceneRegistry: KoolSceneRegistry?
		get() = koolContext

	val currentContext: MinecraftKoolContext?
		get() = koolContext

	fun start(client: Minecraft) {
		refreshDevice()
		val renderTarget = client.gameRenderer.mainRenderTarget()
		renderBridge.resize(renderTarget.width, renderTarget.height)
		if (koolContext == null) {
			val context = MinecraftKoolContext(client, renderBridge)
			koolContext = context
			attachPendingScenes(context)
		}
	}

	fun stop(client: Minecraft) {
		koolContext?.shutdown()
		koolContext = null
		renderBridge.clear()
		device = null
	}

	fun collectSubmits(context: LevelRenderContext) {
		val client = Minecraft.getInstance()
		start(client)
		refreshDevice()

		koolContext?.renderIntoMinecraft()
		renderBridge.collectSubmits(context)
	}

	fun renderSolidDirect(context: LevelRenderContext) {
		renderBridge.renderSolidDirect(context)
	}

	fun renderTranslucentDirect(context: LevelRenderContext) {
		renderBridge.renderTranslucentDirect(context)
	}

	fun extractGui(graphics: GuiGraphicsExtractor) {
		val client = Minecraft.getInstance()
		start(client)
		refreshDevice()
		koolContext?.renderIntoMinecraft()
		renderBridge.extractGui(graphics)
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
		val context = koolContext
		if (context != null) {
			context.registerScene(scene)
		} else {
			pendingScenes.addIfAbsent(scene)
		}
	}

	fun removeScene(scene: Scene) {
		koolContext?.unregisterScene(scene)
		pendingScenes.remove(scene)
	}

	private fun refreshDevice() {
		val gpuDevice = RenderSystem.tryGetDevice() ?: return
		device = BlazeKoolDevice.from(gpuDevice.getDeviceInfo())
	}

	private fun attachPendingScenes(context: MinecraftKoolContext) {
		var index = 0
		while (index < pendingScenes.size) {
			context.registerScene(pendingScenes[index])
			index++
		}
	}
}
