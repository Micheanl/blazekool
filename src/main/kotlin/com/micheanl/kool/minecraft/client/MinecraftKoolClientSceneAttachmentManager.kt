package com.micheanl.kool.minecraft.client

import com.micheanl.kool.api.minecraft.client.MinecraftKoolClientSceneAttachment
import com.micheanl.kool.api.minecraft.client.MinecraftKoolClientSceneAttachments
import com.micheanl.kool.engine.BlazeKoolEngine
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.chunk.LevelChunk
import java.util.IdentityHashMap
import java.util.Collections

object MinecraftKoolClientSceneAttachmentManager {
	private val levels = IdentityHashMap<ClientLevel, LevelAttachmentState>()
	private val chunks = IdentityHashMap<LevelChunk, ChunkAttachmentState>()
	private val entities = IdentityHashMap<Entity, EntityAttachmentState>()
	private val blockEntities = IdentityHashMap<BlockEntity, BlockEntityAttachmentState>()
	private val screens = IdentityHashMap<Screen, ScreenAttachmentState>()
	private var appliedRevision = -1L

	fun onClientStarted(client: Minecraft) {
		appliedRevision = MinecraftKoolClientSceneAttachments.revision
		client.level?.let { level ->
			attachLevel(client, level)
		}
		client.gui.screen()?.let { screen ->
			attachScreen(screenContext(client, screen))
		}
	}

	fun onClientStopping(client: Minecraft) {
		val currentScreen = client.gui.screen()
		if (currentScreen != null) {
			detachScreen(currentScreen, screenContext(client, currentScreen))
		}
		detachAllBlockEntities()
		detachAllEntities()
		detachAllChunks()
		detachAllLevels(client)
	}

	fun onClientTick(client: Minecraft) {
		syncFactoryRevision(client)
	}

	fun onLevelChanged(client: Minecraft, level: ClientLevel) {
		val iterator = levels.keys.iterator()
		while (iterator.hasNext()) {
			val loadedLevel = iterator.next()
			if (loadedLevel !== level) {
				val state = levels.remove(loadedLevel)
				if (state != null) {
					state.detach(MinecraftKoolClientLevelContext(client, loadedLevel))
				}
			}
		}
		attachLevel(client, level)
	}

	fun onLevelTickStart(context: MinecraftKoolClientLevelContext) {
		levels[context.level]?.tick(context)
	}

	fun onLevelTickEnd(context: MinecraftKoolClientLevelContext) {
		levels[context.level]?.tick(context)
	}

	fun onChunkLoad(context: MinecraftKoolClientChunkContext) {
		val state = chunks.computeIfAbsent(context.chunk) { ChunkAttachmentState() }
		state.attach(context)
	}

	fun onChunkUnload(context: MinecraftKoolClientChunkContext) {
		chunks.remove(context.chunk)?.detach(context)
	}

	fun onEntityLoad(context: MinecraftKoolClientEntityContext) {
		val state = entities.computeIfAbsent(context.entity) { EntityAttachmentState() }
		state.attach(context)
	}

	fun onEntityUnload(context: MinecraftKoolClientEntityContext) {
		entities.remove(context.entity)?.detach(context)
	}

	fun onEntityTick(context: MinecraftKoolClientEntityContext) {
		entities[context.entity]?.tick(context)
	}

	fun onBlockEntityLoad(context: MinecraftKoolClientBlockEntityContext) {
		val state = blockEntities.computeIfAbsent(context.blockEntity) { BlockEntityAttachmentState() }
		state.attach(context)
	}

	fun onBlockEntityUnload(context: MinecraftKoolClientBlockEntityContext) {
		blockEntities.remove(context.blockEntity)?.detach(context)
	}

	fun onPlayerTick(context: MinecraftKoolClientPlayerContext) {
		ensurePlayerAttachments(context)
		entities[context.player]?.tickPlayer(context)
	}

	fun onScreenAfterInit(context: MinecraftKoolScreenContext) {
		attachScreen(context)
	}

	fun onScreenTick(context: MinecraftKoolScreenContext) {
		screens[context.screen]?.tick(context)
	}

	fun onScreenRemoved(context: MinecraftKoolScreenContext) {
		detachScreen(context.screen, context)
	}

	private fun attachLevel(client: Minecraft, level: ClientLevel) {
		val state = levels.computeIfAbsent(level) { LevelAttachmentState() }
		state.attach(MinecraftKoolClientLevelContext(client, level))
	}

	private fun ensurePlayerAttachments(context: MinecraftKoolClientPlayerContext) {
		val entityState = entities.computeIfAbsent(context.player) { EntityAttachmentState() }
		entityState.attach(MinecraftKoolClientEntityContext(context.player, context.level))
		entityState.attachPlayer(context)
	}

	private fun attachScreen(context: MinecraftKoolScreenContext) {
		val state = screens.computeIfAbsent(context.screen) { ScreenAttachmentState() }
		state.attach(context)
	}

	private fun detachScreen(screen: Screen, context: MinecraftKoolScreenContext) {
		screens.remove(screen)?.detach(context)
	}

	private fun syncFactoryRevision(client: Minecraft) {
		val revision = MinecraftKoolClientSceneAttachments.revision
		if (revision == appliedRevision) {
			return
		}
		appliedRevision = revision
		client.level?.let { level ->
			attachLevel(client, level)
		}
		chunks.values.forEach { state -> state.refreshFactories() }
		entities.values.forEach { state -> state.refreshFactories() }
		blockEntities.values.forEach { state -> state.refreshFactories() }
		screens.values.forEach { state -> state.refreshFactories() }
	}

	private fun detachAllLevels(client: Minecraft) {
		val iterator = levels.entries.iterator()
		while (iterator.hasNext()) {
			val entry = iterator.next()
			entry.value.detach(MinecraftKoolClientLevelContext(client, entry.key))
			iterator.remove()
		}
	}

	private fun detachAllChunks() {
		val iterator = chunks.entries.iterator()
		while (iterator.hasNext()) {
			val entry = iterator.next()
			entry.value.detachCurrent()
			iterator.remove()
		}
	}

	private fun detachAllEntities() {
		val iterator = entities.entries.iterator()
		while (iterator.hasNext()) {
			val entry = iterator.next()
			entry.value.detachCurrent()
			iterator.remove()
		}
	}

	private fun detachAllBlockEntities() {
		val iterator = blockEntities.entries.iterator()
		while (iterator.hasNext()) {
			val entry = iterator.next()
			entry.value.detachCurrent()
			iterator.remove()
		}
	}

	private fun <C, F> attachAttachment(
		attachments: MutableMap<Any, MinecraftKoolClientSceneAttachment<C>>,
		context: C,
		factory: F,
		create: (F, C) -> MinecraftKoolClientSceneAttachment<C>?
	) {
		if (attachments.containsKey(factory as Any)) {
			return
		}
		val attachment = create(factory, context) ?: return
		attachments[factory] = attachment
		BlazeKoolEngine.addScene(attachment.scene)
		attachment.attach(context)
	}

	private fun validFactoryKeys(): MutableSet<Any> {
		return Collections.newSetFromMap(IdentityHashMap())
	}

	private fun <F> addFactoryKey(keys: MutableSet<Any>, factory: F) {
		keys.add(factory as Any)
	}

	private fun <C> detachRemovedFactories(
		attachments: MutableMap<Any, MinecraftKoolClientSceneAttachment<C>>,
		validFactories: Set<Any>,
		context: C
	) {
		val removedKeys = ArrayList<Any>()
		attachments.keys.forEach { key ->
			if (!validFactories.contains(key)) {
				removedKeys += key
			}
		}
		var index = 0
		while (index < removedKeys.size) {
			val removed = attachments.remove(removedKeys[index])
			if (removed != null) {
				detachAttachment(removed, context)
			}
			index++
		}
	}

	private fun <C> detachAttachment(
		attachment: MinecraftKoolClientSceneAttachment<C>,
		context: C
	) {
		attachment.detach(context)
		BlazeKoolEngine.removeScene(attachment.scene)
		if (attachment.releaseSceneOnDetach) {
			attachment.scene.release()
		}
	}

	private fun screenContext(client: Minecraft, screen: Screen): MinecraftKoolScreenContext {
		return MinecraftKoolScreenContext(client = client, screen = screen)
	}

	private class LevelAttachmentState {
		private val attachments = IdentityHashMap<Any, MinecraftKoolClientSceneAttachment<MinecraftKoolClientLevelContext>>()
		private var lastContext: MinecraftKoolClientLevelContext? = null

		fun attach(context: MinecraftKoolClientLevelContext) {
			lastContext = context
			val validFactories = validFactoryKeys()
			MinecraftKoolClientSceneAttachments.forEachLevelFactory(context.level) { factory ->
				addFactoryKey(validFactories, factory)
				attachAttachment(attachments, context, factory) { sceneFactory, sceneContext ->
					sceneFactory.create(sceneContext)
				}
			}
			detachRemovedFactories(attachments, validFactories, context)
		}

		fun tick(context: MinecraftKoolClientLevelContext) {
			lastContext = context
			attachments.values.forEach { attachment -> attachment.tick(context) }
		}

		fun detach(context: MinecraftKoolClientLevelContext) {
			val attachedScenes = attachments.values.toList()
			attachments.clear()
			var index = 0
			while (index < attachedScenes.size) {
				detachAttachment(attachedScenes[index], context)
				index++
			}
			lastContext = null
		}
	}

	private class ChunkAttachmentState {
		private val attachments = IdentityHashMap<Any, MinecraftKoolClientSceneAttachment<MinecraftKoolClientChunkContext>>()
		private var lastContext: MinecraftKoolClientChunkContext? = null

		fun attach(context: MinecraftKoolClientChunkContext) {
			lastContext = context
			val validFactories = validFactoryKeys()
			MinecraftKoolClientSceneAttachments.forEachChunkFactory(context.level) { factory ->
				addFactoryKey(validFactories, factory)
				attachAttachment(attachments, context, factory) { sceneFactory, sceneContext ->
					sceneFactory.create(sceneContext)
				}
			}
			detachRemovedFactories(attachments, validFactories, context)
		}

		fun refreshFactories() {
			lastContext?.let(::attach)
		}

		fun detach(context: MinecraftKoolClientChunkContext) {
			val attachedScenes = attachments.values.toList()
			attachments.clear()
			var index = 0
			while (index < attachedScenes.size) {
				detachAttachment(attachedScenes[index], context)
				index++
			}
			lastContext = null
		}

		fun detachCurrent() {
			lastContext?.let(::detach)
		}
	}

	private class EntityAttachmentState {
		private val entityAttachments = IdentityHashMap<Any, MinecraftKoolClientSceneAttachment<MinecraftKoolClientEntityContext>>()
		private val playerAttachments = IdentityHashMap<Any, MinecraftKoolClientSceneAttachment<MinecraftKoolClientPlayerContext>>()
		private var lastEntityContext: MinecraftKoolClientEntityContext? = null
		private var lastPlayerContext: MinecraftKoolClientPlayerContext? = null

		fun attach(context: MinecraftKoolClientEntityContext) {
			lastEntityContext = context
			val validFactories = validFactoryKeys()
			MinecraftKoolClientSceneAttachments.forEachEntityFactory(context.entity.type) { factory ->
				addFactoryKey(validFactories, factory)
				attachAttachment(entityAttachments, context, factory) { sceneFactory, sceneContext ->
					sceneFactory.create(sceneContext)
				}
			}
			detachRemovedFactories(entityAttachments, validFactories, context)
		}

		fun attachPlayer(context: MinecraftKoolClientPlayerContext) {
			lastPlayerContext = context
			val validFactories = validFactoryKeys()
			MinecraftKoolClientSceneAttachments.forEachPlayerFactory(context.player) { factory ->
				addFactoryKey(validFactories, factory)
				attachAttachment(playerAttachments, context, factory) { sceneFactory, sceneContext ->
					sceneFactory.create(sceneContext)
				}
			}
			detachRemovedFactories(playerAttachments, validFactories, context)
		}

		fun tick(context: MinecraftKoolClientEntityContext) {
			lastEntityContext = context
			entityAttachments.values.forEach { attachment -> attachment.tick(context) }
		}

		fun tickPlayer(context: MinecraftKoolClientPlayerContext) {
			lastPlayerContext = context
			playerAttachments.values.forEach { attachment -> attachment.tick(context) }
		}

		fun refreshFactories() {
			lastEntityContext?.let(::attach)
			lastPlayerContext?.let(::attachPlayer)
		}

		fun detach(context: MinecraftKoolClientEntityContext) {
			val entityScenes = entityAttachments.values.toList()
			entityAttachments.clear()
			var entityIndex = 0
			while (entityIndex < entityScenes.size) {
				detachAttachment(entityScenes[entityIndex], context)
				entityIndex++
			}
			val playerContext = lastPlayerContext
			if (playerContext != null) {
				val playerScenes = playerAttachments.values.toList()
				playerAttachments.clear()
				var playerIndex = 0
				while (playerIndex < playerScenes.size) {
					detachAttachment(playerScenes[playerIndex], playerContext)
					playerIndex++
				}
			}
			lastEntityContext = null
			lastPlayerContext = null
		}

		fun detachCurrent() {
			lastEntityContext?.let(::detach)
		}
	}

	private class BlockEntityAttachmentState {
		private val attachments = IdentityHashMap<Any, MinecraftKoolClientSceneAttachment<MinecraftKoolClientBlockEntityContext>>()
		private var lastContext: MinecraftKoolClientBlockEntityContext? = null

		fun attach(context: MinecraftKoolClientBlockEntityContext) {
			lastContext = context
			val validFactories = validFactoryKeys()
			MinecraftKoolClientSceneAttachments.forEachBlockEntityFactory(
				blockEntityType = context.blockEntity.type,
				block = context.blockEntity.blockState.block
			) { factory ->
				addFactoryKey(validFactories, factory)
				attachAttachment(attachments, context, factory) { sceneFactory, sceneContext ->
					sceneFactory.create(sceneContext)
				}
			}
			detachRemovedFactories(attachments, validFactories, context)
		}

		fun refreshFactories() {
			lastContext?.let(::attach)
		}

		fun detach(context: MinecraftKoolClientBlockEntityContext) {
			val attachedScenes = attachments.values.toList()
			attachments.clear()
			var index = 0
			while (index < attachedScenes.size) {
				detachAttachment(attachedScenes[index], context)
				index++
			}
			lastContext = null
		}

		fun detachCurrent() {
			lastContext?.let(::detach)
		}
	}

	private class ScreenAttachmentState {
		private val attachments = IdentityHashMap<Any, MinecraftKoolClientSceneAttachment<MinecraftKoolScreenContext>>()
		private var lastContext: MinecraftKoolScreenContext? = null

		fun attach(context: MinecraftKoolScreenContext) {
			lastContext = context
			val validFactories = validFactoryKeys()
			MinecraftKoolClientSceneAttachments.forEachScreenFactory(context.screen) { factory ->
				addFactoryKey(validFactories, factory)
				attachAttachment(attachments, context, factory) { sceneFactory, sceneContext ->
					sceneFactory.create(sceneContext)
				}
			}
			detachRemovedFactories(attachments, validFactories, context)
		}

		fun tick(context: MinecraftKoolScreenContext) {
			lastContext = context
			attachments.values.forEach { attachment -> attachment.tick(context) }
		}

		fun refreshFactories() {
			lastContext?.let(::attach)
		}

		fun detach(context: MinecraftKoolScreenContext) {
			val attachedScenes = attachments.values.toList()
			attachments.clear()
			var index = 0
			while (index < attachedScenes.size) {
				detachAttachment(attachedScenes[index], context)
				index++
			}
			lastContext = null
		}
	}
}
