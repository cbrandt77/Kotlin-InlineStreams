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