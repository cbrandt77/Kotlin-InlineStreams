package io.github.cbrandt77.kt.inlinestreams.util

/**
 * If this is a success, runs transform on it and returns its result, else returns the existing failure.
 */
fun <T, R> Result<T>.flatMap(transform: (T) -> Result<R>): Result<R> {
	if (!this.isFailure)
		return transform(this.getOrNull()!!)
	
	@Suppress("UNCHECKED_CAST")
	return this as Result<R>
}