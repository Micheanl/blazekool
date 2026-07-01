package com.micheanl.kool.api.minecraft.client

import com.micheanl.kool.minecraft.client.MinecraftKoolBlockOutlineContext
import com.micheanl.kool.minecraft.client.MinecraftKoolClientBlockEntityContext
import com.micheanl.kool.minecraft.client.MinecraftKoolClientChunkContext
import com.micheanl.kool.minecraft.client.MinecraftKoolClientContext
import com.micheanl.kool.minecraft.client.MinecraftKoolClientEntityContext
import com.micheanl.kool.minecraft.client.MinecraftKoolClientLevelContext
import com.micheanl.kool.minecraft.client.MinecraftKoolClientPlayerContext
import com.micheanl.kool.minecraft.client.MinecraftKoolRenderContext
import com.micheanl.kool.minecraft.client.MinecraftKoolScreenContext
import com.micheanl.kool.minecraft.client.MinecraftKoolTerrainRenderContext
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

object MinecraftKoolClientBindings {
	private val lifecycleBindings = CopyOnWriteArrayList<MinecraftKoolClientLifecycleBinding>()
	private val globalLevelBindings = CopyOnWriteArrayList<MinecraftKoolClientLevelBinding>()
	private val dimensionLevelBindings = ConcurrentHashMap<ResourceKey<Level>, CopyOnWriteArrayList<MinecraftKoolClientLevelBinding>>()
	private val globalChunkBindings = CopyOnWriteArrayList<MinecraftKoolClientChunkBinding>()
	private val dimensionChunkBindings = ConcurrentHashMap<ResourceKey<Level>, CopyOnWriteArrayList<MinecraftKoolClientChunkBinding>>()
	private val globalEntityBindings = CopyOnWriteArrayList<MinecraftKoolClientEntityBinding>()
	private val entityTypeBindings = ConcurrentHashMap<EntityType<*>, CopyOnWriteArrayList<MinecraftKoolClientEntityBinding>>()
	private val globalBlockEntityBindings = CopyOnWriteArrayList<MinecraftKoolClientBlockEntityBinding>()
	private val blockEntityTypeBindings = ConcurrentHashMap<BlockEntityType<*>, CopyOnWriteArrayList<MinecraftKoolClientBlockEntityBinding>>()
	private val blockBindings = ConcurrentHashMap<Block, CopyOnWriteArrayList<MinecraftKoolClientBlockBinding>>()
	private val globalPlayerBindings = CopyOnWriteArrayList<MinecraftKoolClientPlayerBinding>()
	private val playerBindings = ConcurrentHashMap<UUID, CopyOnWriteArrayList<MinecraftKoolClientPlayerBinding>>()
	private val renderBindings = CopyOnWriteArrayList<MinecraftKoolClientRenderBinding>()
	private val screenBindings = CopyOnWriteArrayList<MinecraftKoolClientScreenBinding>()

	fun registerLifecycle(binding: MinecraftKoolClientLifecycleBinding): MinecraftKoolClientBindingHandle {
		return registerList(lifecycleBindings, binding)
	}

	fun registerLevel(binding: MinecraftKoolClientLevelBinding): MinecraftKoolClientBindingHandle {
		return registerList(globalLevelBindings, binding)
	}

	fun registerLevel(dimension: ResourceKey<Level>, binding: MinecraftKoolClientLevelBinding): MinecraftKoolClientBindingHandle {
		return registerMap(dimensionLevelBindings, dimension, binding)
	}

	fun registerChunk(binding: MinecraftKoolClientChunkBinding): MinecraftKoolClientBindingHandle {
		return registerList(globalChunkBindings, binding)
	}

	fun registerChunk(dimension: ResourceKey<Level>, binding: MinecraftKoolClientChunkBinding): MinecraftKoolClientBindingHandle {
		return registerMap(dimensionChunkBindings, dimension, binding)
	}

	fun registerEntity(binding: MinecraftKoolClientEntityBinding): MinecraftKoolClientBindingHandle {
		return registerList(globalEntityBindings, binding)
	}

	fun registerEntity(entityType: EntityType<*>, binding: MinecraftKoolClientEntityBinding): MinecraftKoolClientBindingHandle {
		return registerMap(entityTypeBindings, entityType, binding)
	}

	fun registerBlockEntity(binding: MinecraftKoolClientBlockEntityBinding): MinecraftKoolClientBindingHandle {
		return registerList(globalBlockEntityBindings, binding)
	}

	fun registerBlockEntity(
		blockEntityType: BlockEntityType<*>,
		binding: MinecraftKoolClientBlockEntityBinding
	): MinecraftKoolClientBindingHandle {
		return registerMap(blockEntityTypeBindings, blockEntityType, binding)
	}

	fun registerBlock(block: Block, binding: MinecraftKoolClientBlockBinding): MinecraftKoolClientBindingHandle {
		return registerMap(blockBindings, block, binding)
	}

	fun registerPlayer(binding: MinecraftKoolClientPlayerBinding): MinecraftKoolClientBindingHandle {
		return registerList(globalPlayerBindings, binding)
	}

	fun registerPlayer(playerId: UUID, binding: MinecraftKoolClientPlayerBinding): MinecraftKoolClientBindingHandle {
		return registerMap(playerBindings, playerId, binding)
	}

	fun registerRender(binding: MinecraftKoolClientRenderBinding): MinecraftKoolClientBindingHandle {
		return registerList(renderBindings, binding)
	}

	fun registerScreen(binding: MinecraftKoolClientScreenBinding): MinecraftKoolClientBindingHandle {
		return registerList(screenBindings, binding)
	}

	internal fun forEachLifecycleBinding(handler: (MinecraftKoolClientLifecycleBinding) -> Unit) {
		forEach(lifecycleBindings, handler)
	}

	internal fun forEachLevelBinding(level: Level, handler: (MinecraftKoolClientLevelBinding) -> Unit) {
		forEach(globalLevelBindings, handler)
		forEach(dimensionLevelBindings[level.dimension()], handler)
	}

	internal fun forEachChunkBinding(level: Level, handler: (MinecraftKoolClientChunkBinding) -> Unit) {
		forEach(globalChunkBindings, handler)
		forEach(dimensionChunkBindings[level.dimension()], handler)
	}

	internal fun forEachEntityBinding(entityType: EntityType<*>, handler: (MinecraftKoolClientEntityBinding) -> Unit) {
		forEach(globalEntityBindings, handler)
		forEach(entityTypeBindings[entityType], handler)
	}

	internal fun forEachBlockEntityBinding(
		blockEntityType: BlockEntityType<*>,
		handler: (MinecraftKoolClientBlockEntityBinding) -> Unit
	) {
		forEach(globalBlockEntityBindings, handler)
		forEach(blockEntityTypeBindings[blockEntityType], handler)
	}

	internal fun forEachBlockBinding(block: Block, handler: (MinecraftKoolClientBlockBinding) -> Unit) {
		forEach(blockBindings[block], handler)
	}

	internal fun forEachPlayerBinding(player: Player, handler: (MinecraftKoolClientPlayerBinding) -> Unit) {
		forEach(globalPlayerBindings, handler)
		forEach(playerBindings[player.uuid], handler)
	}

	internal fun forEachRenderBinding(handler: (MinecraftKoolClientRenderBinding) -> Unit) {
		forEach(renderBindings, handler)
	}

	internal fun forEachScreenBinding(handler: (MinecraftKoolClientScreenBinding) -> Unit) {
		forEach(screenBindings, handler)
	}

	private fun <T> registerList(list: CopyOnWriteArrayList<T>, binding: T): MinecraftKoolClientBindingHandle {
		list.addIfAbsent(binding)
		return MinecraftKoolClientBindingHandle {
			list.remove(binding)
		}
	}

	private fun <K, T> registerMap(
		map: ConcurrentHashMap<K, CopyOnWriteArrayList<T>>,
		key: K,
		binding: T
	): MinecraftKoolClientBindingHandle {
		val list = map.computeIfAbsent(key) { CopyOnWriteArrayList() }
		list.addIfAbsent(binding)
		return MinecraftKoolClientBindingHandle {
			list.remove(binding)
			if (list.isEmpty()) {
				map.remove(key, list)
			}
		}
	}

	private fun <T> forEach(list: CopyOnWriteArrayList<T>?, handler: (T) -> Unit) {
		if (list == null) {
			return
		}
		var index = 0
		while (index < list.size) {
			handler(list[index])
			index++
		}
	}
}

fun interface MinecraftKoolClientBindingHandle : AutoCloseable {
	override fun close()
}

interface MinecraftKoolClientLifecycleBinding {
	fun onClientStarted(context: MinecraftKoolClientContext) {
	}

	fun onClientStopping(context: MinecraftKoolClientContext) {
	}

	fun onClientTickStart(context: MinecraftKoolClientContext) {
	}

	fun onClientTickEnd(context: MinecraftKoolClientContext) {
	}
}

interface MinecraftKoolClientLevelBinding {
	fun onChanged(context: MinecraftKoolClientLevelContext) {
	}

	fun onTickStart(context: MinecraftKoolClientLevelContext) {
	}

	fun onTickEnd(context: MinecraftKoolClientLevelContext) {
	}
}

interface MinecraftKoolClientChunkBinding {
	fun onLoad(context: MinecraftKoolClientChunkContext) {
	}

	fun onUnload(context: MinecraftKoolClientChunkContext) {
	}
}

interface MinecraftKoolClientEntityBinding {
	fun onLoad(context: MinecraftKoolClientEntityContext) {
	}

	fun onUnload(context: MinecraftKoolClientEntityContext) {
	}

	fun onTick(context: MinecraftKoolClientEntityContext) {
	}
}

interface MinecraftKoolClientBlockEntityBinding {
	fun onLoad(context: MinecraftKoolClientBlockEntityContext) {
	}

	fun onUnload(context: MinecraftKoolClientBlockEntityContext) {
	}
}

interface MinecraftKoolClientBlockBinding {
	fun onBlockEntityLoad(context: MinecraftKoolClientBlockEntityContext) {
	}

	fun onBlockEntityUnload(context: MinecraftKoolClientBlockEntityContext) {
	}
}

interface MinecraftKoolClientPlayerBinding {
	fun onTick(context: MinecraftKoolClientPlayerContext) {
	}
}

interface MinecraftKoolClientRenderBinding {
	fun onStartMain(context: MinecraftKoolTerrainRenderContext) {
	}

	fun onAfterOpaqueTerrain(context: MinecraftKoolTerrainRenderContext) {
	}

	fun onCollectSubmits(context: MinecraftKoolRenderContext) {
	}

	fun onAfterSolidFeatures(context: MinecraftKoolRenderContext) {
	}

	fun onAfterTranslucentFeatures(context: MinecraftKoolRenderContext) {
	}

	fun onBeforeBlockOutline(context: MinecraftKoolBlockOutlineContext): Boolean {
		return true
	}

	fun onBeforeGizmos(context: MinecraftKoolRenderContext) {
	}

	fun onBeforeTranslucentTerrain(context: MinecraftKoolRenderContext) {
	}

	fun onAfterTranslucentTerrain(context: MinecraftKoolRenderContext) {
	}

	fun onEndMain(context: MinecraftKoolRenderContext) {
	}
}

interface MinecraftKoolClientScreenBinding {
	fun onBeforeInit(context: MinecraftKoolScreenContext) {
	}

	fun onAfterInit(context: MinecraftKoolScreenContext) {
	}

	fun onRemoved(context: MinecraftKoolScreenContext) {
	}

	fun onBeforeExtract(context: MinecraftKoolScreenContext) {
	}

	fun onAfterBackground(context: MinecraftKoolScreenContext) {
	}

	fun onAfterExtract(context: MinecraftKoolScreenContext) {
	}

	fun onBeforeTick(context: MinecraftKoolScreenContext) {
	}

	fun onAfterTick(context: MinecraftKoolScreenContext) {
	}
}
