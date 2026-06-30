package com.micheanl.kool.client

import com.micheanl.kool.engine.BlazeKoolEngine
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents

object BlazeKoolClient : ClientModInitializer {
	override fun onInitializeClient() {
		ClientLifecycleEvents.CLIENT_STARTED.register(BlazeKoolEngine::start)
		ClientLifecycleEvents.CLIENT_STOPPING.register(BlazeKoolEngine::stop)
		LevelRenderEvents.COLLECT_SUBMITS.register(BlazeKoolEngine::collectSubmits)
	}
}
