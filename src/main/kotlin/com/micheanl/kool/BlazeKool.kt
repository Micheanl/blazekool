package com.micheanl.kool

import com.micheanl.kool.minecraft.BlazeKoolMinecraftCommonIntegration
import net.fabricmc.api.ModInitializer
import net.minecraft.resources.Identifier

object BlazeKool : ModInitializer {
	const val MOD_ID: String = "blazekool"

	override fun onInitialize() {
		BlazeKoolMinecraftCommonIntegration.register()
	}

	fun id(path: String): Identifier
		= Identifier.fromNamespaceAndPath(MOD_ID, path)
}
