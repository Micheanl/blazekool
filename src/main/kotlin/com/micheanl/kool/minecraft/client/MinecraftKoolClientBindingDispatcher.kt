package com.micheanl.kool.minecraft.client

import com.micheanl.kool.api.minecraft.client.MinecraftKoolClientBindings
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.chunk.LevelChunk

object MinecraftKoolClientBindingDispatcher {
	fun dispatchClientStarted(context: MinecraftKoolClientContext) {
		MinecraftKoolClientSceneAttachmentManager.onClientStarted(context.client)
		MinecraftKoolClientBindings.forEachLifecycleBinding { binding -> binding.onClientStarted(context) }
	}

	fun dispatchClientStopping(context: MinecraftKoolClientContext) {
		MinecraftKoolClientSceneAttachmentManager.onClientStopping(context.client)
		MinecraftKoolClientBindings.forEachLifecycleBinding { binding -> binding.onClientStopping(context) }
	}

	fun dispatchClientTickStart(context: MinecraftKoolClientContext) {
		MinecraftKoolClientBindings.forEachLifecycleBinding { binding -> binding.onClientTickStart(context) }
	}

	fun dispatchClientTickEnd(context: MinecraftKoolClientContext) {
		MinecraftKoolClientSceneAttachmentManager.onClientTick(context.client)
		MinecraftKoolClientBindings.forEachLifecycleBinding { binding -> binding.onClientTickEnd(context) }
	}

	fun dispatchLevelChanged(client: Minecraft, level: ClientLevel) {
		val context = MinecraftKoolClientLevelContext(client, level)
		MinecraftKoolClientRuntimeState.update(context)
		MinecraftKoolClientSceneAttachmentManager.onLevelChanged(client, level)
		MinecraftKoolClientBindings.forEachLevelBinding(level) { binding -> binding.onChanged(context) }
	}

	fun dispatchLevelTickStart(context: MinecraftKoolClientLevelContext) {
		MinecraftKoolClientSceneAttachmentManager.onLevelTickStart(context)
		MinecraftKoolClientBindings.forEachLevelBinding(context.level) { binding -> binding.onTickStart(context) }
	}

	fun dispatchLevelTickEnd(context: MinecraftKoolClientLevelContext) {
		MinecraftKoolClientSceneAttachmentManager.onLevelTickEnd(context)
		MinecraftKoolClientBindings.forEachLevelBinding(context.level) { binding -> binding.onTickEnd(context) }
	}

	fun dispatchPlayerTick(context: MinecraftKoolClientPlayerContext) {
		MinecraftKoolClientSceneAttachmentManager.onPlayerTick(context)
		MinecraftKoolClientBindings.forEachPlayerBinding(context.player) { binding -> binding.onTick(context) }
	}

	fun dispatchEntityLoad(entity: Entity, level: ClientLevel) {
		val context = MinecraftKoolClientEntityContext(entity, level)
		MinecraftKoolClientSceneAttachmentManager.onEntityLoad(context)
		MinecraftKoolClientBindings.forEachEntityBinding(entity.type) { binding -> binding.onLoad(context) }
	}

	fun dispatchEntityUnload(entity: Entity, level: ClientLevel) {
		val context = MinecraftKoolClientEntityContext(entity, level)
		MinecraftKoolClientSceneAttachmentManager.onEntityUnload(context)
		MinecraftKoolClientBindings.forEachEntityBinding(entity.type) { binding -> binding.onUnload(context) }
	}

	fun dispatchEntityTick(entity: Entity, level: ClientLevel) {
		val context = MinecraftKoolClientEntityContext(entity, level)
		MinecraftKoolClientSceneAttachmentManager.onEntityTick(context)
		MinecraftKoolClientBindings.forEachEntityBinding(entity.type) { binding -> binding.onTick(context) }
	}

	fun dispatchBlockEntityLoad(blockEntity: BlockEntity, level: ClientLevel) {
		val context = MinecraftKoolClientBlockEntityContext(blockEntity, level)
		MinecraftKoolClientSceneAttachmentManager.onBlockEntityLoad(context)
		MinecraftKoolClientBindings.forEachBlockEntityBinding(blockEntity.type) { binding -> binding.onLoad(context) }
		MinecraftKoolClientBindings.forEachBlockBinding(blockEntity.blockState.block) { binding -> binding.onBlockEntityLoad(context) }
	}

	fun dispatchBlockEntityUnload(blockEntity: BlockEntity, level: ClientLevel) {
		val context = MinecraftKoolClientBlockEntityContext(blockEntity, level)
		MinecraftKoolClientSceneAttachmentManager.onBlockEntityUnload(context)
		MinecraftKoolClientBindings.forEachBlockEntityBinding(blockEntity.type) { binding -> binding.onUnload(context) }
		MinecraftKoolClientBindings.forEachBlockBinding(blockEntity.blockState.block) { binding -> binding.onBlockEntityUnload(context) }
	}

	fun dispatchChunkLoad(level: ClientLevel, chunk: LevelChunk) {
		val context = MinecraftKoolClientChunkContext(level, chunk)
		MinecraftKoolClientSceneAttachmentManager.onChunkLoad(context)
		MinecraftKoolClientBindings.forEachChunkBinding(level) { binding -> binding.onLoad(context) }
	}

	fun dispatchChunkUnload(level: ClientLevel, chunk: LevelChunk) {
		val context = MinecraftKoolClientChunkContext(level, chunk)
		MinecraftKoolClientSceneAttachmentManager.onChunkUnload(context)
		MinecraftKoolClientBindings.forEachChunkBinding(level) { binding -> binding.onUnload(context) }
	}

	fun dispatchStartMain(context: MinecraftKoolTerrainRenderContext) {
		MinecraftKoolClientBindings.forEachRenderBinding { binding -> binding.onStartMain(context) }
	}

	fun dispatchAfterOpaqueTerrain(context: MinecraftKoolTerrainRenderContext) {
		MinecraftKoolClientBindings.forEachRenderBinding { binding -> binding.onAfterOpaqueTerrain(context) }
	}

	fun dispatchCollectSubmits(context: MinecraftKoolRenderContext) {
		MinecraftKoolClientBindings.forEachRenderBinding { binding -> binding.onCollectSubmits(context) }
	}

	fun dispatchAfterSolidFeatures(context: MinecraftKoolRenderContext) {
		MinecraftKoolClientBindings.forEachRenderBinding { binding -> binding.onAfterSolidFeatures(context) }
	}

	fun dispatchAfterTranslucentFeatures(context: MinecraftKoolRenderContext) {
		MinecraftKoolClientBindings.forEachRenderBinding { binding -> binding.onAfterTranslucentFeatures(context) }
	}

	fun dispatchBeforeBlockOutline(context: MinecraftKoolBlockOutlineContext): Boolean {
		var shouldRender = true
		MinecraftKoolClientBindings.forEachRenderBinding { binding ->
			if (!binding.onBeforeBlockOutline(context)) {
				shouldRender = false
			}
		}
		return shouldRender
	}

	fun dispatchBeforeGizmos(context: MinecraftKoolRenderContext) {
		MinecraftKoolClientBindings.forEachRenderBinding { binding -> binding.onBeforeGizmos(context) }
	}

	fun dispatchBeforeTranslucentTerrain(context: MinecraftKoolRenderContext) {
		MinecraftKoolClientBindings.forEachRenderBinding { binding -> binding.onBeforeTranslucentTerrain(context) }
	}

	fun dispatchAfterTranslucentTerrain(context: MinecraftKoolRenderContext) {
		MinecraftKoolClientBindings.forEachRenderBinding { binding -> binding.onAfterTranslucentTerrain(context) }
	}

	fun dispatchEndMain(context: MinecraftKoolRenderContext) {
		MinecraftKoolClientBindings.forEachRenderBinding { binding -> binding.onEndMain(context) }
	}

	fun dispatchScreenBeforeInit(context: MinecraftKoolScreenContext) {
		MinecraftKoolClientBindings.forEachScreenBinding { binding -> binding.onBeforeInit(context) }
	}

	fun dispatchScreenAfterInit(context: MinecraftKoolScreenContext) {
		MinecraftKoolClientSceneAttachmentManager.onScreenAfterInit(context)
		MinecraftKoolClientBindings.forEachScreenBinding { binding -> binding.onAfterInit(context) }
	}

	fun dispatchScreenRemoved(context: MinecraftKoolScreenContext) {
		MinecraftKoolClientSceneAttachmentManager.onScreenRemoved(context)
		MinecraftKoolClientBindings.forEachScreenBinding { binding -> binding.onRemoved(context) }
	}

	fun dispatchScreenBeforeExtract(context: MinecraftKoolScreenContext) {
		MinecraftKoolClientBindings.forEachScreenBinding { binding -> binding.onBeforeExtract(context) }
	}

	fun dispatchScreenAfterBackground(context: MinecraftKoolScreenContext) {
		MinecraftKoolClientBindings.forEachScreenBinding { binding -> binding.onAfterBackground(context) }
	}

	fun dispatchScreenAfterExtract(context: MinecraftKoolScreenContext) {
		MinecraftKoolClientBindings.forEachScreenBinding { binding -> binding.onAfterExtract(context) }
	}

	fun dispatchScreenBeforeTick(context: MinecraftKoolScreenContext) {
		MinecraftKoolClientSceneAttachmentManager.onScreenTick(context)
		MinecraftKoolClientBindings.forEachScreenBinding { binding -> binding.onBeforeTick(context) }
	}

	fun dispatchScreenAfterTick(context: MinecraftKoolScreenContext) {
		MinecraftKoolClientSceneAttachmentManager.onScreenTick(context)
		MinecraftKoolClientBindings.forEachScreenBinding { binding -> binding.onAfterTick(context) }
	}
}
