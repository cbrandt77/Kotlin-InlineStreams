package io.github.cbrandt77.kt.inlinestreams.ir.types.declarations

import io.github.cbrandt77.kt.inlinestreams.NamesAndIds.isIntermediateOperation
import io.github.cbrandt77.kt.inlinestreams.NamesAndIds.isTerminalOperation
import io.github.cbrandt77.kt.inlinestreams.ir.StreamOpVisitor
import io.github.cbrandt77.kt.inlinestreams.ir.types.declarations.chain.IntermediateOperationChainFunction
import io.github.cbrandt77.kt.inlinestreams.ir.types.declarations.chain.StreamOperationChainFunction
import io.github.cbrandt77.kt.inlinestreams.ir.types.declarations.chain.TerminalOperationChainFunction
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
	return intermediateOperation { _, _, _ ->
	
	}
}

inline fun <T, U> InlineStream<T>.inT_outU(): InlineStream<U> {
	return intermediateOperation { _, _, _ ->
	
	}
}

inline fun <T : Iterable<U>, U : java.io.Serializable> InlineStream<T>.inIterableT_outSerializableU(): InlineStream<U> {
	return intermediateOperation { _, _, _ ->
	
	}
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

inline fun <T> InlineStream<T>.first(predicate: (T) -> Boolean): T {
	val data = 4

	data.let {
		println(it * 2)
	}
	
	val bbb = "bbb"

	var out: T? = null
	var hasSet = false
	
	terminalOperation { el, breakLoop ->
		if (predicate(el)) {
			out = el;
			hasSet = true;
			breakLoop()
		}
	}
	if (!hasSet)
		throw NoSuchElementException("No item found");
	
	return out!!;
}
		""".trimIndent()
		
		
		irVisitors += object : StreamOpVisitor() {
			override fun visitFunction(declaration: IrFunction, data: IrPluginContext) {
				init(data)
				if (declaration.name.asString() == "foo") {
					with(context) {
						val preamble = IntermediateOperationChainFunction(declaration).getOrThrow().preamble.toList()
						assertEquals(3, preamble.size) { "Preamble incorrect size.\n" + preamble.joinToString("\n") { it.render() } }
						assertNotEquals(true, (preamble.last() as? IrCall)?.symbol?.owner?.callableId?.isIntermediateOperation) { "Preamble captured DSL call: " + preamble.last().render() }
					}
				} else if (declaration.name.asString() == "first") {
					with (context) {
						val op = TerminalOperationChainFunction(declaration).getOrThrow()
						
						val preamble = op.preamble.toList()
						assertEquals(5, preamble.size) { "Preamble incorrect size.\n" + preamble.joinToString("\n") { it.render() } }
						assertNotEquals(true, (preamble.last() as? IrCall)?.symbol?.owner?.callableId?.isTerminalOperation) { "Preamble captured DSL call: " + preamble.last().render() }
						
						val epilogue = op.epilogue.toList()
						assertEquals(2, epilogue.size) { "Epilogue incorrect size.\n" + epilogue.joinToString("\n") { it.render() } }
						assertNotEquals(true, (epilogue.first() as? IrCall)?.symbol?.owner?.callableId?.isTerminalOperation) { "Epilogue captured DSL call: " + epilogue.first().render() }
					}
				}
			}
		}
	}
}