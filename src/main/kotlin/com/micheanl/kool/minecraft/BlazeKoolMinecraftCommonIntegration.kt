package com.micheanl.kool.minecraft

import com.micheanl.kool.api.minecraft.MinecraftKoolCommonEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraft.server.packs.resources.CloseableResourceManager
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.phys.BlockHitResult

object BlazeKoolMinecraftCommonIntegration {
	@Volatile
	private var registered = false

	fun register() {
		if (registered) {
			return
		}
		registered = true

		ServerLifecycleEvents.SERVER_STARTING.register(::onServerStarting)
		ServerLifecycleEvents.SERVER_STARTED.register(::onServerStarted)
		ServerLifecycleEvents.SERVER_STOPPING.register(::onServerStopping)
		ServerLifecycleEvents.SERVER_STOPPED.register(::onServerStopped)
		ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register(::onDataPackSync)
		ServerLifecycleEvents.START_DATA_PACK_RELOAD.register(::onDataPackReloadStart)
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register(::onDataPackReloadEnd)
		ServerLifecycleEvents.BEFORE_SAVE.register(::onBeforeSave)
		ServerLifecycleEvents.AFTER_SAVE.register(::onAfterSave)

		ServerLevelEvents.LOAD.register(::onLevelLoad)
		ServerLevelEvents.UNLOAD.register(::onLevelUnload)

		ServerTickEvents.START_SERVER_TICK.register(::onServerTickStart)
		ServerTickEvents.END_SERVER_TICK.register(::onServerTickEnd)
		ServerTickEvents.START_LEVEL_TICK.register(::onLevelTickStart)
		ServerTickEvents.END_LEVEL_TICK.register(::onLevelTickEnd)

		ServerChunkEvents.CHUNK_LOAD.register(::onChunkLoad)
		ServerChunkEvents.CHUNK_UNLOAD.register(::onChunkUnload)
		ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register(::onBlockEntityLoad)
		ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register(::onBlockEntityUnload)
		ServerEntityEvents.ALLOW_LOAD.register(::onEntityAllowLoad)
		ServerEntityEvents.ENTITY_LOAD.register(::onEntityLoad)
		ServerEntityEvents.ENTITY_UNLOAD.register(::onEntityUnload)
		ServerEntityEvents.EQUIPMENT_CHANGE.register(::onEquipmentChange)
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(::onAllowDamage)
		ServerLivingEntityEvents.AFTER_DAMAGE.register(::onAfterDamage)
		ServerLivingEntityEvents.ALLOW_DEATH.register(::onAllowDeath)
		ServerLivingEntityEvents.AFTER_DEATH.register(::onAfterDeath)
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register(::onKilledOtherEntity)
		ServerEntityLevelChangeEvents.AFTER_ENTITY_CHANGE_LEVEL.register(::onEntityLevelChanged)
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register(::onPlayerLevelChanged)
		ServerPlayerEvents.JOIN.register(::onPlayerJoin)
		ServerPlayerEvents.LEAVE.register(::onPlayerLeave)
		ServerPlayerEvents.COPY_FROM.register(::onPlayerCopy)
		ServerPlayerEvents.AFTER_RESPAWN.register(::onPlayerRespawn)

		ServerPlayConnectionEvents.INIT.register(::onPlayInit)
		ServerPlayConnectionEvents.JOIN.register(::onPlayJoin)
		ServerPlayConnectionEvents.DISCONNECT.register(::onPlayDisconnect)

		UseBlockCallback.EVENT.register(::onUseBlock)
		UseItemCallback.EVENT.register(::onUseItem)
		AttackBlockCallback.EVENT.register(::onAttackBlock)
		PlayerBlockBreakEvents.BEFORE.register(::onBlockBreakBefore)
		PlayerBlockBreakEvents.AFTER.register(::onBlockBreakAfter)
		PlayerBlockBreakEvents.CANCELED.register(::onBlockBreakCanceled)
	}

	private fun onServerStarting(server: MinecraftServer) {
		val context = MinecraftKoolServerContext(server)
		MinecraftKoolRuntimeState.update(context)
		MinecraftKoolBindingDispatcher.dispatchServerStarting(context)
		MinecraftKoolCommonEvents.dispatchServerStarting(context)
	}

	private fun onServerStarted(server: MinecraftServer) {
		val context = MinecraftKoolServerContext(server)
		MinecraftKoolRuntimeState.update(context)
		MinecraftKoolBindingDispatcher.dispatchServerStarted(context)
		MinecraftKoolCommonEvents.dispatchServerStarted(context)
	}

	private fun onServerStopping(server: MinecraftServer) {
		val context = MinecraftKoolServerContext(server)
		MinecraftKoolRuntimeState.update(context)
		MinecraftKoolBindingDispatcher.dispatchServerStopping(context)
		MinecraftKoolCommonEvents.dispatchServerStopping(context)
	}

	private fun onServerStopped(server: MinecraftServer) {
		val context = MinecraftKoolServerContext(server)
		MinecraftKoolRuntimeState.update(context)
		MinecraftKoolBindingDispatcher.dispatchServerStopped(context)
		MinecraftKoolCommonEvents.dispatchServerStopped(context)
		MinecraftKoolRuntimeState.clearServer(server)
	}

	private fun onDataPackSync(player: ServerPlayer, joined: Boolean) {
		val context = MinecraftKoolDataPackSyncContext(player, joined)
		MinecraftKoolRuntimeState.update(context)
		MinecraftKoolCommonEvents.dispatchDataPackSync(context)
	}

	private fun onDataPackReloadStart(server: MinecraftServer, resourceManager: CloseableResourceManager) {
		val context = MinecraftKoolServerContext(server, resourceManager = resourceManager)
		MinecraftKoolRuntimeState.update(context)
		MinecraftKoolCommonEvents.dispatchDataPackReloadStart(context)
	}

	private fun onDataPackReloadEnd(server: MinecraftServer, resourceManager: CloseableResourceManager, success: Boolean) {
		val context = MinecraftKoolServerContext(server, resourceManager = resourceManager, success = success)
		MinecraftKoolRuntimeState.update(context)
		MinecraftKoolCommonEvents.dispatchDataPackReloadEnd(context)
	}

	private fun onBeforeSave(server: MinecraftServer, flush: Boolean, force: Boolean) {
		val context = MinecraftKoolServerContext(server, flush = flush, force = force)
		MinecraftKoolRuntimeState.update(context)
		MinecraftKoolCommonEvents.dispatchBeforeSave(context)
	}

	private fun onAfterSave(server: MinecraftServer, flush: Boolean, force: Boolean) {
		val context = MinecraftKoolServerContext(server, flush = flush, force = force)
		MinecraftKoolRuntimeState.update(context)
		MinecraftKoolCommonEvents.dispatchAfterSave(context)
	}

	private fun onLevelLoad(server: MinecraftServer, level: ServerLevel) {
		MinecraftKoolBindingDispatcher.dispatchLevelLoad(server, level)
	}

	private fun onLevelUnload(server: MinecraftServer, level: ServerLevel) {
		MinecraftKoolBindingDispatcher.dispatchLevelUnload(server, level)
	}

	private fun onServerTickStart(server: MinecraftServer) {
		val context = MinecraftKoolServerContext(server)
		MinecraftKoolRuntimeState.update(context)
		MinecraftKoolBindingDispatcher.dispatchServerTickStart(context)
		MinecraftKoolCommonEvents.dispatchServerTickStart(context)
	}

	private fun onServerTickEnd(server: MinecraftServer) {
		val context = MinecraftKoolServerContext(server)
		MinecraftKoolRuntimeState.update(context)
		MinecraftKoolBindingDispatcher.dispatchServerTickEnd(context)
		MinecraftKoolCommonEvents.dispatchServerTickEnd(context)
		val players = server.playerList.players
		var index = 0
		while (index < players.size) {
			val player = players[index]
			val playerContext = MinecraftKoolPlayerContext(player, player.level(), server)
			MinecraftKoolRuntimeState.update(playerContext)
			MinecraftKoolBindingDispatcher.dispatchPlayerTick(playerContext)
			MinecraftKoolCommonEvents.dispatchPlayerTick(playerContext)
			index++
		}
	}

	private fun onLevelTickStart(level: ServerLevel) {
		val context = MinecraftKoolLevelContext(level, level.server)
		MinecraftKoolRuntimeState.update(context)
		MinecraftKoolBindingDispatcher.dispatchLevelTickStart(context)
		MinecraftKoolCommonEvents.dispatchLevelTickStart(context)
	}

	private fun onLevelTickEnd(level: ServerLevel) {
		val context = MinecraftKoolLevelContext(level, level.server)
		MinecraftKoolRuntimeState.update(context)
		MinecraftKoolBindingDispatcher.dispatchLevelTickEnd(context)
		MinecraftKoolCommonEvents.dispatchLevelTickEnd(context)
	}

	private fun onChunkLoad(level: ServerLevel, chunk: LevelChunk, generated: Boolean) {
		MinecraftKoolBindingDispatcher.dispatchChunkLoad(level, chunk, generated)
	}

	private fun onChunkUnload(level: ServerLevel, chunk: LevelChunk) {
		MinecraftKoolBindingDispatcher.dispatchChunkUnload(level, chunk)
	}

	private fun onBlockEntityLoad(blockEntity: BlockEntity, level: ServerLevel) {
		MinecraftKoolBindingDispatcher.dispatchBlockEntityLoad(blockEntity, level)
	}

	private fun onBlockEntityUnload(blockEntity: BlockEntity, level: ServerLevel) {
		MinecraftKoolBindingDispatcher.dispatchBlockEntityUnload(blockEntity, level)
	}

	private fun onEntityAllowLoad(
		entity: Entity,
		level: ServerLevel,
		spawnReason: EntitySpawnReason?,
		loadedFromDisk: Boolean
	): Boolean {
		return MinecraftKoolBindingDispatcher.dispatchEntityAllowLoad(entity, level, spawnReason, loadedFromDisk)
	}

	private fun onEntityLoad(entity: Entity, level: ServerLevel) {
		MinecraftKoolBindingDispatcher.dispatchEntityLoad(entity, level)
	}

	private fun onEntityUnload(entity: Entity, level: ServerLevel) {
		MinecraftKoolBindingDispatcher.dispatchEntityUnload(entity, level)
	}

	private fun onEquipmentChange(
		entity: LivingEntity,
		slot: EquipmentSlot,
		previousStack: ItemStack,
		currentStack: ItemStack
	) {
		MinecraftKoolBindingDispatcher.dispatchEquipmentChange(entity, slot, previousStack, currentStack)
	}

	private fun onAllowDamage(entity: LivingEntity, source: DamageSource, amount: Float): Boolean {
		return MinecraftKoolBindingDispatcher.dispatchAllowDamage(entity, source, amount)
	}

	private fun onAfterDamage(
		entity: LivingEntity,
		source: DamageSource,
		baseDamageTaken: Float,
		damageTaken: Float,
		blocked: Boolean
	) {
		MinecraftKoolBindingDispatcher.dispatchAfterDamage(entity, source, baseDamageTaken, damageTaken, blocked)
	}

	private fun onAllowDeath(entity: LivingEntity, source: DamageSource, amount: Float): Boolean {
		return MinecraftKoolBindingDispatcher.dispatchAllowDeath(entity, source, amount)
	}

	private fun onAfterDeath(entity: LivingEntity, source: DamageSource) {
		MinecraftKoolBindingDispatcher.dispatchAfterDeath(entity, source)
	}

	private fun onKilledOtherEntity(level: ServerLevel, attacker: Entity, killedEntity: LivingEntity, source: DamageSource) {
		MinecraftKoolBindingDispatcher.dispatchKilledOtherEntity(level, attacker, killedEntity, source)
	}

	private fun onEntityLevelChanged(originalEntity: Entity, newEntity: Entity, origin: ServerLevel, destination: ServerLevel) {
		MinecraftKoolBindingDispatcher.dispatchEntityLevelChanged(originalEntity, newEntity, origin, destination)
	}

	private fun onPlayerLevelChanged(player: ServerPlayer, origin: ServerLevel, destination: ServerLevel) {
		MinecraftKoolBindingDispatcher.dispatchPlayerLevelChanged(player, origin, destination)
	}

	private fun onPlayerJoin(player: ServerPlayer) {
		MinecraftKoolBindingDispatcher.dispatchPlayerJoin(player)
	}

	private fun onPlayerLeave(player: ServerPlayer) {
		MinecraftKoolBindingDispatcher.dispatchPlayerLeave(player)
	}

	private fun onPlayerCopy(oldPlayer: ServerPlayer, newPlayer: ServerPlayer, alive: Boolean) {
		MinecraftKoolBindingDispatcher.dispatchPlayerCopy(oldPlayer, newPlayer, alive)
	}

	private fun onPlayerRespawn(oldPlayer: ServerPlayer, newPlayer: ServerPlayer, alive: Boolean) {
		MinecraftKoolBindingDispatcher.dispatchPlayerRespawn(oldPlayer, newPlayer, alive)
	}

	private fun onPlayInit(listener: ServerGamePacketListenerImpl, server: MinecraftServer) {
		val context = MinecraftKoolConnectionContext(listener, server)
		MinecraftKoolRuntimeState.update(context)
		MinecraftKoolCommonEvents.dispatchPlayInit(context)
	}

	private fun onPlayJoin(listener: ServerGamePacketListenerImpl, sender: PacketSender, server: MinecraftServer) {
		val context = MinecraftKoolConnectionContext(listener, server, sender, listener.player)
		MinecraftKoolRuntimeState.update(context)
		MinecraftKoolCommonEvents.dispatchPlayJoin(context)
	}

	private fun onPlayDisconnect(listener: ServerGamePacketListenerImpl, server: MinecraftServer) {
		val player = listener.player
		val context = MinecraftKoolConnectionContext(listener, server, player = player)
		MinecraftKoolRuntimeState.update(context)
		MinecraftKoolCommonEvents.dispatchPlayDisconnect(context)
		MinecraftKoolRuntimeState.clearPlayer(player)
	}

	private fun onUseBlock(player: Player, level: Level, hand: InteractionHand, hitResult: BlockHitResult): InteractionResult {
		val context = MinecraftKoolInteractionContext(
			phase = MinecraftKoolInteractionPhase.USE_BLOCK,
			player = player,
			level = level,
			hand = hand,
			itemStack = player.getItemInHand(hand),
			hitResult = hitResult,
			blockPos = hitResult.blockPos,
			blockState = level.getBlockState(hitResult.blockPos),
			blockEntity = level.getBlockEntity(hitResult.blockPos)
		)
		MinecraftKoolRuntimeState.update(context)
		val bindingResult = MinecraftKoolBindingDispatcher.dispatchUseBlock(context)
		if (bindingResult != InteractionResult.PASS) {
			return bindingResult
		}
		return MinecraftKoolCommonEvents.dispatchUseBlock(context)
	}

	private fun onUseItem(player: Player, level: Level, hand: InteractionHand): InteractionResult {
		val context = MinecraftKoolInteractionContext(
			phase = MinecraftKoolInteractionPhase.USE_ITEM,
			player = player,
			level = level,
			hand = hand,
			itemStack = player.getItemInHand(hand)
		)
		MinecraftKoolRuntimeState.update(context)
		val bindingResult = MinecraftKoolBindingDispatcher.dispatchUseItem(context)
		if (bindingResult != InteractionResult.PASS) {
			return bindingResult
		}
		return MinecraftKoolCommonEvents.dispatchUseItem(context)
	}

	private fun onAttackBlock(player: Player, level: Level, hand: InteractionHand, pos: BlockPos, direction: Direction): InteractionResult {
		val context = MinecraftKoolInteractionContext(
			phase = MinecraftKoolInteractionPhase.ATTACK_BLOCK,
			player = player,
			level = level,
			hand = hand,
			itemStack = player.getItemInHand(hand),
			blockPos = pos,
			direction = direction,
			blockState = level.getBlockState(pos),
			blockEntity = level.getBlockEntity(pos)
		)
		MinecraftKoolRuntimeState.update(context)
		val bindingResult = MinecraftKoolBindingDispatcher.dispatchAttackBlock(context)
		if (bindingResult != InteractionResult.PASS) {
			return bindingResult
		}
		return MinecraftKoolCommonEvents.dispatchAttackBlock(context)
	}

	private fun onBlockBreakBefore(level: Level, player: Player, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean {
		val context = blockBreakContext(MinecraftKoolInteractionPhase.BLOCK_BREAK_BEFORE, level, player, pos, state, blockEntity)
		MinecraftKoolRuntimeState.update(context)
		return MinecraftKoolBindingDispatcher.dispatchBlockBreakBefore(context) &&
			MinecraftKoolCommonEvents.dispatchBlockBreakBefore(context)
	}

	private fun onBlockBreakAfter(level: Level, player: Player, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?) {
		val context = blockBreakContext(MinecraftKoolInteractionPhase.BLOCK_BREAK_AFTER, level, player, pos, state, blockEntity)
		MinecraftKoolRuntimeState.update(context)
		MinecraftKoolBindingDispatcher.dispatchBlockBreakAfter(context)
		MinecraftKoolCommonEvents.dispatchBlockBreakAfter(context)
	}

	private fun onBlockBreakCanceled(level: Level, player: Player, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?) {
		val context = blockBreakContext(MinecraftKoolInteractionPhase.BLOCK_BREAK_CANCELED, level, player, pos, state, blockEntity)
		MinecraftKoolRuntimeState.update(context)
		MinecraftKoolBindingDispatcher.dispatchBlockBreakCanceled(context)
		MinecraftKoolCommonEvents.dispatchBlockBreakCanceled(context)
	}

	private fun blockBreakContext(
		phase: MinecraftKoolInteractionPhase,
		level: Level,
		player: Player,
		pos: BlockPos,
		state: BlockState,
		blockEntity: BlockEntity?
	): MinecraftKoolInteractionContext {
		return MinecraftKoolInteractionContext(
			phase = phase,
			player = player,
			level = level,
			hand = InteractionHand.MAIN_HAND,
			itemStack = ItemStack.EMPTY,
			blockPos = pos,
			blockState = state,
			blockEntity = blockEntity
		)
	}
}
