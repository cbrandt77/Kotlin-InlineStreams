package io.github.cbrandt77.kt.inlinestreams.ir.types.declarations

import io.github.cbrandt77.kt.inlinestreams.NamesAndIds.isIntermediateOperation
import io.github.cbrandt77.kt.inlinestreams.ir.StreamOpVisitor
import io.github.cbrandt77.kt.inlinestreams.ir.types.declarations.chain.IntermediateOperationChainFunction
import io.github.cbrandt77.kt.inlinestreams.ir.types.declarations.chain.StreamOperationChainFunction
import io.github.cbrandt77.kt.inlinestreams.test.FirUnitTester
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.util.callableId
import org.jetbrains.kotlin.ir.util.isTypeParameter
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.directives.CodegenTestDirectives
import org.junit.jupiter.api.Assertions.*

private const val HEADER = """
// RUN_PIPELINE_TILL: BACKEND
// MODULE: main
// FILE: Main.kt
@file:Suppress("NOTHING_TO_INLINE")
package pkg

import io.github.cbrandt77.kt.inlinestreams.*
"""

class StreamOperationChainFunctionTest : FirUnitTester() {
	override fun configureEnv(builder: TestConfigurationBuilder) {
		builder.defaultDirectives {
			+CodegenTestDirectives.IGNORE_FIR_DIAGNOSTICS_DIFF
		}
	}
	
	@FirTest
	fun TestData.inputOutputElementType() {
		@Suppress("UnusedReceiverParameter")
		code = """
$HEADER;

inline fun InlineStream<String>.inString_outInt(): InlineStream<Int> {
	TODO()
}

inline fun <T, U> InlineStream<T>.inT_outU(): InlineStream<U> {
	TODO()
}

inline fun <T : Iterable<U>, U : java.io.Serializable> InlineStream<T>.inIterableT_outSerializableU(): InlineStream<U> {
	TODO()
}
			""".trimIndent()
		
		irVisitors += object : StreamOpVisitor() {
			override fun visitFunction(declaration: IrFunction, data: IrPluginContext) {
				init(data)
				with (context) {
					val wrapped = IntermediateOperationChainFunction(declaration).getOrThrow()
					when (declaration.name.asString()) {
						"inString_outInt" -> {
							assertEquals(data.irBuiltIns.stringType, wrapped.inputElementType) { declaration.render() }
							assertEquals(data.irBuiltIns.intType, wrapped.outputElementType) { declaration.render() }
						}
						"inT_outU" -> {
							assertTrue(wrapped.inputElementType.isTypeParameter())
							assertEquals(wrapped.ir.typeParameters[0].symbol, wrapped.inputElementType.classifierOrNull as? IrTypeParameterSymbol) { wrapped.inputElementType.render() }
							assertTrue(wrapped.outputElementType.isTypeParameter())
							assertEquals(wrapped.ir.typeParameters[1].symbol, wrapped.outputElementType.classifierOrNull as? IrTypeParameterSymbol)
						}
						"inIterableT_outSerializableU" -> {
							assertTrue(wrapped.inputElementType.isTypeParameter())
							assertEquals(wrapped.ir.typeParameters[0].symbol, wrapped.inputElementType.classifierOrNull as? IrTypeParameterSymbol) { wrapped.inputElementType.render() }
							
							assertTrue(wrapped.outputElementType.isTypeParameter())
							assertEquals(wrapped.ir.typeParameters[1].symbol, wrapped.outputElementType.classifierOrNull as? IrTypeParameterSymbol)
						}
					}
				}
			}
		}
	}
	
	@FirTest
	fun TestData.getsPreamble() {
		@Suppress("UnusedReceiverParameter")
		code = """
$HEADER;

inline fun InlineStream<String>.foo(): InlineStream<Unit> {
	val data = 4

	data.let {
		println(it * 2)
	}
	
	val bbb = "bbb"

	return intermediateOperation { el, _, _ ->
		println(el)
		println(bbb)
	}
}
		""".trimIndent()
		
		
		irVisitors += object : StreamOpVisitor() {
			override fun visitFunction(declaration: IrFunction, data: IrPluginContext) {
				init(data)
				if (declaration.name.asString() == "foo") {
					with(context) {
						val preamble = StreamOperationChainFunction(declaration).getOrThrow().preamble.toList()
						assertEquals(3, preamble.size) { preamble.joinToString("\n") { it.render() } }
						assertNotEquals(true, (preamble.last() as? IrCall)?.symbol?.owner?.callableId?.isIntermediateOperation) {
							preamble.last()
								.render()
						}
					}
				}
			}
		}
	}
}