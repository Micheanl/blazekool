package com.micheanl.kool.minecraft

import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraft.server.packs.resources.CloseableResourceManager
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult

data class MinecraftKoolServerContext(
	val server: MinecraftServer,
	val resourceManager: CloseableResourceManager? = null,
	val flush: Boolean? = null,
	val force: Boolean? = null,
	val success: Boolean? = null
)

data class MinecraftKoolLevelContext(
	val level: Level,
	val server: MinecraftServer? = null
)

data class MinecraftKoolPlayerContext(
	val player: Player,
	val level: Level,
	val server: MinecraftServer? = null
)

data class MinecraftKoolDataPackSyncContext(
	val player: ServerPlayer,
	val joined: Boolean
)

data class MinecraftKoolConnectionContext(
	val listener: ServerGamePacketListenerImpl,
	val server: MinecraftServer,
	val sender: PacketSender? = null,
	val player: ServerPlayer? = null
)

data class MinecraftKoolInteractionContext(
	val phase: MinecraftKoolInteractionPhase,
	val player: Player,
	val level: Level,
	val hand: InteractionHand,
	val itemStack: ItemStack,
	val hitResult: BlockHitResult? = null,
	val blockPos: BlockPos? = null,
	val direction: Direction? = null,
	val blockState: BlockState? = null,
	val blockEntity: BlockEntity? = null
)

enum class MinecraftKoolInteractionPhase {
	USE_BLOCK,
	USE_ITEM,
	ATTACK_BLOCK,
	BLOCK_BREAK_BEFORE,
	BLOCK_BREAK_AFTER,
	BLOCK_BREAK_CANCELED
}
