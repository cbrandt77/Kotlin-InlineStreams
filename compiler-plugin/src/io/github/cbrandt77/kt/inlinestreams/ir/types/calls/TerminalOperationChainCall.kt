package io.github.cbrandt77.kt.inlinestreams.ir.types.calls

import io.github.cbrandt77.kt.inlinestreams.ir.StreamOpContext
import io.github.cbrandt77.kt.inlinestreams.ir.types.declarations.chain.TerminalOperationChainFunction
import io.github.cbrandt77.kt.inlinestreams.util.flatMap
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.render

@JvmInline value class TerminalOperationChainCall private constructor(val internal: StreamOperationChainCall) {
	companion object {
		context(data: StreamOpContext)
		operator fun invoke(call: IrCall): Result<TerminalOperationChainCall> {
			return StreamOperationChainCall.invoke(call).flatMap {
				if (it.ir.type.classOrNull == data.inlineStreamClassSymbol)
					Result.failure(IllegalArgumentException("Terminal operation returns InlineStream."))
				else
					Result.success(TerminalOperationChainCall(it))
			}
		}
	}
	
	
	val ir get() = internal.ir
	
	context(_: StreamOpContext)
	val receiver: IrExpression get() = internal.receiver
	
	context(_: StreamOpContext)
	val previousOperation get() = internal.previousOperation
	
	context(_: StreamOpContext)
	val inputElementType: IrType get() = internal.inputElementType
	
	val outputType: IrType
		get() = ir.type
	
	context(_: StreamOpContext)
	val target get() = TerminalOperationChainFunction(internal._targetFunc)
	
	override fun toString() = internal.toString()
}