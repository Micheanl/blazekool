package com.micheanl.kool.api.minecraft

import com.micheanl.kool.minecraft.MinecraftKoolConnectionContext
import com.micheanl.kool.minecraft.MinecraftKoolDataPackSyncContext
import com.micheanl.kool.minecraft.MinecraftKoolInteractionContext
import com.micheanl.kool.minecraft.MinecraftKoolLevelContext
import com.micheanl.kool.minecraft.MinecraftKoolPlayerContext
import com.micheanl.kool.minecraft.MinecraftKoolServerContext
import net.minecraft.world.InteractionResult
import java.util.concurrent.CopyOnWriteArrayList

object MinecraftKoolCommonEvents {
	private val serverListeners = CopyOnWriteArrayList<MinecraftKoolServerListener>()
	private val connectionListeners = CopyOnWriteArrayList<MinecraftKoolConnectionListener>()
	private val interactionListeners = CopyOnWriteArrayList<MinecraftKoolInteractionListener>()

	fun registerServer(listener: MinecraftKoolServerListener) {
		serverListeners.addIfAbsent(listener)
	}

	fun unregisterServer(listener: MinecraftKoolServerListener) {
		serverListeners.remove(listener)
	}

	fun registerConnection(listener: MinecraftKoolConnectionListener) {
		connectionListeners.addIfAbsent(listener)
	}

	fun unregisterConnection(listener: MinecraftKoolConnectionListener) {
		connectionListeners.remove(listener)
	}

	fun registerInteraction(listener: MinecraftKoolInteractionListener) {
		interactionListeners.addIfAbsent(listener)
	}

	fun unregisterInteraction(listener: MinecraftKoolInteractionListener) {
		interactionListeners.remove(listener)
	}

	internal fun dispatchServerStarting(context: MinecraftKoolServerContext) {
		forEachServerListener { listener -> listener.onServerStarting(context) }
	}

	internal fun dispatchServerStarted(context: MinecraftKoolServerContext) {
		forEachServerListener { listener -> listener.onServerStarted(context) }
	}

	internal fun dispatchServerStopping(context: MinecraftKoolServerContext) {
		forEachServerListener { listener -> listener.onServerStopping(context) }
	}

	internal fun dispatchServerStopped(context: MinecraftKoolServerContext) {
		forEachServerListener { listener -> listener.onServerStopped(context) }
	}

	internal fun dispatchDataPackSync(context: MinecraftKoolDataPackSyncContext) {
		forEachServerListener { listener -> listener.onDataPackSync(context) }
	}

	internal fun dispatchDataPackReloadStart(context: MinecraftKoolServerContext) {
		forEachServerListener { listener -> listener.onDataPackReloadStart(context) }
	}

	internal fun dispatchDataPackReloadEnd(context: MinecraftKoolServerContext) {
		forEachServerListener { listener -> listener.onDataPackReloadEnd(context) }
	}

	internal fun dispatchBeforeSave(context: MinecraftKoolServerContext) {
		forEachServerListener { listener -> listener.onBeforeSave(context) }
	}

	internal fun dispatchAfterSave(context: MinecraftKoolServerContext) {
		forEachServerListener { listener -> listener.onAfterSave(context) }
	}

	internal fun dispatchServerTickStart(context: MinecraftKoolServerContext) {
		forEachServerListener { listener -> listener.onServerTickStart(context) }
	}

	internal fun dispatchServerTickEnd(context: MinecraftKoolServerContext) {
		forEachServerListener { listener -> listener.onServerTickEnd(context) }
	}

	internal fun dispatchLevelTickStart(context: MinecraftKoolLevelContext) {
		forEachServerListener { listener -> listener.onLevelTickStart(context) }
	}

	internal fun dispatchLevelTickEnd(context: MinecraftKoolLevelContext) {
		forEachServerListener { listener -> listener.onLevelTickEnd(context) }
	}

	internal fun dispatchPlayerTick(context: MinecraftKoolPlayerContext) {
		forEachServerListener { listener -> listener.onPlayerTick(context) }
	}

	internal fun dispatchPlayInit(context: MinecraftKoolConnectionContext) {
		forEachConnectionListener { listener -> listener.onPlayInit(context) }
	}

	internal fun dispatchPlayJoin(context: MinecraftKoolConnectionContext) {
		forEachConnectionListener { listener -> listener.onPlayJoin(context) }
	}

	internal fun dispatchPlayDisconnect(context: MinecraftKoolConnectionContext) {
		forEachConnectionListener { listener -> listener.onPlayDisconnect(context) }
	}

	internal fun dispatchUseBlock(context: MinecraftKoolInteractionContext): InteractionResult {
		return dispatchInteractionResult { listener -> listener.onUseBlock(context) }
	}

	internal fun dispatchUseItem(context: MinecraftKoolInteractionContext): InteractionResult {
		return dispatchInteractionResult { listener -> listener.onUseItem(context) }
	}

	internal fun dispatchAttackBlock(context: MinecraftKoolInteractionContext): InteractionResult {
		return dispatchInteractionResult { listener -> listener.onAttackBlock(context) }
	}

	internal fun dispatchBlockBreakBefore(context: MinecraftKoolInteractionContext): Boolean {
		var index = 0
		while (index < interactionListeners.size) {
			if (!interactionListeners[index].onBlockBreakBefore(context)) {
				return false
			}
			index++
		}
		return true
	}

	internal fun dispatchBlockBreakAfter(context: MinecraftKoolInteractionContext) {
		forEachInteractionListener { listener -> listener.onBlockBreakAfter(context) }
	}

	internal fun dispatchBlockBreakCanceled(context: MinecraftKoolInteractionContext) {
		forEachInteractionListener { listener -> listener.onBlockBreakCanceled(context) }
	}

	private fun dispatchInteractionResult(
		handler: (MinecraftKoolInteractionListener) -> InteractionResult
	): InteractionResult {
		var index = 0
		while (index < interactionListeners.size) {
			val result = handler(interactionListeners[index])
			if (result != InteractionResult.PASS) {
				return result
			}
			index++
		}
		return InteractionResult.PASS
	}

	private fun forEachServerListener(handler: (MinecraftKoolServerListener) -> Unit) {
		var index = 0
		while (index < serverListeners.size) {
			handler(serverListeners[index])
			index++
		}
	}

	private fun forEachConnectionListener(handler: (MinecraftKoolConnectionListener) -> Unit) {
		var index = 0
		while (index < connectionListeners.size) {
			handler(connectionListeners[index])
			index++
		}
	}

	private fun forEachInteractionListener(handler: (MinecraftKoolInteractionListener) -> Unit) {
		var index = 0
		while (index < interactionListeners.size) {
			handler(interactionListeners[index])
			index++
		}
	}
}

interface MinecraftKoolServerListener {
	fun onServerStarting(context: MinecraftKoolServerContext) {
	}

	fun onServerStarted(context: MinecraftKoolServerContext) {
	}

	fun onServerStopping(context: MinecraftKoolServerContext) {
	}

	fun onServerStopped(context: MinecraftKoolServerContext) {
	}

	fun onDataPackSync(context: MinecraftKoolDataPackSyncContext) {
	}

	fun onDataPackReloadStart(context: MinecraftKoolServerContext) {
	}

	fun onDataPackReloadEnd(context: MinecraftKoolServerContext) {
	}

	fun onBeforeSave(context: MinecraftKoolServerContext) {
	}

	fun onAfterSave(context: MinecraftKoolServerContext) {
	}

	fun onServerTickStart(context: MinecraftKoolServerContext) {
	}

	fun onServerTickEnd(context: MinecraftKoolServerContext) {
	}

	fun onLevelTickStart(context: MinecraftKoolLevelContext) {
	}

	fun onLevelTickEnd(context: MinecraftKoolLevelContext) {
	}

	fun onPlayerTick(context: MinecraftKoolPlayerContext) {
	}
}

interface MinecraftKoolConnectionListener {
	fun onPlayInit(context: MinecraftKoolConnectionContext) {
	}

	fun onPlayJoin(context: MinecraftKoolConnectionContext) {
	}

	fun onPlayDisconnect(context: MinecraftKoolConnectionContext) {
	}
}

interface MinecraftKoolInteractionListener {
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
}
