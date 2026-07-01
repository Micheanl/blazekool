package com.micheanl.kool.minecraft

import com.micheanl.kool.api.minecraft.MinecraftKoolBindings
import com.micheanl.kool.api.minecraft.MinecraftKoolPlayerBinding
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.chunk.LevelChunk
import java.util.IdentityHashMap

object MinecraftKoolBindingDispatcher {
	fun dispatchServerStarting(context: MinecraftKoolServerContext) {
		MinecraftKoolBindings.forEachServerBinding { binding -> binding.onServerStarting(context) }
	}

	fun dispatchServerStarted(context: MinecraftKoolServerContext) {
		MinecraftKoolBindings.forEachServerBinding { binding -> binding.onServerStarted(context) }
	}

	fun dispatchServerStopping(context: MinecraftKoolServerContext) {
		MinecraftKoolBindings.forEachServerBinding { binding -> binding.onServerStopping(context) }
	}

	fun dispatchServerStopped(context: MinecraftKoolServerContext) {
		MinecraftKoolBindings.forEachServerBinding { binding -> binding.onServerStopped(context) }
	}

	fun dispatchServerTickStart(context: MinecraftKoolServerContext) {
		MinecraftKoolBindings.forEachServerBinding { binding -> binding.onServerTickStart(context) }
	}

	fun dispatchServerTickEnd(context: MinecraftKoolServerContext) {
		MinecraftKoolBindings.forEachServerBinding { binding -> binding.onServerTickEnd(context) }
	}

	fun dispatchLevelLoad(server: MinecraftServer, level: ServerLevel) {
		val context = MinecraftKoolLevelContext(level, server)
		MinecraftKoolRuntimeState.update(context)
		MinecraftKoolBindings.forEachLevelBinding(level) { binding -> binding.onLoad(context) }
	}

	fun dispatchLevelUnload(server: MinecraftServer, level: ServerLevel) {
		val context = MinecraftKoolLevelContext(level, server)
		MinecraftKoolRuntimeState.update(context)
		MinecraftKoolBindings.forEachLevelBinding(level) { binding -> binding.onUnload(context) }
		MinecraftKoolRuntimeState.clearLevel(level)
	}

	fun dispatchLevelTickStart(context: MinecraftKoolLevelContext) {
		MinecraftKoolBindings.forEachLevelBinding(context.level) { binding -> binding.onTickStart(context) }
	}

	fun dispatchLevelTickEnd(context: MinecraftKoolLevelContext) {
		MinecraftKoolBindings.forEachLevelBinding(context.level) { binding -> binding.onTickEnd(context) }
		val level = context.level as? ServerLevel ?: return
		dispatchLoadedEntityTicks(level)
	}

	fun dispatchChunkLoad(level: ServerLevel, chunk: LevelChunk, generated: Boolean) {
		val context = MinecraftKoolChunkContext(level, chunk, generated)
		MinecraftKoolBindings.forEachChunkBinding(level) { binding -> binding.onLoad(context) }
	}

	fun dispatchChunkUnload(level: ServerLevel, chunk: LevelChunk) {
		val context = MinecraftKoolChunkContext(level, chunk)
		MinecraftKoolBindings.forEachChunkBinding(level) { binding -> binding.onUnload(context) }
	}

	fun dispatchBlockEntityLoad(blockEntity: BlockEntity, level: ServerLevel) {
		val context = MinecraftKoolBlockEntityContext(blockEntity, level)
		MinecraftKoolBindings.forEachBlockEntityBinding(blockEntity.type) { binding -> binding.onLoad(context) }
		MinecraftKoolBindings.forEachBlockBinding(blockEntity.blockState.block) { binding -> binding.onBlockEntityLoad(context) }
	}

	fun dispatchBlockEntityUnload(blockEntity: BlockEntity, level: ServerLevel) {
		val context = MinecraftKoolBlockEntityContext(blockEntity, level)
		MinecraftKoolBindings.forEachBlockEntityBinding(blockEntity.type) { binding -> binding.onUnload(context) }
		MinecraftKoolBindings.forEachBlockBinding(blockEntity.blockState.block) { binding -> binding.onBlockEntityUnload(context) }
	}

	fun dispatchEntityAllowLoad(
		entity: Entity,
		level: ServerLevel,
		spawnReason: EntitySpawnReason?,
		loadedFromDisk: Boolean
	): Boolean {
		val context = MinecraftKoolEntityContext(entity, level, spawnReason, loadedFromDisk)
		var allowed = true
		MinecraftKoolBindings.forEachEntityBinding(entity.type) { binding ->
			if (!binding.onAllowLoad(context)) {
				allowed = false
			}
		}
		return allowed
	}

	fun dispatchEntityLoad(entity: Entity, level: ServerLevel) {
		val context = MinecraftKoolEntityContext(entity, level, entity.spawnReason(), entity.isLoadedFromDisk)
		MinecraftKoolBindings.forEachEntityBinding(entity.type) { binding -> binding.onLoad(context) }
	}

	fun dispatchEntityUnload(entity: Entity, level: ServerLevel) {
		val context = MinecraftKoolEntityContext(entity, level)
		MinecraftKoolBindings.forEachEntityBinding(entity.type) { binding -> binding.onUnload(context) }
	}

	fun dispatchEntityLevelChanged(originalEntity: Entity, newEntity: Entity, origin: ServerLevel, destination: ServerLevel) {
		val context = MinecraftKoolEntityLevelChangeContext(originalEntity, newEntity, origin, destination)
		val seen = IdentityHashMap<Any, Unit>()
		MinecraftKoolBindings.forEachEntityBinding(originalEntity.type) { binding ->
			seen[binding] = Unit
			binding.onLevelChanged(context)
		}
		MinecraftKoolBindings.forEachEntityBinding(newEntity.type) { binding ->
			if (!seen.containsKey(binding)) {
				binding.onLevelChanged(context)
			}
		}
	}

	fun dispatchPlayerLevelChanged(player: ServerPlayer, origin: ServerLevel, destination: ServerLevel) {
		val context = MinecraftKoolPlayerLevelChangeContext(player, origin, destination)
		MinecraftKoolBindings.forEachPlayerBinding(player) { binding -> binding.onLevelChanged(context) }
	}

	fun dispatchPlayerJoin(player: ServerPlayer) {
		val context = MinecraftKoolPlayerContext(player, player.level(), player.level().server)
		MinecraftKoolRuntimeState.update(context)
		MinecraftKoolBindings.forEachPlayerBinding(player) { binding -> binding.onJoin(context) }
	}

	fun dispatchPlayerLeave(player: ServerPlayer) {
		val context = MinecraftKoolPlayerContext(player, player.level(), player.level().server)
		MinecraftKoolRuntimeState.update(context)
		MinecraftKoolBindings.forEachPlayerBinding(player) { binding -> binding.onLeave(context) }
	}

	fun dispatchPlayerTick(context: MinecraftKoolPlayerContext) {
		MinecraftKoolBindings.forEachPlayerBinding(context.player) { binding -> binding.onTick(context) }
		dispatchPlayerInventoryItemTicks(context.player as? ServerPlayer ?: return)
	}

	fun dispatchPlayerCopy(oldPlayer: ServerPlayer, newPlayer: ServerPlayer, alive: Boolean) {
		val context = MinecraftKoolPlayerCopyContext(oldPlayer, newPlayer, alive)
		dispatchPlayerPairBinding(oldPlayer, newPlayer) { binding -> binding.onCopyFrom(context) }
	}

	fun dispatchPlayerRespawn(oldPlayer: ServerPlayer, newPlayer: ServerPlayer, alive: Boolean) {
		val context = MinecraftKoolPlayerCopyContext(oldPlayer, newPlayer, alive)
		dispatchPlayerPairBinding(oldPlayer, newPlayer) { binding -> binding.onRespawn(context) }
	}

	fun dispatchUseBlock(context: MinecraftKoolInteractionContext): InteractionResult {
		return dispatchInteractionResult(
			context = context,
			blockHandler = { binding -> binding.onUse(context) },
			itemHandler = { binding -> binding.onUseBlock(context) },
			playerHandler = { binding -> binding.onUseBlock(context) }
		)
	}

	fun dispatchUseItem(context: MinecraftKoolInteractionContext): InteractionResult {
		return dispatchInteractionResult(
			context = context,
			blockHandler = null,
			itemHandler = { binding -> binding.onUseItem(context) },
			playerHandler = { binding -> binding.onUseItem(context) }
		)
	}

	fun dispatchAttackBlock(context: MinecraftKoolInteractionContext): InteractionResult {
		return dispatchInteractionResult(
			context = context,
			blockHandler = { binding -> binding.onAttack(context) },
			itemHandler = { binding -> binding.onAttackBlock(context) },
			playerHandler = { binding -> binding.onAttackBlock(context) }
		)
	}

	fun dispatchBlockBreakBefore(context: MinecraftKoolInteractionContext): Boolean {
		var allowed = true
		context.blockState?.block?.let { block ->
			MinecraftKoolBindings.forEachBlockBinding(block) { binding ->
				if (!binding.onBreakBefore(context)) {
					allowed = false
				}
			}
		}
		MinecraftKoolBindings.forEachItemBinding(context.itemStack) { binding ->
			if (!binding.onBlockBreakBefore(context)) {
				allowed = false
			}
		}
		MinecraftKoolBindings.forEachPlayerBinding(context.player) { binding ->
			if (!binding.onBlockBreakBefore(context)) {
				allowed = false
			}
		}
		return allowed
	}

	fun dispatchBlockBreakAfter(context: MinecraftKoolInteractionContext) {
		context.blockState?.block?.let { block ->
			MinecraftKoolBindings.forEachBlockBinding(block) { binding -> binding.onBreakAfter(context) }
		}
		MinecraftKoolBindings.forEachItemBinding(context.itemStack) { binding -> binding.onBlockBreakAfter(context) }
		MinecraftKoolBindings.forEachPlayerBinding(context.player) { binding -> binding.onBlockBreakAfter(context) }
	}

	fun dispatchBlockBreakCanceled(context: MinecraftKoolInteractionContext) {
		context.blockState?.block?.let { block ->
			MinecraftKoolBindings.forEachBlockBinding(block) { binding -> binding.onBreakCanceled(context) }
		}
		MinecraftKoolBindings.forEachItemBinding(context.itemStack) { binding -> binding.onBlockBreakCanceled(context) }
		MinecraftKoolBindings.forEachPlayerBinding(context.player) { binding -> binding.onBlockBreakCanceled(context) }
	}

	fun dispatchAllowDamage(entity: LivingEntity, source: DamageSource, amount: Float): Boolean {
		val level = entity.level() as? ServerLevel ?: return true
		val context = MinecraftKoolLivingDamageContext(
			entity = entity,
			level = level,
			source = source,
			phase = MinecraftKoolDamagePhase.ALLOW_DAMAGE,
			amount = amount
		)
		var allowed = true
		MinecraftKoolBindings.forEachEntityBinding(entity.type) { binding ->
			if (!binding.onAllowDamage(context)) {
				allowed = false
			}
		}
		return allowed
	}

	fun dispatchAfterDamage(
		entity: LivingEntity,
		source: DamageSource,
		baseDamageTaken: Float,
		damageTaken: Float,
		blocked: Boolean
	) {
		val level = entity.level() as? ServerLevel ?: return
		val context = MinecraftKoolLivingDamageContext(
			entity = entity,
			level = level,
			source = source,
			phase = MinecraftKoolDamagePhase.AFTER_DAMAGE,
			amount = damageTaken,
			baseDamageTaken = baseDamageTaken,
			damageTaken = damageTaken,
			blocked = blocked
		)
		MinecraftKoolBindings.forEachEntityBinding(entity.type) { binding -> binding.onAfterDamage(context) }
	}

	fun dispatchAllowDeath(entity: LivingEntity, source: DamageSource, amount: Float): Boolean {
		val level = entity.level() as? ServerLevel ?: return true
		val context = MinecraftKoolLivingDamageContext(
			entity = entity,
			level = level,
			source = source,
			phase = MinecraftKoolDamagePhase.ALLOW_DEATH,
			amount = amount
		)
		var allowed = true
		MinecraftKoolBindings.forEachEntityBinding(entity.type) { binding ->
			if (!binding.onAllowDeath(context)) {
				allowed = false
			}
		}
		return allowed
	}

	fun dispatchAfterDeath(entity: LivingEntity, source: DamageSource) {
		val level = entity.level() as? ServerLevel ?: return
		val context = MinecraftKoolLivingDamageContext(
			entity = entity,
			level = level,
			source = source,
			phase = MinecraftKoolDamagePhase.AFTER_DEATH,
			amount = 0.0f
		)
		MinecraftKoolBindings.forEachEntityBinding(entity.type) { binding -> binding.onAfterDeath(context) }
	}

	fun dispatchKilledOtherEntity(level: ServerLevel, attacker: Entity, killedEntity: LivingEntity, source: DamageSource) {
		val context = MinecraftKoolEntityKillContext(level, attacker, killedEntity, source)
		MinecraftKoolBindings.forEachEntityBinding(attacker.type) { binding -> binding.onKilledOtherEntity(context) }
	}

	fun dispatchEquipmentChange(
		entity: LivingEntity,
		slot: EquipmentSlot,
		previousStack: ItemStack,
		currentStack: ItemStack
	) {
		val level = entity.level() as? ServerLevel ?: return
		val context = MinecraftKoolEquipmentContext(entity, level, slot, previousStack, currentStack)
		MinecraftKoolBindings.forEachEntityBinding(entity.type) { binding -> binding.onEquipmentChange(context) }
		MinecraftKoolBindings.forEachItemBinding(previousStack) { binding -> binding.onEquipmentChange(context) }
		MinecraftKoolBindings.forEachItemBinding(currentStack) { binding -> binding.onEquipmentChange(context) }
	}

	private fun dispatchLoadedEntityTicks(level: ServerLevel) {
		val entities = level.allEntities.iterator()
		while (entities.hasNext()) {
			val entity = entities.next()
			if (!entity.isRemoved) {
				val context = MinecraftKoolEntityContext(entity, level)
				MinecraftKoolBindings.forEachEntityBinding(entity.type) { binding -> binding.onTick(context) }
			}
		}
	}

	private fun dispatchPlayerInventoryItemTicks(player: ServerPlayer) {
		val inventory = player.inventory
		val selectedSlot = inventory.selectedSlot
		var slot = 0
		while (slot < inventory.containerSize) {
			val stack = inventory.getItem(slot)
			if (!stack.isEmpty) {
				val context = MinecraftKoolPlayerInventoryItemContext(
					player = player,
					level = player.level(),
					slot = slot,
					itemStack = stack,
					selected = slot == selectedSlot
				)
				MinecraftKoolBindings.forEachItemBinding(stack) { binding -> binding.onInventoryTick(context) }
				MinecraftKoolBindings.forEachPlayerBinding(player) { binding -> binding.onInventoryItemTick(context) }
			}
			slot++
		}
	}

	private fun dispatchPlayerPairBinding(
		oldPlayer: ServerPlayer,
		newPlayer: ServerPlayer,
		handler: (MinecraftKoolPlayerBinding) -> Unit
	) {
		val seen = IdentityHashMap<Any, Unit>()
		MinecraftKoolBindings.forEachPlayerBinding(oldPlayer) { binding ->
			seen[binding] = Unit
			handler(binding)
		}
		MinecraftKoolBindings.forEachPlayerBinding(newPlayer) { binding ->
			if (!seen.containsKey(binding)) {
				handler(binding)
			}
		}
	}

	private fun dispatchInteractionResult(
		context: MinecraftKoolInteractionContext,
		blockHandler: (((com.micheanl.kool.api.minecraft.MinecraftKoolBlockBinding) -> InteractionResult))?,
		itemHandler: (com.micheanl.kool.api.minecraft.MinecraftKoolItemBinding) -> InteractionResult,
		playerHandler: (com.micheanl.kool.api.minecraft.MinecraftKoolPlayerBinding) -> InteractionResult
	): InteractionResult {
		if (blockHandler != null) {
			context.blockState?.block?.let { block ->
				var blockResult: InteractionResult = InteractionResult.PASS
				MinecraftKoolBindings.forEachBlockBinding(block) { binding ->
					if (blockResult == InteractionResult.PASS) {
						blockResult = blockHandler(binding)
					}
				}
				if (blockResult != InteractionResult.PASS) {
					return blockResult
				}
			}
		}

		var itemResult: InteractionResult = InteractionResult.PASS
		MinecraftKoolBindings.forEachItemBinding(context.itemStack) { binding ->
			if (itemResult == InteractionResult.PASS) {
				itemResult = itemHandler(binding)
			}
		}
		if (itemResult != InteractionResult.PASS) {
			return itemResult
		}

		var playerResult: InteractionResult = InteractionResult.PASS
		MinecraftKoolBindings.forEachPlayerBinding(context.player) { binding ->
			if (playerResult == InteractionResult.PASS) {
				playerResult = playerHandler(binding)
			}
		}
		return playerResult
	}
}
