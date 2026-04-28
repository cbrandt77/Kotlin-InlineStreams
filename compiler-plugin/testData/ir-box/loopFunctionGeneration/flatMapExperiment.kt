package pkg

import io.github.cbrandt77.kt.inlinestreams.*

inline fun <T, U> InlineStream<Iterable<T>>.flatMap(mapper: (T) -> U): InlineStream<U> {
	var state = 4
	
	return intermediateOperation { el, breakLoop, downstreamAction ->
		for (item in el) {
			state = 2
			downstreamAction(mapper(item))
		}
	}
}

inline fun <T> testOperation_runtime(el: T, breakLoop: () -> Unit, downstreamAction: (T) -> Unit, getState: () -> Int, setState: (Int) -> Unit) {
	setState(getState() + 1)
	downstreamAction(el)
	breakLoop()
}

fun box(): String {
	return "OK"
}