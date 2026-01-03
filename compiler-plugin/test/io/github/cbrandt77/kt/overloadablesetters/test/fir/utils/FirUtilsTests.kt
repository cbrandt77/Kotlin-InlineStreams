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
import io.github.cbrandt77.kt.overloadablesetters.util.getDeclaredAndInheritedCallables
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.name.Name
import org.junit.jupiter.api.Assertions.*

class DeclaredAndInheritedCallablesTest : FirUnitTester() {
	@FirTest
	fun TestData.`gets own functions with no parent`() {
		code = """
// RUN_PIPELINE_TILL: BACKEND
// MODULE: main
// FILE: Main.kt

class Main {
	fun foo() {
	
	}

	fun bar(strParam: String) {
	
	}

	fun Int.baz() {
	
	}
}
		""".trimIndent()
		
		declCheckers += checkDeclaration<FirClass> { decl, session ->
			val expectedNames = mutableSetOf("foo", "bar", "baz", "yx")
			decl.symbol.getDeclaredAndInheritedCallables(session).forEach {
				expectedNames.remove(it.name.asString())
			}
			assertTrue(expectedNames.isEmpty(), "Some names not found in class: $expectedNames")
		}
	}
	
	@FirTest
	fun TestData.`gets functions and properties from self and parent`() {
		code = """
// RUN_PIPELINE_TILL: FRONTEND
// MODULE: main
// FILE: Main.kt
class Child {
	val childProp: String = ""
	override val parentProp: String
		get() = "4"
	
	fun foo() {
	
	}

	fun bar(strParam: String) {
	
	}

	fun Int.baz() {
	
	}
}
		""".trimIndent()
		
		declCheckers += checkDeclaration<FirClass> { decl, session ->
			val expectedNames = mutableSetOf("foo", "bar", "baz")
			decl.symbol.getDeclaredAndInheritedCallables(session).forEach {
				expectedNames.remove(it.name.asString())
			}
			assertTrue(expectedNames.isEmpty(), "Some names not found in class: $expectedNames")
		}
	}
}