package io.github.cbrandt77.kt.overloadablesetters.util

import io.github.cbrandt77.kt.overloadablesetters.OverloadedSetterOptions
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.name.Name

private fun String.capitalizeForSetter(): String {
	if (this.first().isLowerCase()) {
		return this.replaceFirstChar { it.uppercase() }
	}
	
	return this;
}

private fun getAllAmbiguousCaseVariants(setterName: String): Set<String> {
	return setOf(setterName, setterName.replaceFirstChar { it.lowercaseChar() })
}

private fun makeAmbiguousSetter(propertyName: String): String {
	return "set${propertyName.capitalizeForSetter()}"
}
/**
 * Matches the setter format, with a possibly-capitalized property name.
 *
 * Group $1 = name of the target property, possibly capitalized.
 */
private val AMBIGUOUS_SETTER_PATTERN = Regex("^set(\\p{Upper}\\w*)$")

private fun makeNonAmbiguousSetter(propertyName: String): String {
	return "set-${propertyName}"
}
/**
 * Matches the setter format.
 *
 * Group $1 = name of the target property, guaranteed to have the same case as the property.
 */
private val UNAMBIGUOUS_SETTER_PATTERN = Regex("^set-(\\w+)$")


fun makeSetterName(property: FirPropertySymbol, useExplicitCase: Boolean = false): String {
	return makeSetterName(property.name, useExplicitCase)
}

fun makeSetterName(propertyName: Name, useExplicitCase: Boolean = false): String {
	return makeSetterName(propertyName.asString(), useExplicitCase)
}

fun makeSetterName(propertyName: String, useExplicitCase: Boolean = false): String {
	return when {
		useExplicitCase -> "set-${propertyName}"
		else -> "set${propertyName.capitalizeForSetter()}"
	}
}

fun makeSetterNameId(propertyName: Name): Name {
	return Name.identifier(makeSetterName(propertyName))
}

fun getPropertyNameFromSetterName(setterName: Name): Set<String>? {
	return getPropertyNameFromSetterName(setterName.asString())
}

fun getPropertyNameFromSetterName(setterName: String): Set<String>? {
	val fromUnambiguous = UNAMBIGUOUS_SETTER_PATTERN.matchEntire(setterName)?.groups[1]?.value
	if (fromUnambiguous != null) {
		return setOf(fromUnambiguous)
	}
	
	val fromAmbiguous = AMBIGUOUS_SETTER_PATTERN.matchEntire(setterName)?.groups[1]?.value
	                ?: return null;
	return getAllAmbiguousCaseVariants(fromAmbiguous)
}

fun FirBasedSymbol<*>.isVisibleFrom(origin: FirBasedSymbol<*>, session: FirSession): Boolean {
	val status = when (this) {
		is FirCallableSymbol<*> -> resolvedStatus
		is FirClassLikeSymbol -> resolvedStatus
		else -> return true
	}
	return isVisibleFrom(origin, status, session)
}
