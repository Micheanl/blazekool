package com.micheanl.kool.api.minecraft

import com.micheanl.kool.minecraft.MinecraftKoolBlockEntityContext
import com.micheanl.kool.minecraft.MinecraftKoolChunkContext
import com.micheanl.kool.minecraft.MinecraftKoolEntityContext
import com.micheanl.kool.minecraft.MinecraftKoolEntityKillContext
import com.micheanl.kool.minecraft.MinecraftKoolEntityLevelChangeContext
import com.micheanl.kool.minecraft.MinecraftKoolEquipmentContext
import com.micheanl.kool.minecraft.MinecraftKoolInteractionContext
import com.micheanl.kool.minecraft.MinecraftKoolLevelContext
import com.micheanl.kool.minecraft.MinecraftKoolLivingDamageContext
import com.micheanl.kool.minecraft.MinecraftKoolPlayerContext
import com.micheanl.kool.minecraft.MinecraftKoolPlayerCopyContext
import com.micheanl.kool.minecraft.MinecraftKoolPlayerInventoryItemContext
import com.micheanl.kool.minecraft.MinecraftKoolPlayerLevelChangeContext
import com.micheanl.kool.minecraft.MinecraftKoolServerContext
import net.minecraft.resources.ResourceKey
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

object MinecraftKoolBindings {
	private val serverBindings = CopyOnWriteArrayList<MinecraftKoolServerBinding>()
	private val globalLevelBindings = CopyOnWriteArrayList<MinecraftKoolLevelBinding>()
	private val dimensionLevelBindings = ConcurrentHashMap<ResourceKey<Level>, CopyOnWriteArrayList<MinecraftKoolLevelBinding>>()
	private val globalChunkBindings = CopyOnWriteArrayList<MinecraftKoolChunkBinding>()
	private val dimensionChunkBindings = ConcurrentHashMap<ResourceKey<Level>, CopyOnWriteArrayList<MinecraftKoolChunkBinding>>()
	private val blockBindings = ConcurrentHashMap<Block, CopyOnWriteArrayList<MinecraftKoolBlockBinding>>()
	private val itemBindings = ConcurrentHashMap<Item, CopyOnWriteArrayList<MinecraftKoolItemBinding>>()
	private val entityTypeBindings = ConcurrentHashMap<EntityType<*>, CopyOnWriteArrayList<MinecraftKoolEntityBinding>>()
	private val globalEntityBindings = CopyOnWriteArrayList<MinecraftKoolEntityBinding>()
	private val blockEntityTypeBindings = ConcurrentHashMap<BlockEntityType<*>, CopyOnWriteArrayList<MinecraftKoolBlockEntityBinding>>()
	private val globalBlockEntityBindings = CopyOnWriteArrayList<MinecraftKoolBlockEntityBinding>()
	private val globalPlayerBindings = CopyOnWriteArrayList<MinecraftKoolPlayerBinding>()
	private val playerBindings = ConcurrentHashMap<UUID, CopyOnWriteArrayList<MinecraftKoolPlayerBinding>>()

	fun registerServer(binding: MinecraftKoolServerBinding): MinecraftKoolBindingHandle {
		return registerList(serverBindings, binding)
	}

	fun registerLevel(binding: MinecraftKoolLevelBinding): MinecraftKoolBindingHandle {
		return registerList(globalLevelBindings, binding)
	}

	fun registerLevel(dimension: ResourceKey<Level>, binding: MinecraftKoolLevelBinding): MinecraftKoolBindingHandle {
		return registerMap(dimensionLevelBindings, dimension, binding)
	}

	fun registerChunk(binding: MinecraftKoolChunkBinding): MinecraftKoolBindingHandle {
		return registerList(globalChunkBindings, binding)
	}

	fun registerChunk(dimension: ResourceKey<Level>, binding: MinecraftKoolChunkBinding): MinecraftKoolBindingHandle {
		return registerMap(dimensionChunkBindings, dimension, binding)
	}

	fun registerBlock(block: Block, binding: MinecraftKoolBlockBinding): MinecraftKoolBindingHandle {
		return registerMap(blockBindings, block, binding)
	}

	fun registerItem(item: Item, binding: MinecraftKoolItemBinding): MinecraftKoolBindingHandle {
		return registerMap(itemBindings, item, binding)
	}

	fun registerEntity(binding: MinecraftKoolEntityBinding): MinecraftKoolBindingHandle {
		return registerList(globalEntityBindings, binding)
	}

	fun registerEntity(entityType: EntityType<*>, binding: MinecraftKoolEntityBinding): MinecraftKoolBindingHandle {
		return registerMap(entityTypeBindings, entityType, binding)
	}

	fun registerBlockEntity(binding: MinecraftKoolBlockEntityBinding): MinecraftKoolBindingHandle {
		return registerList(globalBlockEntityBindings, binding)
	}

	fun registerBlockEntity(blockEntityType: BlockEntityType<*>, binding: MinecraftKoolBlockEntityBinding): MinecraftKoolBindingHandle {
		return registerMap(blockEntityTypeBindings, blockEntityType, binding)
	}

	fun registerPlayer(binding: MinecraftKoolPlayerBinding): MinecraftKoolBindingHandle {
		return registerList(globalPlayerBindings, binding)
	}

	fun registerPlayer(playerId: UUID, binding: MinecraftKoolPlayerBinding): MinecraftKoolBindingHandle {
		return registerMap(playerBindings, playerId, binding)
	}

	internal fun forEachServerBinding(handler: (MinecraftKoolServerBinding) -> Unit) {
		forEach(serverBindings, handler)
	}

	internal fun forEachLevelBinding(level: Level, handler: (MinecraftKoolLevelBinding) -> Unit) {
		forEach(globalLevelBindings, handler)
		forEach(dimensionLevelBindings[level.dimension()], handler)
	}

	internal fun forEachChunkBinding(level: Level, handler: (MinecraftKoolChunkBinding) -> Unit) {
		forEach(globalChunkBindings, handler)
		forEach(dimensionChunkBindings[level.dimension()], handler)
	}

	internal fun forEachBlockBinding(block: Block, handler: (MinecraftKoolBlockBinding) -> Unit) {
		forEach(blockBindings[block], handler)
	}

	internal fun forEachItemBinding(stack: ItemStack, handler: (MinecraftKoolItemBinding) -> Unit) {
		if (!stack.isEmpty) {
			forEach(itemBindings[stack.item], handler)
		}
	}

	internal fun forEachEntityBinding(entityType: EntityType<*>, handler: (MinecraftKoolEntityBinding) -> Unit) {
		forEach(globalEntityBindings, handler)
		forEach(entityTypeBindings[entityType], handler)
	}

	internal fun forEachBlockEntityBinding(
		blockEntityType: BlockEntityType<*>,
		handler: (MinecraftKoolBlockEntityBinding) -> Unit
	) {
		forEach(globalBlockEntityBindings, handler)
		forEach(blockEntityTypeBindings[blockEntityType], handler)
	}

	internal fun forEachPlayerBinding(player: Player, handler: (MinecraftKoolPlayerBinding) -> Unit) {
		forEach(globalPlayerBindings, handler)
		forEach(playerBindings[player.uuid], handler)
	}

	private fun <T> registerList(list: CopyOnWriteArrayList<T>, binding: T): MinecraftKoolBindingHandle {
		list.addIfAbsent(binding)
		return MinecraftKoolBindingHandle {
			list.remove(binding)
		}
	}

	private fun <K, T> registerMap(
		map: ConcurrentHashMap<K, CopyOnWriteArrayList<T>>,
		key: K,
		binding: T
	): MinecraftKoolBindingHandle {
		val list = map.computeIfAbsent(key) { CopyOnWriteArrayList() }
		list.addIfAbsent(binding)
		return MinecraftKoolBindingHandle {
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

fun interface MinecraftKoolBindingHandle : AutoCloseable {
	override fun close()
}

interface MinecraftKoolServerBinding {
	fun onServerStarting(context: MinecraftKoolServerContext) {
	}

	fun onServerStarted(context: MinecraftKoolServerContext) {
	}

	fun onServerStopping(context: MinecraftKoolServerContext) {
	}

	fun onServerStopped(context: MinecraftKoolServerContext) {
	}

	fun onServerTickStart(context: MinecraftKoolServerContext) {
	}

	fun onServerTickEnd(context: MinecraftKoolServerContext) {
	}
}

interface MinecraftKoolLevelBinding {
	fun onLoad(context: MinecraftKoolLevelContext) {
	}

	fun onUnload(context: MinecraftKoolLevelContext) {
	}

	fun onTickStart(context: MinecraftKoolLevelContext) {
	}

	fun onTickEnd(context: MinecraftKoolLevelContext) {
	}
}

interface MinecraftKoolChunkBinding {
	fun onLoad(context: MinecraftKoolChunkContext) {
	}

	fun onUnload(context: MinecraftKoolChunkContext) {
	}
}

interface MinecraftKoolBlockBinding {
	fun onUse(context: MinecraftKoolInteractionContext): InteractionResult {
		return InteractionResult.PASS
	}

	fun onAttack(context: MinecraftKoolInteractionContext): InteractionResult {
		return InteractionResult.PASS
	}

	fun onBreakBefore(context: MinecraftKoolInteractionContext): Boolean {
		return true
	}

	fun onBreakAfter(context: MinecraftKoolInteractionContext) {
	}

	fun onBreakCanceled(context: MinecraftKoolInteractionContext) {
	}

	fun onBlockEntityLoad(context: MinecraftKoolBlockEntityContext) {
	}

	fun onBlockEntityUnload(context: MinecraftKoolBlockEntityContext) {
	}
}

interface MinecraftKoolItemBinding {
	fun onUseItem(context: MinecraftKoolInteractionContext): InteractionResult {
		return InteractionResult.PASS
	}

	fun onUseBlock(context: MinecraftKoolInteractionContext): InteractionResult {
		return InteractionResult.PASS
	}

	fun onAttackBlock(context: MinecraftKoolInteractionContext): InteractionResult {
		return InteractionResult.PASS
	}

	fun onBlockBreakBefore(context: MinecraftKoolInteractionContext): Boolean {
		return true
	}

	fun onBlockBreakAfter(context: MinecraftKoolInteractionContext) {
	}

	fun onBlockBreakCanceled(context: MinecraftKoolInteractionContext) {
	}

	fun onInventoryTick(context: MinecraftKoolPlayerInventoryItemContext) {
	}

	fun onEquipmentChange(context: MinecraftKoolEquipmentContext) {
	}
}

interface MinecraftKoolEntityBinding {
	fun onAllowLoad(context: MinecraftKoolEntityContext): Boolean {
		return true
	}

	fun onLoad(context: MinecraftKoolEntityContext) {
	}

	fun onUnload(context: MinecraftKoolEntityContext) {
	}

	fun onTick(context: MinecraftKoolEntityContext) {
	}

	fun onLevelChanged(context: MinecraftKoolEntityLevelChangeContext) {
	}

	fun onAllowDamage(context: MinecraftKoolLivingDamageContext): Boolean {
		return true
	}

	fun onAfterDamage(context: MinecraftKoolLivingDamageContext) {
	}

	fun onAllowDeath(context: MinecraftKoolLivingDamageContext): Boolean {
		return true
	}

	fun onAfterDeath(context: MinecraftKoolLivingDamageContext) {
	}

	fun onKilledOtherEntity(context: MinecraftKoolEntityKillContext) {
	}

	fun onEquipmentChange(context: MinecraftKoolEquipmentContext) {
	}
}

interface MinecraftKoolBlockEntityBinding {
	fun onLoad(context: MinecraftKoolBlockEntityContext) {
	}

	fun onUnload(context: MinecraftKoolBlockEntityContext) {
	}
}

interface MinecraftKoolPlayerBinding {
	fun onJoin(context: MinecraftKoolPlayerContext) {
	}

	fun onLeave(context: MinecraftKoolPlayerContext) {
	}

	fun onTick(context: MinecraftKoolPlayerContext) {
	}

	fun onLevelChanged(context: MinecraftKoolPlayerLevelChangeContext) {
	}

	fun onCopyFrom(context: MinecraftKoolPlayerCopyContext) {
	}

	fun onRespawn(context: MinecraftKoolPlayerCopyContext) {
	}

	fun onUseBlock(context: MinecraftKoolInteractionContext): InteractionResult {
		return InteractionResult.PASS
	}

	fun onUseItem(context: MinecraftKoolInteractionContext): InteractionResult {
		return InteractionResult.PASS
	}

	fun onAttackBlock(context: MinecraftKoolInteractionContext): InteractionResult {
		return InteractionResult.PASS
	}

	fun onBlockBreakBefore(context: MinecraftKoolInteractionContext): Boolean {
		return true
	}

	fun onBlockBreakAfter(context: MinecraftKoolInteractionContext) {
	}

	fun onBlockBreakCanceled(context: MinecraftKoolInteractionContext) {
	}

	fun onInventoryItemTick(context: MinecraftKoolPlayerInventoryItemContext) {
	}
}
