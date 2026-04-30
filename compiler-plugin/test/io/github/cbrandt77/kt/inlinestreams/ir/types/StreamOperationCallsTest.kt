package io.github.cbrandt77.kt.inlinestreams.ir.types

import io.github.cbrandt77.kt.inlinestreams.NamesAndIds.referenceStdStreamOperationFunction
import io.github.cbrandt77.kt.inlinestreams.ir.StreamOpContext
import io.github.cbrandt77.kt.inlinestreams.ir.types.calls.IntermediateOperationCall
import io.github.cbrandt77.kt.inlinestreams.ir.types.calls.TerminalOperationCall
import io.github.cbrandt77.kt.inlinestreams.test.FirUnitTester
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.fir.backend.utils.defaultTypeWithoutArguments
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.visitors.IrVisitor
import org.junit.jupiter.api.Assertions.*

class StreamOperationCallsTest : FirUnitTester() {
	@FirTest
	fun TestData.check1() {
		code = """
// RUN_PIPELINE_TILL: BACKEND
// MODULE: main
// FILE: Main.kt
package pkg

import io.github.cbrandt77.kt.inlinestreams.*

fun dsl(): Int? {
	return listOf("hi", "there", "gamer")
		.inlineStream()
		.map { it.toCharArray() }
		.flatMap { it.toList() }
		.map { it.code }
		.firstOrNull()
}
		""".trimIndent()
		
		
		irVisitors += object : IrVisitor<Unit, IrPluginContext>() {
			override fun visitElement(element: IrElement, data: IrPluginContext) {
				element.acceptChildren(this, data)
			}
			
			override fun visitCall(expression: IrCall, data: IrPluginContext): Unit = with (StreamOpContext(data)) { with (data) {
				val termOp = TerminalOperationCall(expression).getOrThrow()
				
				assertEquals(irBuiltIns.intType, termOp.inputElementType)
				assertEquals(irBuiltIns.intType.makeNullable(), termOp.outputType)
				
				generateSequence(termOp.previousOperation) { it.previousOperation }
					.toList()
					.also {
						assertEquals(3, it.size)
					}
					.zip<_, (IntermediateOperationCall) -> Unit>(listOf(
					{
						assertAll( "map it.code",
							{ assertEquals(data.referenceStdStreamOperationFunction("map"), it.ir.symbol) },
							{ assertEquals(irBuiltIns.charType, it.inputElementType) },
							{ assertEquals(irBuiltIns.intType, it.outputElementType) },
						)
					},
					{
						assertAll( "flatMap it.toList()",
							{ assertEquals(data.referenceStdStreamOperationFunction("flatMap"), it.ir.symbol) },
							{ assertEquals(irBuiltIns.charArray.defaultTypeWithoutArguments, it.inputElementType) },
							{ assertEquals(irBuiltIns.charType, it.outputElementType) },
						)
					},
					{
						assertAll( "map it.toCharArray()",
							{ assertEquals(data.referenceStdStreamOperationFunction("map"), it.ir.symbol) },
							{ assertEquals(irBuiltIns.stringType, it.inputElementType) },
							{ assertEquals(irBuiltIns.charArray.defaultTypeWithoutArguments, it.outputElementType) },
						)
					},
					))
					.forEach { (el, test) -> test(el) }
			}}
		}
	}
}