@file:Suppress("UnusedReceiverParameter")

package io.github.cbrandt77.kt.inlinestreams

import kotlin.jvm.JvmInline

@JvmInline @Suppress("UNUSED")
public value class InlineStream<out T> // TODO figure out type variance
internal constructor(private val dummy: Nothing?) // TODO should this hold the iterable itself?

private val instance: InlineStream<Any> = InlineStream(null)
@Suppress("UNCHECKED_CAST")
internal fun <T> instance(): InlineStream<T> = instance as InlineStream<T>


public fun <T> Iterable<T>.inlineStream(): InlineStream<T> {
	return instance()
}

public fun <T> Iterator<T>.inlineStream(): InlineStream<T> {
	return instance()
}

/**
 * Use this to define new stream operations.
 *
 * This stub defines the action performed within the loop for a stream operation.  Only one call to this may exist per stream operation, and it should be returned from the function that calls it.
 * The function using this will be statically replaced with a call to a newly-generated inline function that performs the action defined here.
 *
 * For state that persists between iterations of the loop, define variables outside of this block and reference them within.
 *
 * @param element The most recent element produced by previous operations in the stream.
 * @param breakLoop Call this to prematurely end the stream entirely. See [take] for an example.
 * @param downstreamAction Whatever is supposed to happen next.  You may call this once (see [map]), more than once (see [flatMap]), or not at all (see [drop]).
 */
@Suppress("UNCHECKED_CAST", "KotlinUnreachableCode", "KDocUnresolvedReference")
public inline fun <T, U> InlineStream<T>.intermediateOperation(action: (element: T, breakLoop: () -> Unit, downstreamAction: (U) -> Unit) -> Unit): InlineStream<U> {
	error("Stream operation executed without being transformed by compiler plugin.") // TODO flesh out error message
	return this as InlineStream<U>;
}

@Suppress("UNCHECKED_CAST", "KDocUnresolvedReference", "UnusedReceiverParameter", "RedundantUnitReturnType")
public inline fun <T> InlineStream<T>.terminalOperation(action: (element: T, breakLoop: () -> Unit) -> Unit): Unit {
	error("Stream operation executed without being transformed by compiler plugin.") // TODO flesh out error message
}

