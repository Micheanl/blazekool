package com.micheanl.kool.api.minecraft.client

import com.micheanl.kool.minecraft.client.MinecraftKoolClientBlockEntityContext
import com.micheanl.kool.minecraft.client.MinecraftKoolClientChunkContext
import com.micheanl.kool.minecraft.client.MinecraftKoolClientEntityContext
import com.micheanl.kool.minecraft.client.MinecraftKoolClientLevelContext
import com.micheanl.kool.minecraft.client.MinecraftKoolClientPlayerContext
import com.micheanl.kool.minecraft.client.MinecraftKoolScreenContext
import de.fabmax.kool.pipeline.ComputePass
import de.fabmax.kool.scene.Node
import de.fabmax.kool.scene.Scene
import net.minecraft.client.gui.screens.Screen
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

object MinecraftKoolClientSceneAttachments {
	private val revisionCounter = AtomicLong()
	private val globalLevelFactories = CopyOnWriteArrayList<MinecraftKoolClientLevelSceneFactory>()
	private val dimensionLevelFactories = ConcurrentHashMap<ResourceKey<Level>, CopyOnWriteArrayList<MinecraftKoolClientLevelSceneFactory>>()
	private val globalChunkFactories = CopyOnWriteArrayList<MinecraftKoolClientChunkSceneFactory>()
	private val dimensionChunkFactories = ConcurrentHashMap<ResourceKey<Level>, CopyOnWriteArrayList<MinecraftKoolClientChunkSceneFactory>>()
	private val globalEntityFactories = CopyOnWriteArrayList<MinecraftKoolClientEntitySceneFactory>()
	private val entityTypeFactories = ConcurrentHashMap<EntityType<*>, CopyOnWriteArrayList<MinecraftKoolClientEntitySceneFactory>>()
	private val globalBlockEntityFactories = CopyOnWriteArrayList<MinecraftKoolClientBlockEntitySceneFactory>()
	private val blockEntityTypeFactories = ConcurrentHashMap<BlockEntityType<*>, CopyOnWriteArrayList<MinecraftKoolClientBlockEntitySceneFactory>>()
	private val blockFactories = ConcurrentHashMap<Block, CopyOnWriteArrayList<MinecraftKoolClientBlockEntitySceneFactory>>()
	private val globalPlayerFactories = CopyOnWriteArrayList<MinecraftKoolClientPlayerSceneFactory>()
	private val playerFactories = ConcurrentHashMap<UUID, CopyOnWriteArrayList<MinecraftKoolClientPlayerSceneFactory>>()
	private val globalScreenFactories = CopyOnWriteArrayList<MinecraftKoolClientScreenSceneFactory>()
	private val screenClassFactories = ConcurrentHashMap<Class<out Screen>, CopyOnWriteArrayList<MinecraftKoolClientScreenSceneFactory>>()

	internal val revision: Long
		get() = revisionCounter.get()

	fun registerLevel(factory: MinecraftKoolClientLevelSceneFactory): MinecraftKoolClientSceneAttachmentHandle {
		return registerList(globalLevelFactories, factory)
	}

	fun registerLevel(
		dimension: ResourceKey<Level>,
		factory: MinecraftKoolClientLevelSceneFactory
	): MinecraftKoolClientSceneAttachmentHandle {
		return registerMap(dimensionLevelFactories, dimension, factory)
	}

	fun registerChunk(factory: MinecraftKoolClientChunkSceneFactory): MinecraftKoolClientSceneAttachmentHandle {
		return registerList(globalChunkFactories, factory)
	}

	fun registerChunk(
		dimension: ResourceKey<Level>,
		factory: MinecraftKoolClientChunkSceneFactory
	): MinecraftKoolClientSceneAttachmentHandle {
		return registerMap(dimensionChunkFactories, dimension, factory)
	}

	fun registerEntity(factory: MinecraftKoolClientEntitySceneFactory): MinecraftKoolClientSceneAttachmentHandle {
		return registerList(globalEntityFactories, factory)
	}

	fun registerEntity(
		entityType: EntityType<*>,
		factory: MinecraftKoolClientEntitySceneFactory
	): MinecraftKoolClientSceneAttachmentHandle {
		return registerMap(entityTypeFactories, entityType, factory)
	}

	fun registerBlockEntity(factory: MinecraftKoolClientBlockEntitySceneFactory): MinecraftKoolClientSceneAttachmentHandle {
		return registerList(globalBlockEntityFactories, factory)
	}

	fun registerBlockEntity(
		blockEntityType: BlockEntityType<*>,
		factory: MinecraftKoolClientBlockEntitySceneFactory
	): MinecraftKoolClientSceneAttachmentHandle {
		return registerMap(blockEntityTypeFactories, blockEntityType, factory)
	}

	fun registerBlock(block: Block, factory: MinecraftKoolClientBlockEntitySceneFactory): MinecraftKoolClientSceneAttachmentHandle {
		return registerMap(blockFactories, block, factory)
	}

	fun registerPlayer(factory: MinecraftKoolClientPlayerSceneFactory): MinecraftKoolClientSceneAttachmentHandle {
		return registerList(globalPlayerFactories, factory)
	}

	fun registerPlayer(playerId: UUID, factory: MinecraftKoolClientPlayerSceneFactory): MinecraftKoolClientSceneAttachmentHandle {
		return registerMap(playerFactories, playerId, factory)
	}

	fun registerScreen(factory: MinecraftKoolClientScreenSceneFactory): MinecraftKoolClientSceneAttachmentHandle {
		return registerList(globalScreenFactories, factory)
	}

	fun registerScreen(
		screenClass: Class<out Screen>,
		factory: MinecraftKoolClientScreenSceneFactory
	): MinecraftKoolClientSceneAttachmentHandle {
		return registerMap(screenClassFactories, screenClass, factory)
	}

	internal fun forEachLevelFactory(level: Level, handler: (MinecraftKoolClientLevelSceneFactory) -> Unit) {
		forEach(globalLevelFactories, handler)
		forEach(dimensionLevelFactories[level.dimension()], handler)
	}

	internal fun forEachChunkFactory(level: Level, handler: (MinecraftKoolClientChunkSceneFactory) -> Unit) {
		forEach(globalChunkFactories, handler)
		forEach(dimensionChunkFactories[level.dimension()], handler)
	}

	internal fun forEachEntityFactory(entityType: EntityType<*>, handler: (MinecraftKoolClientEntitySceneFactory) -> Unit) {
		forEach(globalEntityFactories, handler)
		forEach(entityTypeFactories[entityType], handler)
	}

	internal fun forEachBlockEntityFactory(
		blockEntityType: BlockEntityType<*>,
		block: Block,
		handler: (MinecraftKoolClientBlockEntitySceneFactory) -> Unit
	) {
		forEach(globalBlockEntityFactories, handler)
		forEach(blockEntityTypeFactories[blockEntityType], handler)
		forEach(blockFactories[block], handler)
	}

	internal fun forEachPlayerFactory(player: Player, handler: (MinecraftKoolClientPlayerSceneFactory) -> Unit) {
		forEach(globalPlayerFactories, handler)
		forEach(playerFactories[player.uuid], handler)
	}

	internal fun forEachScreenFactory(screen: Screen, handler: (MinecraftKoolClientScreenSceneFactory) -> Unit) {
		forEach(globalScreenFactories, handler)
		screenClassFactories.forEach { (screenClass, factories) ->
			if (screenClass.isInstance(screen)) {
				forEach(factories, handler)
			}
		}
	}

	private fun <T> registerList(list: CopyOnWriteArrayList<T>, factory: T): MinecraftKoolClientSceneAttachmentHandle {
		list.addIfAbsent(factory)
		revisionCounter.incrementAndGet()
		return MinecraftKoolClientSceneAttachmentHandle {
			if (list.remove(factory)) {
				revisionCounter.incrementAndGet()
			}
		}
	}

	private fun <K, T> registerMap(
		map: ConcurrentHashMap<K, CopyOnWriteArrayList<T>>,
		key: K,
		factory: T
	): MinecraftKoolClientSceneAttachmentHandle {
		val list = map.computeIfAbsent(key) { CopyOnWriteArrayList() }
		list.addIfAbsent(factory)
		revisionCounter.incrementAndGet()
		return MinecraftKoolClientSceneAttachmentHandle {
			if (list.remove(factory)) {
				if (list.isEmpty()) {
					map.remove(key, list)
				}
				revisionCounter.incrementAndGet()
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

class MinecraftKoolClientSceneAttachment<C>(
	val scene: Scene,
	val releaseSceneOnDetach: Boolean = true,
	private val attachCallbacks: List<(C) -> Unit> = emptyList(),
	private val tickCallbacks: List<(C) -> Unit> = emptyList(),
	private val detachCallbacks: List<(C) -> Unit> = emptyList()
) {
	internal fun attach(context: C) {
		var index = 0
		while (index < attachCallbacks.size) {
			attachCallbacks[index](context)
			index++
		}
	}

	internal fun tick(context: C) {
		var index = 0
		while (index < tickCallbacks.size) {
			tickCallbacks[index](context)
			index++
		}
	}

	internal fun detach(context: C) {
		var index = 0
		while (index < detachCallbacks.size) {
			detachCallbacks[index](context)
			index++
		}
	}
}

class MinecraftKoolClientSceneAttachmentBuilder<C>(
	val context: C,
	name: String
) {
	val scene: Scene = Scene(name)
	private val attachCallbacks = ArrayList<(C) -> Unit>()
	private val tickCallbacks = ArrayList<(C) -> Unit>()
	private val detachCallbacks = ArrayList<(C) -> Unit>()
	private var releaseSceneOnDetach = true

	fun node(node: Node): Node {
		scene.addNode(node)
		return node
	}

	fun computePass(pass: ComputePass): ComputePass {
		scene.addComputePass(pass)
		return pass
	}

	fun keepSceneAfterDetach() {
		releaseSceneOnDetach = false
	}

	fun onAttach(callback: (C) -> Unit) {
		attachCallbacks += callback
	}

	fun onTick(callback: (C) -> Unit) {
		tickCallbacks += callback
	}

	fun onDetach(callback: (C) -> Unit) {
		detachCallbacks += callback
	}

	fun build(): MinecraftKoolClientSceneAttachment<C> {
		return MinecraftKoolClientSceneAttachment(
			scene = scene,
			releaseSceneOnDetach = releaseSceneOnDetach,
			attachCallbacks = attachCallbacks.toList(),
			tickCallbacks = tickCallbacks.toList(),
			detachCallbacks = detachCallbacks.toList()
		)
	}
}

fun <C> minecraftKoolClientSceneAttachment(
	context: C,
	name: String,
	block: MinecraftKoolClientSceneAttachmentBuilder<C>.() -> Unit
): MinecraftKoolClientSceneAttachment<C> {
	val builder = MinecraftKoolClientSceneAttachmentBuilder(context, name)
	builder.block()
	return builder.build()
}

fun interface MinecraftKoolClientSceneAttachmentHandle : AutoCloseable {
	override fun close()
}

fun interface MinecraftKoolClientLevelSceneFactory {
	fun create(context: MinecraftKoolClientLevelContext): MinecraftKoolClientSceneAttachment<MinecraftKoolClientLevelContext>?
}

fun interface MinecraftKoolClientChunkSceneFactory {
	fun create(context: MinecraftKoolClientChunkContext): MinecraftKoolClientSceneAttachment<MinecraftKoolClientChunkContext>?
}

fun interface MinecraftKoolClientEntitySceneFactory {
	fun create(context: MinecraftKoolClientEntityContext): MinecraftKoolClientSceneAttachment<MinecraftKoolClientEntityContext>?
}

fun interface MinecraftKoolClientBlockEntitySceneFactory {
	fun create(context: MinecraftKoolClientBlockEntityContext): MinecraftKoolClientSceneAttachment<MinecraftKoolClientBlockEntityContext>?
}

fun interface MinecraftKoolClientPlayerSceneFactory {
	fun create(context: MinecraftKoolClientPlayerContext): MinecraftKoolClientSceneAttachment<MinecraftKoolClientPlayerContext>?
}

fun interface MinecraftKoolClientScreenSceneFactory {
	fun create(context: MinecraftKoolScreenContext): MinecraftKoolClientSceneAttachment<MinecraftKoolScreenContext>?
}
