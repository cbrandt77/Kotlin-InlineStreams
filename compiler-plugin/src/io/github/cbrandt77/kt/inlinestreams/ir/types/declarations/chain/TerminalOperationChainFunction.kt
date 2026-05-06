package io.github.cbrandt77.kt.inlinestreams.ir.types.declarations.chain

import io.github.cbrandt77.kt.inlinestreams.NamesAndIds.isTerminalOperation
import io.github.cbrandt77.kt.inlinestreams.ir.StreamOpContext
import io.github.cbrandt77.kt.inlinestreams.util.flatMap
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.types.IrType
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
	val preamble get() = ir.body!!.statements.asSequence()
		.takeWhile { !(it is IrCall && it.symbol.owner.callableId.isTerminalOperation) }
	
	val stateVariables get() = preamble.filter { it is IrVariable }
	
	/**
	 * Statements after the loop operation definition, including the value returned and any postprocessing.
	 */
	val epilogue get() = ir.body!!.statements.asSequence()
		.dropWhile { !(it is IrCall && it.symbol.owner.callableId.isTerminalOperation) }
		.drop(1)
	
	val arguments get() = internal.arguments
	
	context(_: StreamOpContext)
	val identifier get() = internal.identifier
}