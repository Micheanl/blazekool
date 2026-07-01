package com.micheanl.kool.minecraft

import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level

object MinecraftKoolRuntimeState {
	@Volatile
	var server: MinecraftServer? = null

	@Volatile
	var player: Player? = null

	@Volatile
	var level: Level? = null

	@Volatile
	var lastServerContext: MinecraftKoolServerContext? = null

	@Volatile
	var lastLevelContext: MinecraftKoolLevelContext? = null

	@Volatile
	var lastPlayerContext: MinecraftKoolPlayerContext? = null

	@Volatile
	var lastDataPackSyncContext: MinecraftKoolDataPackSyncContext? = null

	@Volatile
	var lastConnectionContext: MinecraftKoolConnectionContext? = null

	@Volatile
	var lastInteractionContext: MinecraftKoolInteractionContext? = null

	fun update(context: MinecraftKoolServerContext) {
		server = context.server
		lastServerContext = context
	}

	fun update(context: MinecraftKoolLevelContext) {
		level = context.level
		if (context.server != null) {
			server = context.server
		}
		lastLevelContext = context
	}

	fun update(context: MinecraftKoolPlayerContext) {
		player = context.player
		level = context.level
		if (context.server != null) {
			server = context.server
		}
		lastPlayerContext = context
	}

	fun update(context: MinecraftKoolDataPackSyncContext) {
		player = context.player
		level = context.player.level()
		lastDataPackSyncContext = context
	}

	fun update(context: MinecraftKoolConnectionContext) {
		server = context.server
		player = context.player
		lastConnectionContext = context
	}

	fun update(context: MinecraftKoolInteractionContext) {
		player = context.player
		level = context.level
		lastInteractionContext = context
	}

	fun clearServer(server: MinecraftServer) {
		if (this.server === server) {
			this.server = null
		}
	}

	fun clearPlayer(player: Player) {
		if (this.player === player) {
			this.player = null
		}
	}

	fun clearLevel(level: Level) {
		if (this.level === level) {
			this.level = null
		}
	}
}
