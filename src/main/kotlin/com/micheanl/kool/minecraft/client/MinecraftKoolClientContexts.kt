package com.micheanl.kool.minecraft.client

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelTerrainRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState
import net.minecraft.world.entity.player.Player

data class MinecraftKoolClientContext(
	val client: Minecraft
)

data class MinecraftKoolClientLevelContext(
	val client: Minecraft,
	val level: ClientLevel
)

data class MinecraftKoolClientPlayerContext(
	val client: Minecraft,
	val player: Player,
	val level: ClientLevel
)

data class MinecraftKoolRenderContext(
	val levelRenderContext: LevelRenderContext
)

data class MinecraftKoolTerrainRenderContext(
	val levelTerrainRenderContext: LevelTerrainRenderContext
)

data class MinecraftKoolBlockOutlineContext(
	val levelRenderContext: LevelRenderContext,
	val outlineRenderState: BlockOutlineRenderState
)

data class MinecraftKoolScreenContext(
	val client: Minecraft,
	val screen: Screen,
	val graphics: GuiGraphicsExtractor? = null,
	val scaledWidth: Int = 0,
	val scaledHeight: Int = 0,
	val mouseX: Int = 0,
	val mouseY: Int = 0,
	val tickProgress: Float = 0.0f
)
