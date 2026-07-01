package com.micheanl.kool.client.demo

import com.micheanl.kool.client.gui.BlazeKoolDemoScreen
import com.micheanl.kool.engine.BlazeKoolEngine
import com.mojang.blaze3d.platform.InputConstants
import de.fabmax.kool.demo.DemoLoader
import de.fabmax.kool.demo.Demos
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import java.util.concurrent.atomic.AtomicBoolean

object BlazeKoolDemoRuntime {
	private val registered = AtomicBoolean(false)
	private val loaderLock = Any()
	private lateinit var openKey: KeyMapping

	@Volatile
	private var loader: DemoLoader? = null

	fun register() {
		if (!registered.compareAndSet(false, true)) {
			return
		}
		openKey = KeyMappingHelper.registerKeyMapping(
			KeyMapping("key.blazekool.demo.open", InputConstants.Type.KEYSYM, InputConstants.KEY_O, KeyMapping.Category.MISC)
		)
		ClientTickEvents.END_CLIENT_TICK.register(::tick)
		ClientLifecycleEvents.CLIENT_STOPPING.register { clear() }
	}

	fun tick(client: Minecraft) {
		if (!registered.get() || client.gui.screen() != null) {
			return
		}
		while (openKey.consumeClick()) {
			openScreen(null, Demos.defaultDemo, client)
		}
	}

	fun openScreen(parent: Screen?, startDemo: String? = Demos.defaultDemo, client: Minecraft = Minecraft.getInstance()) {
		client.gui.setScreen(BlazeKoolDemoScreen(parent, startDemo))
	}

	fun ensureDemoLoaded(client: Minecraft, startDemo: String? = Demos.defaultDemo): DemoLoader {
		BlazeKoolEngine.start(client)
		val context = BlazeKoolEngine.currentContext ?: error("BlazeKool Minecraft Kool context is not available")
		val normalizedStartDemo = startDemo?.lowercase()?.removeSuffix("demo")
		return synchronized(loaderLock) {
			val existing = loader
			if (existing == null) {
				DemoLoader(context, normalizedStartDemo).also { created ->
					created.menu.isExpanded = true
					loader = created
				}
			} else {
				val demoEntry = normalizedStartDemo?.let(Demos.demos::get)
				if (demoEntry != null) {
					existing.loadDemo(demoEntry)
				}
				existing.menu.isExpanded = true
				existing
			}
		}
	}

	fun extractGui(graphics: GuiGraphicsExtractor) {
		BlazeKoolEngine.extractGui(graphics)
	}

	fun clear() {
		synchronized(loaderLock) {
			loader = null
		}
	}
}
