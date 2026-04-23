package io.github.cbrandt77.kt.inlinestreams


public fun <T> List<T>.compactIfPossible(): List<T> {
	return when (size) {
		0 -> emptyList()
		1 -> listOf(this[0])
		else -> this
	}
}

public fun <T> ArrayList<T>.compactIfPossible(): List<T> {
	return when (size) {
		0 -> emptyList()
		1 -> listOf(this[0])
		else -> this.apply { trimToSize() }
	}
}