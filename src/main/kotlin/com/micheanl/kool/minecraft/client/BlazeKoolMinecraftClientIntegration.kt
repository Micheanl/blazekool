package com.micheanl.kool.minecraft.client

import com.micheanl.kool.api.minecraft.client.MinecraftKoolClientEvents
import com.micheanl.kool.engine.BlazeKoolEngine
import com.micheanl.kool.integration.kool.MinecraftKoolControllerBridge
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelTerrainRenderContext
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.chunk.LevelChunk

object BlazeKoolMinecraftClientIntegration {
	@Volatile
	private var registered = false

	fun register() {
		if (registered) {
			return
		}
		registered = true

		ClientLifecycleEvents.CLIENT_STARTED.register(::onClientStarted)
		ClientLifecycleEvents.CLIENT_STOPPING.register(::onClientStopping)

		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register(::onClientLevelChanged)
		ClientTickEvents.START_CLIENT_TICK.register(::onClientTickStart)
		ClientTickEvents.END_CLIENT_TICK.register(::onClientTickEnd)
		ClientTickEvents.START_LEVEL_TICK.register(::onLevelTickStart)
		ClientTickEvents.END_LEVEL_TICK.register(::onLevelTickEnd)

		ClientEntityEvents.ENTITY_LOAD.register(::onEntityLoad)
		ClientEntityEvents.ENTITY_UNLOAD.register(::onEntityUnload)
		ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.register(::onBlockEntityLoad)
		ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register(::onBlockEntityUnload)
		ClientChunkEvents.CHUNK_LOAD.register(::onChunkLoad)
		ClientChunkEvents.CHUNK_UNLOAD.register(::onChunkUnload)

		LevelRenderEvents.START_MAIN.register(::onStartMain)
		LevelRenderEvents.AFTER_OPAQUE_TERRAIN.register(::onAfterOpaqueTerrain)
		LevelRenderEvents.COLLECT_SUBMITS.register(::onCollectSubmits)
		LevelRenderEvents.AFTER_SOLID_FEATURES.register(::onAfterSolidFeatures)
		LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(::onAfterTranslucentFeatures)
		LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register(::onBeforeBlockOutline)
		LevelRenderEvents.BEFORE_GIZMOS.register(::onBeforeGizmos)
		LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(::onBeforeTranslucentTerrain)
		LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(::onAfterTranslucentTerrain)
		LevelRenderEvents.END_MAIN.register(::onEndMain)

		ScreenEvents.BEFORE_INIT.register(::onScreenBeforeInit)
		ScreenEvents.AFTER_INIT.register(::onScreenAfterInit)
	}

	private fun onClientStarted(client: Minecraft) {
		val context = MinecraftKoolClientContext(client)
		MinecraftKoolClientRuntimeState.update(context)
		MinecraftKoolControllerBridge.initialize()
		BlazeKoolEngine.start(client)
		MinecraftKoolClientBindingDispatcher.dispatchClientStarted(context)
		MinecraftKoolClientEvents.dispatchClientStarted(context)
	}

	private fun onClientStopping(client: Minecraft) {
		val context = MinecraftKoolClientContext(client)
		MinecraftKoolClientRuntimeState.update(context)
		MinecraftKoolClientBindingDispatcher.dispatchClientStopping(context)
		MinecraftKoolClientEvents.dispatchClientStopping(context)
		BlazeKoolEngine.stop(client)
		MinecraftKoolControllerBridge.shutdown()
		MinecraftKoolClientRuntimeState.clearClient(client)
		MinecraftKoolClientRuntimeState.screen?.let(MinecraftKoolClientRuntimeState::clearScreen)
	}

	private fun onClientLevelChanged(client: Minecraft, level: ClientLevel) {
		MinecraftKoolClientBindingDispatcher.dispatchLevelChanged(client, level)
	}

	private fun onClientTickStart(client: Minecraft) {
		val context = MinecraftKoolClientContext(client)
		MinecraftKoolClientRuntimeState.update(context)
		MinecraftKoolClientBindingDispatcher.dispatchClientTickStart(context)
		MinecraftKoolClientEvents.dispatchClientTickStart(context)
		updateClientLevelAndPlayer(client)
	}

	private fun onClientTickEnd(client: Minecraft) {
		val context = MinecraftKoolClientContext(client)
		MinecraftKoolClientRuntimeState.update(context)
		MinecraftKoolClientBindingDispatcher.dispatchClientTickEnd(context)
		MinecraftKoolClientEvents.dispatchClientTickEnd(context)
		updateClientLevelAndPlayer(client)
	}

	private fun onLevelTickStart(level: ClientLevel) {
		val client = Minecraft.getInstance()
		val context = MinecraftKoolClientLevelContext(client, level)
		MinecraftKoolClientRuntimeState.update(context)
		MinecraftKoolClientBindingDispatcher.dispatchLevelTickStart(context)
		MinecraftKoolClientEvents.dispatchClientLevelTickStart(context)
	}

	private fun onLevelTickEnd(level: ClientLevel) {
		val client = Minecraft.getInstance()
		val context = MinecraftKoolClientLevelContext(client, level)
		MinecraftKoolClientRuntimeState.update(context)
		MinecraftKoolClientBindingDispatcher.dispatchLevelTickEnd(context)
		MinecraftKoolClientEvents.dispatchClientLevelTickEnd(context)
		dispatchLoadedEntityTicks(level)
		updateClientLevelAndPlayer(client)
	}

	private fun onEntityLoad(entity: Entity, level: ClientLevel) {
		MinecraftKoolClientBindingDispatcher.dispatchEntityLoad(entity, level)
	}

	private fun onEntityUnload(entity: Entity, level: ClientLevel) {
		MinecraftKoolClientBindingDispatcher.dispatchEntityUnload(entity, level)
	}

	private fun onBlockEntityLoad(blockEntity: BlockEntity, level: ClientLevel) {
		MinecraftKoolClientBindingDispatcher.dispatchBlockEntityLoad(blockEntity, level)
	}

	private fun onBlockEntityUnload(blockEntity: BlockEntity, level: ClientLevel) {
		MinecraftKoolClientBindingDispatcher.dispatchBlockEntityUnload(blockEntity, level)
	}

	private fun onChunkLoad(level: ClientLevel, chunk: LevelChunk) {
		MinecraftKoolClientBindingDispatcher.dispatchChunkLoad(level, chunk)
	}

	private fun onChunkUnload(level: ClientLevel, chunk: LevelChunk) {
		MinecraftKoolClientBindingDispatcher.dispatchChunkUnload(level, chunk)
	}

	private fun onStartMain(context: LevelTerrainRenderContext) {
		val renderContext = MinecraftKoolTerrainRenderContext(context)
		MinecraftKoolClientRuntimeState.update(renderContext)
		MinecraftKoolClientBindingDispatcher.dispatchStartMain(renderContext)
		MinecraftKoolClientEvents.dispatchStartMain(renderContext)
	}

	private fun onAfterOpaqueTerrain(context: LevelTerrainRenderContext) {
		val renderContext = MinecraftKoolTerrainRenderContext(context)
		MinecraftKoolClientRuntimeState.update(renderContext)
		MinecraftKoolClientBindingDispatcher.dispatchAfterOpaqueTerrain(renderContext)
		MinecraftKoolClientEvents.dispatchAfterOpaqueTerrain(renderContext)
	}

	private fun onCollectSubmits(context: LevelRenderContext) {
		val renderContext = MinecraftKoolRenderContext(context)
		MinecraftKoolClientRuntimeState.update(renderContext)
		BlazeKoolEngine.collectSubmits(context)
		MinecraftKoolClientBindingDispatcher.dispatchCollectSubmits(renderContext)
		MinecraftKoolClientEvents.dispatchCollectSubmits(renderContext)
	}

	private fun onAfterSolidFeatures(context: LevelRenderContext) {
		val renderContext = MinecraftKoolRenderContext(context)
		MinecraftKoolClientRuntimeState.update(renderContext)
		BlazeKoolEngine.renderSolidDirect(context)
		MinecraftKoolClientBindingDispatcher.dispatchAfterSolidFeatures(renderContext)
		MinecraftKoolClientEvents.dispatchAfterSolidFeatures(renderContext)
	}

	private fun onAfterTranslucentFeatures(context: LevelRenderContext) {
		val renderContext = MinecraftKoolRenderContext(context)
		MinecraftKoolClientRuntimeState.update(renderContext)
		MinecraftKoolClientBindingDispatcher.dispatchAfterTranslucentFeatures(renderContext)
		MinecraftKoolClientEvents.dispatchAfterTranslucentFeatures(renderContext)
	}

	private fun onBeforeBlockOutline(context: LevelRenderContext, outlineRenderState: BlockOutlineRenderState): Boolean {
		val blockOutlineContext = MinecraftKoolBlockOutlineContext(context, outlineRenderState)
		MinecraftKoolClientRuntimeState.update(blockOutlineContext)
		return MinecraftKoolClientBindingDispatcher.dispatchBeforeBlockOutline(blockOutlineContext) &&
			MinecraftKoolClientEvents.dispatchBeforeBlockOutline(blockOutlineContext)
	}

	private fun onBeforeGizmos(context: LevelRenderContext) {
		val renderContext = MinecraftKoolRenderContext(context)
		MinecraftKoolClientRuntimeState.update(renderContext)
		MinecraftKoolClientBindingDispatcher.dispatchBeforeGizmos(renderContext)
		MinecraftKoolClientEvents.dispatchBeforeGizmos(renderContext)
	}

	private fun onBeforeTranslucentTerrain(context: LevelRenderContext) {
		val renderContext = MinecraftKoolRenderContext(context)
		MinecraftKoolClientRuntimeState.update(renderContext)
		MinecraftKoolClientBindingDispatcher.dispatchBeforeTranslucentTerrain(renderContext)
		MinecraftKoolClientEvents.dispatchBeforeTranslucentTerrain(renderContext)
	}

	private fun onAfterTranslucentTerrain(context: LevelRenderContext) {
		val renderContext = MinecraftKoolRenderContext(context)
		MinecraftKoolClientRuntimeState.update(renderContext)
		BlazeKoolEngine.renderTranslucentDirect(context)
		MinecraftKoolClientBindingDispatcher.dispatchAfterTranslucentTerrain(renderContext)
		MinecraftKoolClientEvents.dispatchAfterTranslucentTerrain(renderContext)
	}

	private fun onEndMain(context: LevelRenderContext) {
		val renderContext = MinecraftKoolRenderContext(context)
		MinecraftKoolClientRuntimeState.update(renderContext)
		MinecraftKoolClientBindingDispatcher.dispatchEndMain(renderContext)
		MinecraftKoolClientEvents.dispatchEndMain(renderContext)
	}

	private fun onScreenBeforeInit(client: Minecraft, screen: Screen, scaledWidth: Int, scaledHeight: Int) {
		val context = screenContext(client, screen, scaledWidth, scaledHeight)
		MinecraftKoolClientRuntimeState.update(context)
		MinecraftKoolClientRuntimeState.screenGraphics = null
		MinecraftKoolClientBindingDispatcher.dispatchScreenBeforeInit(context)
		MinecraftKoolClientEvents.dispatchScreenBeforeInit(context)
	}

	private fun onScreenAfterInit(client: Minecraft, screen: Screen, scaledWidth: Int, scaledHeight: Int) {
		val context = screenContext(client, screen, scaledWidth, scaledHeight)
		MinecraftKoolClientRuntimeState.update(context)
		MinecraftKoolClientBindingDispatcher.dispatchScreenAfterInit(context)
		MinecraftKoolClientEvents.dispatchScreenAfterInit(context)
		ScreenEvents.remove(screen).register { removedScreen ->
			val removedContext = screenContext(client, removedScreen, scaledWidth, scaledHeight)
			MinecraftKoolClientRuntimeState.update(removedContext)
			MinecraftKoolClientBindingDispatcher.dispatchScreenRemoved(removedContext)
			MinecraftKoolClientEvents.dispatchScreenRemoved(removedContext)
			MinecraftKoolClientRuntimeState.clearScreen(removedScreen)
		}
		ScreenEvents.beforeExtract(screen).register { currentScreen, graphics, mouseX, mouseY, tickProgress ->
			val extractContext = screenContext(client, currentScreen, scaledWidth, scaledHeight, graphics, mouseX, mouseY, tickProgress)
			MinecraftKoolClientRuntimeState.update(extractContext)
			MinecraftKoolClientBindingDispatcher.dispatchScreenBeforeExtract(extractContext)
			MinecraftKoolClientEvents.dispatchScreenBeforeExtract(extractContext)
		}
		ScreenEvents.afterBackground(screen).register { currentScreen, graphics, mouseX, mouseY, tickProgress ->
			val backgroundContext = screenContext(client, currentScreen, scaledWidth, scaledHeight, graphics, mouseX, mouseY, tickProgress)
			MinecraftKoolClientRuntimeState.update(backgroundContext)
			MinecraftKoolClientBindingDispatcher.dispatchScreenAfterBackground(backgroundContext)
			MinecraftKoolClientEvents.dispatchScreenAfterBackground(backgroundContext)
		}
		ScreenEvents.afterExtract(screen).register { currentScreen, graphics, mouseX, mouseY, tickProgress ->
			val extractContext = screenContext(client, currentScreen, scaledWidth, scaledHeight, graphics, mouseX, mouseY, tickProgress)
			MinecraftKoolClientRuntimeState.update(extractContext)
			MinecraftKoolClientBindingDispatcher.dispatchScreenAfterExtract(extractContext)
			MinecraftKoolClientEvents.dispatchScreenAfterExtract(extractContext)
		}
		ScreenEvents.beforeTick(screen).register { currentScreen ->
			val tickContext = screenContext(client, currentScreen, scaledWidth, scaledHeight)
			MinecraftKoolClientRuntimeState.update(tickContext)
			MinecraftKoolClientBindingDispatcher.dispatchScreenBeforeTick(tickContext)
			MinecraftKoolClientEvents.dispatchScreenBeforeTick(tickContext)
		}
		ScreenEvents.afterTick(screen).register { currentScreen ->
			val tickContext = screenContext(client, currentScreen, scaledWidth, scaledHeight)
			MinecraftKoolClientRuntimeState.update(tickContext)
			MinecraftKoolClientBindingDispatcher.dispatchScreenAfterTick(tickContext)
			MinecraftKoolClientEvents.dispatchScreenAfterTick(tickContext)
		}
	}

	private fun updateClientLevelAndPlayer(client: Minecraft) {
		val level = client.level ?: return
		val player = client.player ?: return
		val context = MinecraftKoolClientPlayerContext(client, player, level)
		MinecraftKoolClientRuntimeState.update(context)
		MinecraftKoolClientBindingDispatcher.dispatchPlayerTick(context)
		MinecraftKoolClientEvents.dispatchClientPlayerTick(context)
	}

	private fun dispatchLoadedEntityTicks(level: ClientLevel) {
		val entities = level.entitiesForRendering().iterator()
		while (entities.hasNext()) {
			val entity = entities.next()
			if (!entity.isRemoved) {
				MinecraftKoolClientBindingDispatcher.dispatchEntityTick(entity, level)
			}
		}
	}

	private fun screenContext(
		client: Minecraft,
		screen: Screen,
		scaledWidth: Int,
		scaledHeight: Int,
		graphics: GuiGraphicsExtractor? = null,
		mouseX: Int = 0,
		mouseY: Int = 0,
		tickProgress: Float = 0.0f
	): MinecraftKoolScreenContext {
		return MinecraftKoolScreenContext(
			client = client,
			screen = screen,
			graphics = graphics,
			scaledWidth = scaledWidth,
			scaledHeight = scaledHeight,
			mouseX = mouseX,
			mouseY = mouseY,
			tickProgress = tickProgress
		)
	}
}
