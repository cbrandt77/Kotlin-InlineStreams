/*
 * Copyright (C) 2026 Caleb Brandt
 *
 * This file is part of Inline Streams.
 *
 * Inline Streams is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Inline Streams is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.cbrandt77.kt.inlinestreams.util

import dev.zacsweers.metro.compiler.compat.CompatContext.Companion.getContainingClassSymbol
import dev.zacsweers.metro.compiler.compat.ext.toRegularClassSymbol
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirDeclarationStatus
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.processAllDeclaredCallables
import org.jetbrains.kotlin.fir.declarations.utils.isExtension
import org.jetbrains.kotlin.fir.packageFqName
import org.jetbrains.kotlin.fir.resolve.getContainingClass
import org.jetbrains.kotlin.fir.resolve.getSuperTypes
import org.jetbrains.kotlin.fir.resolve.providers.firProvider
import org.jetbrains.kotlin.fir.resolve.providers.getContainingFile
import org.jetbrains.kotlin.fir.resolve.toClassLikeSymbol
import org.jetbrains.kotlin.fir.resolve.toClassSymbol
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.utils.exceptions.withFirSymbolEntry
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.utils.exceptions.errorWithAttachment


fun FirClassSymbol<*>.getDeclaredAndInheritedCallables(session: FirSession, memberRequiredPhase: FirResolvePhase = FirResolvePhase.STATUS): Sequence<FirCallableSymbol<*>> {
	val superTypeSequence = this.getSuperTypes(session)
		.asSequence()
		.mapNotNull { it.toClassSymbol(session) }
	
	return (sequenceOf(this) + superTypeSequence)
		.flatMap { classSymbol ->
			mutableListOf<FirCallableSymbol<*>>().apply {
				classSymbol.processAllDeclaredCallables(session, memberRequiredPhase, processor=this::add)
			}// possible stdlib addition: sequence flat-mapping with callback support?
		}
}

fun FirFunction.getReceiverClass(session: FirSession): FirRegularClassSymbol? {
	return when {
		this.isExtension -> this.receiverParameter?.typeRef?.toRegularClassSymbol(session)
		else -> this.getContainingClass()?.symbol
	}
}

fun FirCallableSymbol<*>.getReceiverClass(session: FirSession): FirRegularClassSymbol? {
	return when {
		this.isExtension -> this.receiverParameterSymbol?.resolvedType?.toRegularClassSymbol(session)
		else -> this.getContainingClassSymbol() as? FirRegularClassSymbol
	}
}

fun findPropertyByName(owningClass: FirClassSymbol<*>, propertyName: String, session: FirSession): Sequence<FirPropertySymbol> {
	val name = Name.identifier(propertyName)
	return owningClass.propertiesInScope(session).filter { it.name == name }
}

// TODO turn this into a scope search instead of a direct "check the class", both so we get the extension properties AND the supertype properties
fun FirClassSymbol<*>.propertiesInScope(session: FirSession, memberRequiredPhase: FirResolvePhase = FirResolvePhase.STATUS): Sequence<FirPropertySymbol> {
	return this.getDeclaredAndInheritedCallables(session, memberRequiredPhase)
		.filterIsInstance<FirPropertySymbol>()
}


/**
 * Returns true if this symbol can be accessed by the argument.
 */
fun FirBasedSymbol<*>.isVisibleFrom(attemptedInvoker: FirBasedSymbol<*>, status: FirDeclarationStatus, session: FirSession): Boolean {
//	fun FirBasedSymbol<*>.getPackageName() = when (this) {
//		is FirCallableSymbol<*> -> callableId.packageName
//		is FirClassLikeSymbol<*> -> classId.packageFqName
//		else -> null
//	}
	
	val ourVisibility = status.visibility
	if (ourVisibility == Visibilities.Public)
		return true;
	
	// For any non-public visibility:
	run {
		when (val ourContainingClass = this.getContainingClassSymbol()) {
			// If we're on the top level and if the invoker is declared in the same file, it's always "true", even if it's private
			null -> {
				val ourTopLevelContainer = session.firProvider.getContainingFile(this)
				                           ?: errorWithAttachment("No containing file found for `this` based symbol") {
					                           withFirSymbolEntry("this", this@isVisibleFrom)
				                           }
				
				val theirTopLevelContainer = session.firProvider.getContainingFile(attemptedInvoker);
				if (ourTopLevelContainer.symbol == theirTopLevelContainer?.symbol)
					return true;
			}
			// If we're in a class, and the invoker is either in the same class or in an outer class of this, it's always "true", even if the member is private
			else -> {
				val theirContainingClass = attemptedInvoker.getContainingClassSymbol() ?: return@run
				
				if (ourContainingClass == theirContainingClass || ourContainingClass.isDeclaredInInnerClassOf(theirContainingClass))
					return true;
			}
		}
	}

//	if (!ourVisibility.visibleFromPackage(attemptedInvoker.packageFqName(), getPackageName() ?: return true))
//		return false;
	
	return when (ourVisibility) {
		// If "Private", and the above conditions weren't satisfied, it's always "false".
		Visibilities.Private, Visibilities.PrivateToThis -> false
		// If "Internal", we only have to check if they're in the same module.
		Visibilities.Internal -> attemptedInvoker.moduleData.let { containingClassModuleData ->
			when (moduleData) {
				containingClassModuleData -> true
				in containingClassModuleData.friendDependencies -> true
				in containingClassModuleData.dependsOnDependencies -> true
				else -> false
			}
		}
		// If "Protected", we only have to check if we're in a superclass of the invoker's declared class.
		Visibilities.Protected -> attemptedInvoker.isDeclaredInSubClassOf(this, session)
		else -> false
	}
}


/**
 * Check if `declaredInOuter` can see `this`'s private members. (no jokes)
 * ```
 * class Foo {
 *   class Bar {
 *      private val inBar // this
 *   }
 *
 *   fun inFoo() { // declaredInOuter
 *      // I want to check if I can reference `Bar#inBar` from here
 *   }
 * }
 * ```
 */
fun FirBasedSymbol<*>.isDeclaredInInnerClassOf(declaredInOuter: FirBasedSymbol<*>): Boolean {
	if (declaredInOuter.packageFqName() != this.packageFqName())
		return false;
	
	// if neither are declared in classes, then it can't be in a parent
	var ourPossiblyInnerContainer = this.containingClassOrSelf() ?: return false;
	
	val theirPossiblyOuterContainerId = declaredInOuter.containingClassOrSelf()?.classId ?: return false;
	
	while (true) {
		if (ourPossiblyInnerContainer.classId == theirPossiblyOuterContainerId)
			return true;
		
		ourPossiblyInnerContainer = ourPossiblyInnerContainer.getContainingClassSymbol() ?: return false;
	}
}

/**
 * Returns false if:
 * - `declaredInSuper` and `this` are not either a) a class or b) declared inside a class
 * - `this` is not either a subclass of or declared inside a subclass of `declaredInSuper`'s declaring class (or itself if declaredInSuper IS a class)
 *
 * Else returns true
 */
fun FirBasedSymbol<*>.isDeclaredInSubClassOf(declaredInSuper: FirBasedSymbol<*>, session: FirSession): Boolean {
	val superContainingClass = declaredInSuper.containingClassOrSelf()?.classId ?: return false
	
	val ourContainingClass: FirClassLikeSymbol<*> = this.containingClassOrSelf() ?: return false
	
	return ourContainingClass.getSuperTypes(session, true).any { it.toClassLikeSymbol(session)?.classId == superContainingClass }
}

/**
 * Returns `this` if `this` is a FirClassLikeSymbol, or else attempts to get the containing class symbol of `this`
 */
fun FirBasedSymbol<*>.containingClassOrSelf(): FirClassLikeSymbol<*>? {
	return when (this) {
		is FirClassLikeSymbol<*> -> this
		else -> this.getContainingClassSymbol()
	}
}