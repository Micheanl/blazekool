package com.micheanl.kool.api

import de.fabmax.kool.scene.Scene

interface KoolSceneRegistry {
	fun registerScene(scene: Scene)

	fun unregisterScene(scene: Scene)
}
