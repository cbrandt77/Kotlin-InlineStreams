package io.github.cbrandt77.kt.inlinestreams.util

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * @return true if both `is T`, null if one is and the other isn't, and false if neither are
 */
@OptIn(ExperimentalContracts::class)
inline fun <reified T> bothInstanceOf(x: Any, y: Any): Boolean? {
	contract {
		returns(true) implies (x is T && y is T)
	}
	
	val xIs = x is T
	
	if (xIs == y is T) {
		return xIs;
	}
	
	return null;
}

/**
 * @return null if the list sizes are not equal, true if the corresponding items of each list are all true against the predicate, false if at least one pair of items failed the predicate.
 */
inline fun <T> List<T>.zipAllMatch(otherList: List<T>, predicate: (T, T) -> Boolean): Boolean? {
	if (this.size != otherList.size)
		return null;
	if (this.isEmpty()) {
		return true;
	}
	
	for (i in indices) {
		val fromThis = this[i]
		val fromOther = otherList[i]
		if (!predicate(fromThis, fromOther))
			return false;
	}
	
	return true;
}

/**
 * I thought List#hashCode was stable between runs, but apparently not in every implementation.
 *
 * This makes a hashcode that's sensitive to the order of the list with a function I know is independent of each implementation.
 */
fun <T> List<T>.stableHashCode(): Int {
	if (this.isEmpty())
		return 0; // TODO is this ok?
	
	var hash = 0
	for (el in this) {
		hash = 31 * hash + el.hashCode()
	}
	return hash;
}