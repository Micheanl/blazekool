package com.micheanl.kool.minecraft

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.chunk.LevelChunk

data class MinecraftKoolChunkContext(
	val level: ServerLevel,
	val chunk: LevelChunk,
	val generated: Boolean = false
)

data class MinecraftKoolBlockEntityContext(
	val blockEntity: BlockEntity,
	val level: ServerLevel
)

data class MinecraftKoolEntityContext(
	val entity: Entity,
	val level: ServerLevel,
	val spawnReason: EntitySpawnReason? = null,
	val loadedFromDisk: Boolean? = null
)

data class MinecraftKoolEntityLevelChangeContext(
	val originalEntity: Entity,
	val newEntity: Entity,
	val origin: ServerLevel,
	val destination: ServerLevel
)

data class MinecraftKoolPlayerLevelChangeContext(
	val player: ServerPlayer,
	val origin: ServerLevel,
	val destination: ServerLevel
)

data class MinecraftKoolPlayerCopyContext(
	val oldPlayer: ServerPlayer,
	val newPlayer: ServerPlayer,
	val alive: Boolean
)

data class MinecraftKoolLivingDamageContext(
	val entity: LivingEntity,
	val level: ServerLevel,
	val source: DamageSource,
	val phase: MinecraftKoolDamagePhase,
	val amount: Float,
	val baseDamageTaken: Float = amount,
	val damageTaken: Float = amount,
	val blocked: Boolean = false
)

data class MinecraftKoolEntityKillContext(
	val level: ServerLevel,
	val attacker: Entity,
	val killedEntity: LivingEntity,
	val source: DamageSource
)

data class MinecraftKoolEquipmentContext(
	val entity: LivingEntity,
	val level: ServerLevel,
	val slot: EquipmentSlot,
	val previousStack: ItemStack,
	val currentStack: ItemStack
)

data class MinecraftKoolPlayerInventoryItemContext(
	val player: ServerPlayer,
	val level: ServerLevel,
	val slot: Int,
	val itemStack: ItemStack,
	val selected: Boolean
)

enum class MinecraftKoolDamagePhase {
	ALLOW_DAMAGE,
	AFTER_DAMAGE,
	ALLOW_DEATH,
	AFTER_DEATH
}
