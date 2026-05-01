package io.github.cbrandt77.kt.inlinestreams.ir.transformers

import io.github.cbrandt77.kt.inlinestreams.ir.StreamOpContext
import io.github.cbrandt77.kt.inlinestreams.ir.types.calls.TerminalOperationChainCall
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

class InlineStreamLoopCreationException(cause: Throwable) : RuntimeException("Failure creating loop for InlineStream.", cause) {

}

/**
 * Assertion: InlineStreams are never reassigned, they just happen in a single uninterrupted chain.
 */
class LoopCreator(val context: IrPluginContext, val data: StreamOpContext) : IrElementTransformerVoid() {
	override fun visitCall(expression: IrCall): IrExpression = with (data) {
		if (expression.callsInlineStreamFunction) {
			return remappedInlineStream(expression)
		}
		
		return super.visitCall(expression)
	}
	
	
	private fun StreamOpContext.remappedInlineStream(expression: IrCall): IrExpression {
		val terminalOperation = TerminalOperationChainCall(expression).getOrElse {
			throw InlineStreamLoopCreationException(it)
		}
		
		val intermediatesLastToFirst = generateSequence(terminalOperation.previousOperation) { it.previousOperation }.toList()
		val entranceFunction = (intermediatesLastToFirst.last().receiver as? IrCall)?.takeIf { it.callsInlineStreamEntranceFunction } ?: error("Intermediate operations not preceded by entrance function")
		
		val originalInputType = entranceFunction.typeArguments.first()!!
		val finalOutputType = terminalOperation.outputType
		
		// TODO maybe we just use our own custom block and return in that?
		val rootCall = IrCallImpl(-1, -1, finalOutputType, kotlinRunFunction, 1).apply {
			typeArguments += finalOutputType
		}
		
		val outerRunBlock = makeLambdaFunction(finalOutputType).also {
			rootCall.arguments += IrFunctionExpressionImpl(-1, -1, finalOutputType, it, IrStatementOrigin.LAMBDA)
		}
		
		
		outerRunBlock.body = context.irFactory.createBlockBody(-1, -1)
		
		// TODO add preamble
		
		
//		val forLoop = IrBlockImpl(-1, -1, context.irBuiltIns.unitType, IrStatementOrigin.FOR_LOOP).also { loopBlock ->
//			val iter = IrVariableImpl(-1, -1, IrDeclarationOrigin.FOR_LOOP_ITERATOR, IrVariableSymbolImpl(), Name.identifier("tmp_1"), , false, false, false).also { iterVar ->
////				iterVar.initializer = IrCallImpl()
//			}
//		}
//
//		val innerRunBlock = makeLambdaFunction()
//
//		for (it in streamCallsLastToFirst) {
//			// TODO handle state and wire getters/setters
//		}
		TODO()
	}
	
	val IrFunctionSymbol.loopOperationFunction: IrFunctionSymbol
		get() = TODO()
	
	
	fun StreamOpContext.makeLambdaFunction(rType: IrType = context.irBuiltIns.unitType): IrSimpleFunction {
		return context.irFactory.buildFun {
			returnType = rType
			name = anonymous
			visibility = DescriptorVisibilities.LOCAL
			modality = Modality.FINAL
		}
	}
}


