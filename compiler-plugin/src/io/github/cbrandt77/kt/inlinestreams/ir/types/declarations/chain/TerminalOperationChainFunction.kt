package io.github.cbrandt77.kt.inlinestreams.ir.types.declarations.chain

import io.github.cbrandt77.kt.inlinestreams.NamesAndIds.isTerminalOperation
import io.github.cbrandt77.kt.inlinestreams.ir.StreamOpContext
import io.github.cbrandt77.kt.inlinestreams.util.flatMap
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrFail
import org.jetbrains.kotlin.ir.util.callableId
import org.jetbrains.kotlin.ir.util.statements

@JvmInline value class TerminalOperationChainFunction private constructor(val internal: StreamOperationChainFunction) {
	companion object {
		context(data: StreamOpContext)
		operator fun invoke(ir: IrFunction): Result<TerminalOperationChainFunction> = with (data) {
			return StreamOperationChainFunction.invoke(ir).flatMap {
				if (it.ir.body!!.statements.none { it is IrCall && it.symbol.owner.callableId.isTerminalOperation })
					Result.failure(IllegalArgumentException("Must call `terminalOperation` DSL function."))
				else
					Result.success(TerminalOperationChainFunction(it))
			}
		}
	}
	
	val ir get() = internal.ir
	
	val receiver get() = internal.receiver
	
	context(_: StreamOpContext)
	val inputElementType get() = internal.inputElementType
	
	val outputType: IrType
		get() = ir.returnType
	
	/**
	 * Statements before the loop operation definition
	 */
	context(_: StreamOpContext)
	val preamble get() = internal.preamble
	
	/**
	 * Statements after the loop operation definition, including the value returned and any postprocessing.
	 */
	context(data: StreamOpContext)
	val epilogue: Sequence<IrStatement> get() {
		if (body is IrExpressionBody || body.statements.size == 1)
			return body.statements[0].run { sequenceOf(IrReturnImpl(startOffset, endOffset, data.unitType, internal.ir.symbol, IrGetObjectValueImpl(-1, -1, data.unitType, data.unitType.classOrFail))) }
		
		return body.statements.asSequence()
			.dropWhile { !(it is IrCall && it.symbol.owner.callableId.isTerminalOperation) }
			.drop(1)
	}
	
	val parameters get() = internal.parameters
	
	context(_: StreamOpContext)
	val identifier get() = internal.identifier
	
	val body get() = internal.body
	
	override fun toString() = internal.toString()
}