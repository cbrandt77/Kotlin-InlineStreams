import io.github.cbrandt77.kt.inlinestreams.*

fun <T> InlineStream<T>.testOperation(): InlineStream<T> {
	var state = 4
	
	return intermediateOperation { el, breakLoop, downstreamAction ->
		state++
		downstreamAction(el)
		breakLoop()
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