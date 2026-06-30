package com.micheanl.kool.api.compute

import de.fabmax.kool.util.LongHash
import java.util.concurrent.ConcurrentHashMap

object BlazeKoolComputeRegistry {
	private val shaderExecutors = ConcurrentHashMap<String, BlazeKoolComputeExecutor>()
	private val pipelineExecutors = ConcurrentHashMap<LongHash, BlazeKoolComputeExecutor>()

	fun registerShaderExecutor(shaderName: String, executor: BlazeKoolComputeExecutor) {
		shaderExecutors[shaderName] = executor
	}

	fun unregisterShaderExecutor(shaderName: String) {
		shaderExecutors.remove(shaderName)
	}

	fun registerPipelineExecutor(pipelineHash: LongHash, executor: BlazeKoolComputeExecutor) {
		pipelineExecutors[pipelineHash] = executor
	}

	fun unregisterPipelineExecutor(pipelineHash: LongHash) {
		pipelineExecutors.remove(pipelineHash)
	}

	fun executorFor(context: BlazeKoolComputeContext): BlazeKoolComputeExecutor? {
		return pipelineExecutors[context.pipeline.pipelineHash] ?: shaderExecutors[context.task.shader.name]
	}
}
