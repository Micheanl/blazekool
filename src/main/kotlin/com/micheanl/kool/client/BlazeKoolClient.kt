package com.micheanl.kool.client

import com.micheanl.kool.client.demo.BlazeKoolDemoRuntime
import com.micheanl.kool.minecraft.client.BlazeKoolMinecraftClientIntegration
import net.fabricmc.api.ClientModInitializer

object BlazeKoolClient : ClientModInitializer {
	override fun onInitializeClient() {
		BlazeKoolMinecraftClientIntegration.register()
		BlazeKoolDemoRuntime.register()
	}
}
