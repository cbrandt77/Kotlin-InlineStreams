package io.github.cbrandt77.kt.inlinestreams.ir

import io.github.cbrandt77.kt.inlinestreams.test.FirUnitTester
import io.github.cbrandt77.kt.inlinestreams.test.IrTestVisitor
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.serialization.addLanguageFeaturesToManifest
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.visitors.IrVisitor
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.resolve.featureDependencies
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.directives.CodegenTestDirectives
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

private const val HEADER = """
// RUN_PIPELINE_TILL: BACKEND
// MODULE: main
// FILE: Main.kt
package pkg

import io.github.cbrandt77.kt.inlinestreams.*
"""

class StreamOpContextTest : FirUnitTester() {
	
	@FirTest
	fun TestData.IrFunction_actsOnInlineStream() {
		code = """
// LANGUAGE: +ContextParameters
$HEADER


fun <T> InlineStream<T>.does1() {}

fun InlineStream<String>.does2() {}

fun InlineStream<Boolean>.does3(): InlineStream<String> { TODO() }

fun doesNot1() {}

fun String.doesNot2(): InlineStream<String> { TODO() }


context(_: InlineStream<Boolean>)
fun doesNot3() {}

fun doesNot4(x: InlineStream<String>): InlineStream<Boolean> {TODO()}
		""".trimIndent()
		
		irVisitors += object : IrTestVisitor() {
			lateinit var context: StreamOpContext
			
			override fun visitFunction(declaration: IrFunction, data: IrPluginContext) {
				if (!::context.isInitialized) {
					context = StreamOpContext(data)
				}
				
				with (context) {
					val name = declaration.name.identifierOrNullIfSpecial ?: return;
					if (name.startsWith("does")) {
						assertEquals(!name.startsWith("doesNot"), declaration.actsOnInlineStream) { declaration.render() }
					}
				}
			}
		}
	}
	
	@FirTest
	fun TestData.IrCall_callsInlineStreamFunction() {
		disableFirDiagnostics = true
		
		code = """
$HEADER

fun InlineStream<String>.foo() {

}

fun test() {
	val x: InlineStream<String>
	x.foo()

	x.let {
	
	}

	x.run {
	
	}
}
		""".trimIndent()
		
		
		irVisitors += object : IrTestVisitor() {
			override fun visitFunction(declaration: IrFunction, data: IrPluginContext) {
				if (declaration.name.asString() == "test") {
					declaration.acceptChildren(this, data)
				}
			}
			
			override fun visitCall(expression: IrCall, data: IrPluginContext) = with (StreamOpContext(data)) {
				assertEquals(expression.symbol.owner.name.asString() == "foo", expression.callsInlineStreamFunction) { expression.render() }
			}
		}
	}
	
//	@FirTest
//	fun TestData.IrCall_callsInlineStreamEntranceFunction() {
//	}
//
//	@FirTest
//	fun TestData.getInlineStreamTypeParameterT() {
//	}
//
//	@FirTest
//	fun TestData.getExtensionReceiver_() {
//	}
//
//	@FirTest
//	fun TestData.getLoopOpFunctionName() {
//	}
//
//	@FirTest
//	fun TestData.getDslFunParameterName() {
//	}
//
//	@FirTest
//	fun TestData.isDslFunParameter() {
//	}
//
//	@FirTest
//	fun TestData.makeGetterNameForState() {
//	}
//
//	@FirTest
//	fun TestData.getNameThisIsGetterFor() {
//	}
//
//	@FirTest
//	fun TestData.makeSetterNameForState() {
//	}
//
//	@FirTest
//	fun TestData.getNameThisIsSetterFor() {
//	}
	
}