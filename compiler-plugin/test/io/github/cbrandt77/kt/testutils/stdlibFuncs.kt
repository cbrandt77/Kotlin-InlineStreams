package io.github.cbrandt77.kt.testutils

const val STDLIB_FUNCTIONS = """
// MODULE: stdlib
// FILE: streamOperations.kt
package io.github.cbrandt77.kt.inlinestreams

import io.github.cbrandt77.kt.inlinestreams.*

inline fun <T, U> InlineStream<T>.map(mapper: (T) -> U): InlineStream<U> {
	return intermediateOperation { el, _, downstreamAction ->
		downstreamAction(mapper(el))
	}
}

inline fun <T, U> InlineStream<T>.flatMap(mapper: (T) -> Iterable<U>): InlineStream<U> {
	return intermediateOperation { el, _, downstreamAction ->
		for (it in mapper(el)) {
			downstreamAction(it)
		}
	}
}

fun <T> InlineStream<T>.firstOrNull(): T? {
	var out: T? = null
	var hasBeenSet = false
	
	terminalOperation { el, breakLoop ->
		out = el
		hasBeenSet = true
		breakLoop()
	}
	
	if (!hasBeenSet)
		return null;
	return out;
}
"""