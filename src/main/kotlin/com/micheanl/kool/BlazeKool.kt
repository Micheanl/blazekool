package com.micheanl.kool

import net.fabricmc.api.ModInitializer
import net.minecraft.resources.Identifier

object BlazeKool : ModInitializer {
	const val MOD_ID: String = "blazekool"

	override fun onInitialize() {
	}

	fun id(path: String): Identifier
		= Identifier.fromNamespaceAndPath(MOD_ID, path)
}
