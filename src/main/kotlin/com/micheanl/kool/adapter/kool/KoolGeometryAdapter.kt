package com.micheanl.kool.adapter.kool

import com.micheanl.kool.api.geometry.BlazeKoolTriangleGeometry
import com.micheanl.kool.api.geometry.BlazeKoolVertex
import com.micheanl.kool.api.geometry.blazeKoolColor
import de.fabmax.kool.scene.geometry.IndexedVertexList
import de.fabmax.kool.scene.geometry.PrimitiveType

object KoolGeometryAdapter {
	fun fromTriangles(geometry: IndexedVertexList<*>): BlazeKoolTriangleGeometry {
		require(geometry.primitiveType == PrimitiveType.TRIANGLES) { "Only triangle geometry can be submitted through this adapter" }
		val vertices = ArrayList<BlazeKoolVertex>(geometry.numIndices)
		var index = 0
		while (index < geometry.numIndices) {
			val vertexIndex = geometry.indices[index]
			geometry.vertexIt.index = vertexIndex
			val position = geometry.vertexIt.position
			val normal = geometry.vertexIt.normal
			val color = geometry.vertexIt.color
			vertices += BlazeKoolVertex(
				x = position.x,
				y = position.y,
				z = position.z,
				color = blazeKoolColor(color.r, color.g, color.b, color.a),
				nx = normal.x,
				ny = normal.y,
				nz = normal.z
			)
			index++
		}
		return BlazeKoolTriangleGeometry(vertices)
	}
}
