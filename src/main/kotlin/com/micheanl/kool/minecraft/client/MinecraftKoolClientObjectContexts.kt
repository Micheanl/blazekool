package com.micheanl.kool.minecraft.client

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.chunk.LevelChunk

data class MinecraftKoolClientEntityContext(
	val entity: Entity,
	val level: ClientLevel
)

data class MinecraftKoolClientBlockEntityContext(
	val blockEntity: BlockEntity,
	val level: ClientLevel
)

data class MinecraftKoolClientChunkContext(
	val level: ClientLevel,
	val chunk: LevelChunk
)
