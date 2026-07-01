package com.micheanl.kool.api.demo

object BlazeKoolDemoCatalog {
	val demos: List<BlazeKoolDemoDescriptor> = listOf(
		BlazeKoolDemoDescriptor("phys-vehicle", "Vehicle Physics", "Physics", 0xFF8A2BE2.toInt()),
		BlazeKoolDemoDescriptor("phys-terrain", "Island Terrain", "Physics", 0xFF5C6CFF.toInt()),
		BlazeKoolDemoDescriptor("phys-ragdoll", "Ragdoll Physics", "Physics", 0xFFB44CFF.toInt()),
		BlazeKoolDemoDescriptor("phys-joints", "Joint Chain", "Physics", 0xFF7C4DFF.toInt()),
		BlazeKoolDemoDescriptor("physics", "Collision Shapes", "Physics", 0xFFBA68C8.toInt()),
		BlazeKoolDemoDescriptor("mixer2d", "2D Physics", "Physics", 0xFF9575CD.toInt()),
		BlazeKoolDemoDescriptor("bloom", "Bloom", "Rendering", 0xFFFF4FD8.toInt()),
		BlazeKoolDemoDescriptor("pathtracing", "Path Tracing", "Compute", 0xFF7E57C2.toInt()),
		BlazeKoolDemoDescriptor("ui", "Embedded UI", "UI", 0xFFAB47BC.toInt()),
		BlazeKoolDemoDescriptor("bees", "Particles", "Simulation", 0xFFFFC107.toInt()),
		BlazeKoolDemoDescriptor("shell", "Shell Fur", "Rendering", 0xFFCE93D8.toInt()),
		BlazeKoolDemoDescriptor("creative-coding", "Creative Coding", "Geometry", 0xFFEC407A.toInt()),
		BlazeKoolDemoDescriptor("procedural", "Procedural Geometry", "Geometry", 0xFF8E24AA.toInt()),
		BlazeKoolDemoDescriptor("gltf", "glTF Models", "Assets", 0xFF42A5F5.toInt()),
		BlazeKoolDemoDescriptor("deferred", "Deferred Lighting", "Rendering", 0xFF5E35B1.toInt()),
		BlazeKoolDemoDescriptor("ao", "Ambient Occlusion", "Rendering", 0xFF78909C.toInt()),
		BlazeKoolDemoDescriptor("ssr", "Screen Reflections", "Rendering", 0xFF26C6DA.toInt()),
		BlazeKoolDemoDescriptor("pbr", "PBR Materials", "Rendering", 0xFFFF8A65.toInt()),
		BlazeKoolDemoDescriptor("instance", "Instancing LOD", "Rendering", 0xFF66BB6A.toInt()),
		BlazeKoolDemoDescriptor("simplification", "Mesh Simplification", "Geometry", 0xFFFF7043.toInt())
	)

	val first: BlazeKoolDemoDescriptor
		get() = demos[0]

	fun byIndex(index: Int): BlazeKoolDemoDescriptor {
		val size = demos.size
		val normalized = ((index % size) + size) % size
		return demos[normalized]
	}

	fun indexOf(demo: BlazeKoolDemoDescriptor): Int {
		val index = demos.indexOfFirst { candidate -> candidate.slug == demo.slug }
		return index.coerceAtLeast(0)
	}

	fun next(demo: BlazeKoolDemoDescriptor): BlazeKoolDemoDescriptor {
		return byIndex(indexOf(demo) + 1)
	}

	fun previous(demo: BlazeKoolDemoDescriptor): BlazeKoolDemoDescriptor {
		return byIndex(indexOf(demo) - 1)
	}
}

data class BlazeKoolDemoDescriptor(
	val slug: String,
	val displayName: String,
	val category: String,
	val accentColor: Int
) {
	val liveUrl: String
		get() = "https://kool-engine.github.io/live/demos/?demo=$slug"
}
