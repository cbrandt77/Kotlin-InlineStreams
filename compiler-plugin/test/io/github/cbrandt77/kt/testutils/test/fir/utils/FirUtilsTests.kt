/*
 * Copyright (C) 2025 Caleb Brandt
 *
 * This file is part of Overloadable Setters.
 *
 * Overloadable Setters is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Overloadable Setters is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.cbrandt77.kt.overloadablesetters.test.fir.utils

import io.github.cbrandt77.kt.overloadablesetters.test.FirUnitTester
import io.github.cbrandt77.kt.overloadablesetters.test.checkDeclaration
import io.github.cbrandt77.kt.overloadablesetters.test.newAssignmentAlterer
import io.github.cbrandt77.kt.overloadablesetters.util.getDeclaredAndInheritedCallables
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.expressions.FirVariableAssignment
import org.jetbrains.kotlin.fir.expressions.calleeReference
import org.jetbrains.kotlin.fir.extensions.FirAssignExpressionAltererExtension
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.junit.jupiter.api.Assertions.*

class DeclaredAndInheritedCallablesTest : FirUnitTester() {
	
	inline fun TestData.runInAssignmentAndDeclaration(crossinline action: (cls: FirClassSymbol<*>, session: FirSession) -> Unit) {
		declCheckers += checkDeclaration<FirClass> { decl, session ->
			action(decl.symbol, session)
		}
		
		otherExtensions += newAssignmentAlterer { assignment, session ->
			val owningClass = assignment.calleeReference?.toResolvedCallableSymbol()?.dispatchReceiverType?.toRegularClassSymbol(session) ?: error("$assignment not callable")
			
			action(owningClass, session)
		}
	}
	
	fun id(clsName: String, callableName: String, packageName: String? = null): CallableId {
		val packageFq = packageName?.let { FqName(it) } ?: FqName.ROOT
		return CallableId(packageFq, FqName(clsName), Name.identifier(callableName))
	}
	
	fun List<String>.toIds(clsName: String, packageName: String? = null) = this.mapTo(mutableSetOf()) { id(clsName, it, packageName) }
	
	@FirTest
	fun TestData.`gets own functions with no parent`() {
		code = """
// RUN_PIPELINE_TILL: BACKEND
// MODULE: main
// FILE: Main.kt

class Main {
	var prop: String = ""
	
	fun foo() {
	
	}

	fun bar(strParam: String) {
	
	}

	fun Int.baz() {
	
	}
}

fun main() {
	Main().prop = "something"
}

		""".trimIndent()
		
		runInAssignmentAndDeclaration { cls, session ->
			val expectedNames = mutableSetOf("foo", "bar", "baz", "prop")
			cls.getDeclaredAndInheritedCallables(session).forEach {
				expectedNames.remove(it.name.asString())
			}
			assertTrue(expectedNames.isEmpty(), "Some names not found in class: $expectedNames")
		}
	}
	
	@FirTest
	fun TestData.`gets functions and properties from self and parent`() {
		code = """
// RUN_PIPELINE_TILL: BACKEND
// MODULE: main
// FILE: Main.kt
open class Parent {
	open val parentProp: String = "1"
}

class Child : Parent() {
	val childProp: String = ""

	override val parentProp: String
		get() = "4"
	
	fun foo() {}

	fun bar(strParam: String) {}

	fun Int.baz() {}
}
		""".trimIndent()
		
		runInAssignmentAndDeclaration { cls: FirClassSymbol<*>, session: FirSession ->
			val parentNames = listOf("parentProp").toIds("Parent")
			val expectedNames = when (cls.name.asString()) {
				"Parent" -> parentNames
				"Child" -> (parentNames + listOf("childProp", "foo", "bar", "baz", "parentProp").toIds("Child")).toMutableSet()
				else -> error("Unexpected class: ${cls.name}")
			}
			
			cls.getDeclaredAndInheritedCallables(session).forEach {
				expectedNames.remove(it.callableId)
			}
			
			assertTrue(expectedNames.isEmpty(), "Some names not found in class ${cls.name.asString()}: $expectedNames")
		}
	}
}