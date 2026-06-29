package com.micheanl.kool

import net.fabricmc.api.ModInitializer
import net.minecraft.resources.Identifier

object BlazeKool : ModInitializer {
	const val MOD_ID: String = "blazekool"
/*
今天是2026年6月29日我开启了这个项目
我没什么好说的 对自己的一次尝试吧
就像生活一样 谁也不知道明天会发什么
讲真的我生活挺糟糕的
但我心中有一座城堡 我不会背叛我的梦想
我给你分享一个故事吧
关于我和她

 */
	override fun onInitialize() {
	}

	fun id(path: String): Identifier
		= Identifier.fromNamespaceAndPath(MOD_ID, path)
}
