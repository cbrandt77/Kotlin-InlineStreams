package io.github.cbrandt77.kt.inlinestreams.ir

import io.github.cbrandt77.kt.inlinestreams.test.FirUnitTester
import io.github.cbrandt77.kt.inlinestreams.test.IrTestVisitor
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.junit.jupiter.api.Assertions.assertEquals

private const val HEADER = """
// RUN_PIPELINE_TILL: BACKEND
// MODULE: main
// FILE: Main.kt
package pkg

import io.github.cbrandt77.kt.inlinestreams.*
"""

// make it easy to only make a single context instead of making a new one for each call
open class StreamOpVisitor : IrTestVisitor() {
	lateinit var context: StreamOpContext
	
	protected fun init(data: IrPluginContext) {
		if (!::context.isInitialized)
			context = StreamOpContext(data)
	}
	
	override fun visitElement(element: IrElement, data: IrPluginContext) {
		init(data)
		super.visitElement(element, data)
	}
}

class StreamOpContextTest : FirUnitTester() {
	override fun configureEnv(builder: TestConfigurationBuilder) = with (builder) {
		defaultDirectives {
//			+CodegenTestDirectives.IGNORE_FIR_DIAGNOSTICS
//			+CodegenTestDirectives.IGNORE_FIR_DIAGNOSTICS_DIFF
//			+CodegenTestDirectives.IGNORE_BACKEND_DIAGNOSTICS
		}
	}
	
	@FirTest
	fun TestData.IrFunction_actsOnInlineStream() {
		@Suppress("UnusedReceiverParameter")
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

fun <T : InlineStream<*>> T.doesNot5() { TODO() }
		""".trimIndent()
		
		irVisitors += object : StreamOpVisitor() {
			
			override fun visitFunction(declaration: IrFunction, data: IrPluginContext) {
				init(data)
				
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
		@Suppress("UnusedReceiverParameter")
		code = """
$HEADER

fun InlineStream<String>.foo() {}


fun test() {
	listOf("foo").inlineStream().foo()

	listOf("foo").inlineStream().let {}

	listOf("foo").inlineStream().run {}
}
		""".trimIndent()
		
		
		irVisitors += object : StreamOpVisitor() {
			override fun visitFunction(declaration: IrFunction, data: IrPluginContext) {
				init(data)
				if (declaration.name.asString() == "test") {
					declaration.acceptChildren(this, data)
				}
			}
			
			override fun visitCall(expression: IrCall, data: IrPluginContext) {
				init(data)
				with(context) {
					assertEquals(expression.symbol.owner.name.asString() == "foo", expression.callsInlineStreamFunction) { expression.render() }
				}
				expression.acceptChildren(this, data)
			}
		}
	}
	
	@FirTest
	fun TestData.IrCall_callsInlineStreamEntranceFunction() {
		code = """
$HEADER

fun foo() {
	val x = listOf("").inlineStream()
	val y = arrayOf("").inlineStream()
	val z = listOf("").iterator().inlineStream()
}
		""".trimIndent()
		
		irVisitors += object : StreamOpVisitor() {
			override fun visitCall(expression: IrCall, data: IrPluginContext) {
				init(data)
				with (context) {
					assertEquals(expression.symbol.owner.name.asString() == "inlineStream", expression.callsInlineStreamEntranceFunction) { expression.render() }
				}
				
				expression.acceptChildren(this, data)
			}
		}
	}

	@FirTest
	fun TestData.getLoopOpFunction() {
		
		// TODO
	}
	
//	@Test @Ignore("Tested by XOperationChainCall inputType")
//	fun IrType_getInlineStreamTypeParameterT() {}
//
//
// TODO
//	@FirTest
//	fun TestData.getExtensionReceiver_() {
//		code = """
//fun String.foo() {
//
//}
//		""".trimIndent()
//	}

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