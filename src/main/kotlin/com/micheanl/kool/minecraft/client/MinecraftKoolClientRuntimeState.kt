package com.micheanl.kool.minecraft.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.player.Player

object MinecraftKoolClientRuntimeState {
	@Volatile
	var client: Minecraft? = null

	@Volatile
	var level: ClientLevel? = null

	@Volatile
	var player: Player? = null

	@Volatile
	var screen: Screen? = null

	@Volatile
	var screenGraphics: GuiGraphicsExtractor? = null

	@Volatile
	var lastClientContext: MinecraftKoolClientContext? = null

	@Volatile
	var lastLevelContext: MinecraftKoolClientLevelContext? = null

	@Volatile
	var lastPlayerContext: MinecraftKoolClientPlayerContext? = null

	@Volatile
	var lastRenderContext: MinecraftKoolRenderContext? = null

	@Volatile
	var lastTerrainRenderContext: MinecraftKoolTerrainRenderContext? = null

	@Volatile
	var lastBlockOutlineContext: MinecraftKoolBlockOutlineContext? = null

	@Volatile
	var lastScreenContext: MinecraftKoolScreenContext? = null

	fun update(context: MinecraftKoolClientContext) {
		client = context.client
		lastClientContext = context
	}

	fun update(context: MinecraftKoolClientLevelContext) {
		client = context.client
		level = context.level
		lastLevelContext = context
	}

	fun update(context: MinecraftKoolClientPlayerContext) {
		client = context.client
		player = context.player
		level = context.level
		lastPlayerContext = context
	}

	fun update(context: MinecraftKoolRenderContext) {
		lastRenderContext = context
	}

	fun update(context: MinecraftKoolTerrainRenderContext) {
		lastTerrainRenderContext = context
	}

	fun update(context: MinecraftKoolBlockOutlineContext) {
		lastBlockOutlineContext = context
	}

	fun update(context: MinecraftKoolScreenContext) {
		client = context.client
		screen = context.screen
		screenGraphics = context.graphics
		lastScreenContext = context
	}

	fun clearClient(client: Minecraft) {
		if (this.client === client) {
			this.client = null
		}
	}

	fun clearLevel(level: ClientLevel) {
		if (this.level === level) {
			this.level = null
		}
	}

	fun clearPlayer(player: Player) {
		if (this.player === player) {
			this.player = null
		}
	}

	fun clearScreen(screen: Screen) {
		if (this.screen === screen) {
			this.screen = null
			screenGraphics = null
		}
	}
}
