package io.github.cbrandt77.kt.inlinestreams.util

import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType

/**
 * Check two type parameters from different methods for equality
 */
fun typeParametersMatch(x: IrTypeParameterSymbol, y: IrTypeParameterSymbol): Boolean {
	return x.owner.variance == y.owner.variance
	       && x.owner.superTypes.asSequence().zip(y.owner.superTypes.asSequence()).all { typesMatch(it.first, it.second) };
}

/**
 * Check two IrTypes from different methods.
 */
fun typesMatch(x: IrType, y: IrType): Boolean {
	if (x == y)
		return true;
	
	if (x !is IrSimpleType || y !is IrSimpleType) { // if either is a non-simple type and they aren't already equal, false
		return false;
	}
	
	val xClassifier = x.classifier
	val yClassifier = y.classifier
	
	when (bothInstanceOf<IrTypeParameterSymbol>(xClassifier, yClassifier)) {
		true -> return typeParametersMatch(xClassifier, yClassifier)
		null -> return false;
		false -> {}
	}
	
	if (xClassifier != yClassifier) // if both are classes and are not equal
		return false;
	
	
	// > TYPE ARGUMENTS
	return x.arguments.zipAllMatch(y.arguments) { xArg, yArg ->
		when {
			xArg == yArg -> true
			else -> when (bothInstanceOf<IrType>(xArg, yArg)) {
				true -> typesMatch(xArg, yArg)
				null -> false
				false -> false // not equal and not both IrType => definitely not equal
			}
		}
	} ?: false; // arg counts aren't equal => this shouldn't happen, but definitely not equal
}