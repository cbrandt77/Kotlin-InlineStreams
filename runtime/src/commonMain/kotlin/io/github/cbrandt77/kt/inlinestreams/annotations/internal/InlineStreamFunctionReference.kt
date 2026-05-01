package io.github.cbrandt77.kt.inlinestreams.annotations.internal

/**
 * Unique identifier to match the user-defined "InlineStream.doSomething()" functions
 * to the generated functions `doSomething(el, breakLoop, downstreamAction)` that are actually executed inside the loop.
 *
 * **DO NOT USE THIS!**
 */
@Retention(AnnotationRetention.BINARY)
public annotation class InlineStreamFunctionReference(
	val id: Int
)
