package com.micheanl.kool.api.minecraft.client

import com.micheanl.kool.minecraft.client.MinecraftKoolBlockOutlineContext
import com.micheanl.kool.minecraft.client.MinecraftKoolClientContext
import com.micheanl.kool.minecraft.client.MinecraftKoolClientLevelContext
import com.micheanl.kool.minecraft.client.MinecraftKoolClientPlayerContext
import com.micheanl.kool.minecraft.client.MinecraftKoolRenderContext
import com.micheanl.kool.minecraft.client.MinecraftKoolScreenContext
import com.micheanl.kool.minecraft.client.MinecraftKoolTerrainRenderContext
import java.util.concurrent.CopyOnWriteArrayList

object MinecraftKoolClientEvents {
	private val lifecycleListeners = CopyOnWriteArrayList<MinecraftKoolClientLifecycleListener>()
	private val renderListeners = CopyOnWriteArrayList<MinecraftKoolRenderListener>()
	private val screenListeners = CopyOnWriteArrayList<MinecraftKoolScreenListener>()

	fun registerLifecycle(listener: MinecraftKoolClientLifecycleListener) {
		lifecycleListeners.addIfAbsent(listener)
	}

	fun unregisterLifecycle(listener: MinecraftKoolClientLifecycleListener) {
		lifecycleListeners.remove(listener)
	}

	fun registerRender(listener: MinecraftKoolRenderListener) {
		renderListeners.addIfAbsent(listener)
	}

	fun unregisterRender(listener: MinecraftKoolRenderListener) {
		renderListeners.remove(listener)
	}

	fun registerScreen(listener: MinecraftKoolScreenListener) {
		screenListeners.addIfAbsent(listener)
	}

	fun unregisterScreen(listener: MinecraftKoolScreenListener) {
		screenListeners.remove(listener)
	}

	internal fun dispatchClientStarted(context: MinecraftKoolClientContext) {
		forEachLifecycleListener { listener -> listener.onClientStarted(context) }
	}

	internal fun dispatchClientStopping(context: MinecraftKoolClientContext) {
		forEachLifecycleListener { listener -> listener.onClientStopping(context) }
	}

	internal fun dispatchClientTickStart(context: MinecraftKoolClientContext) {
		forEachLifecycleListener { listener -> listener.onClientTickStart(context) }
	}

	internal fun dispatchClientTickEnd(context: MinecraftKoolClientContext) {
		forEachLifecycleListener { listener -> listener.onClientTickEnd(context) }
	}

	internal fun dispatchClientLevelTickStart(context: MinecraftKoolClientLevelContext) {
		forEachLifecycleListener { listener -> listener.onLevelTickStart(context) }
	}

	internal fun dispatchClientLevelTickEnd(context: MinecraftKoolClientLevelContext) {
		forEachLifecycleListener { listener -> listener.onLevelTickEnd(context) }
	}

	internal fun dispatchClientPlayerTick(context: MinecraftKoolClientPlayerContext) {
		forEachLifecycleListener { listener -> listener.onPlayerTick(context) }
	}

	internal fun dispatchStartMain(context: MinecraftKoolTerrainRenderContext) {
		forEachRenderListener { listener -> listener.onStartMain(context) }
	}

	internal fun dispatchAfterOpaqueTerrain(context: MinecraftKoolTerrainRenderContext) {
		forEachRenderListener { listener -> listener.onAfterOpaqueTerrain(context) }
	}

	internal fun dispatchCollectSubmits(context: MinecraftKoolRenderContext) {
		forEachRenderListener { listener -> listener.onCollectSubmits(context) }
	}

	internal fun dispatchAfterSolidFeatures(context: MinecraftKoolRenderContext) {
		forEachRenderListener { listener -> listener.onAfterSolidFeatures(context) }
	}

	internal fun dispatchAfterTranslucentFeatures(context: MinecraftKoolRenderContext) {
		forEachRenderListener { listener -> listener.onAfterTranslucentFeatures(context) }
	}

	internal fun dispatchBeforeBlockOutline(context: MinecraftKoolBlockOutlineContext): Boolean {
		var shouldRender = true
		var index = 0
		while (index < renderListeners.size) {
			if (!renderListeners[index].onBeforeBlockOutline(context)) {
				shouldRender = false
			}
			index++
		}
		return shouldRender
	}

	internal fun dispatchBeforeGizmos(context: MinecraftKoolRenderContext) {
		forEachRenderListener { listener -> listener.onBeforeGizmos(context) }
	}

	internal fun dispatchBeforeTranslucentTerrain(context: MinecraftKoolRenderContext) {
		forEachRenderListener { listener -> listener.onBeforeTranslucentTerrain(context) }
	}

	internal fun dispatchAfterTranslucentTerrain(context: MinecraftKoolRenderContext) {
		forEachRenderListener { listener -> listener.onAfterTranslucentTerrain(context) }
	}

	internal fun dispatchEndMain(context: MinecraftKoolRenderContext) {
		forEachRenderListener { listener -> listener.onEndMain(context) }
	}

	internal fun dispatchScreenBeforeInit(context: MinecraftKoolScreenContext) {
		forEachScreenListener { listener -> listener.onBeforeInit(context) }
	}

	internal fun dispatchScreenAfterInit(context: MinecraftKoolScreenContext) {
		forEachScreenListener { listener -> listener.onAfterInit(context) }
	}

	internal fun dispatchScreenRemoved(context: MinecraftKoolScreenContext) {
		forEachScreenListener { listener -> listener.onRemoved(context) }
	}

	internal fun dispatchScreenBeforeExtract(context: MinecraftKoolScreenContext) {
		forEachScreenListener { listener -> listener.onBeforeExtract(context) }
	}

	internal fun dispatchScreenAfterBackground(context: MinecraftKoolScreenContext) {
		forEachScreenListener { listener -> listener.onAfterBackground(context) }
	}

	internal fun dispatchScreenAfterExtract(context: MinecraftKoolScreenContext) {
		forEachScreenListener { listener -> listener.onAfterExtract(context) }
	}

	internal fun dispatchScreenBeforeTick(context: MinecraftKoolScreenContext) {
		forEachScreenListener { listener -> listener.onBeforeTick(context) }
	}

	internal fun dispatchScreenAfterTick(context: MinecraftKoolScreenContext) {
		forEachScreenListener { listener -> listener.onAfterTick(context) }
	}

	private fun forEachLifecycleListener(handler: (MinecraftKoolClientLifecycleListener) -> Unit) {
		var index = 0
		while (index < lifecycleListeners.size) {
			handler(lifecycleListeners[index])
			index++
		}
	}

	private fun forEachRenderListener(handler: (MinecraftKoolRenderListener) -> Unit) {
		var index = 0
		while (index < renderListeners.size) {
			handler(renderListeners[index])
			index++
		}
	}

	private fun forEachScreenListener(handler: (MinecraftKoolScreenListener) -> Unit) {
		var index = 0
		while (index < screenListeners.size) {
			handler(screenListeners[index])
			index++
		}
	}
}

interface MinecraftKoolClientLifecycleListener {
	fun onClientStarted(context: MinecraftKoolClientContext) {
	}

	fun onClientStopping(context: MinecraftKoolClientContext) {
	}

	fun onClientTickStart(context: MinecraftKoolClientContext) {
	}

	fun onClientTickEnd(context: MinecraftKoolClientContext) {
	}

	fun onLevelTickStart(context: MinecraftKoolClientLevelContext) {
	}

	fun onLevelTickEnd(context: MinecraftKoolClientLevelContext) {
	}

	fun onPlayerTick(context: MinecraftKoolClientPlayerContext) {
	}
}

interface MinecraftKoolRenderListener {
	fun onStartMain(context: MinecraftKoolTerrainRenderContext) {
	}

	fun onAfterOpaqueTerrain(context: MinecraftKoolTerrainRenderContext) {
	}

	fun onCollectSubmits(context: MinecraftKoolRenderContext) {
	}

	fun onAfterSolidFeatures(context: MinecraftKoolRenderContext) {
	}

	fun onAfterTranslucentFeatures(context: MinecraftKoolRenderContext) {
	}

	fun onBeforeBlockOutline(context: MinecraftKoolBlockOutlineContext): Boolean {
		return true
	}

	fun onBeforeGizmos(context: MinecraftKoolRenderContext) {
	}

	fun onBeforeTranslucentTerrain(context: MinecraftKoolRenderContext) {
	}

	fun onAfterTranslucentTerrain(context: MinecraftKoolRenderContext) {
	}

	fun onEndMain(context: MinecraftKoolRenderContext) {
	}
}

interface MinecraftKoolScreenListener {
	fun onBeforeInit(context: MinecraftKoolScreenContext) {
	}

	fun onAfterInit(context: MinecraftKoolScreenContext) {
	}

	fun onRemoved(context: MinecraftKoolScreenContext) {
	}

	fun onBeforeExtract(context: MinecraftKoolScreenContext) {
	}

	fun onAfterBackground(context: MinecraftKoolScreenContext) {
	}

	fun onAfterExtract(context: MinecraftKoolScreenContext) {
	}

	fun onBeforeTick(context: MinecraftKoolScreenContext) {
	}

	fun onAfterTick(context: MinecraftKoolScreenContext) {
	}
}
