package io.github.cbrandt77.kt.inlinestreams.ir.types.calls

import io.github.cbrandt77.kt.inlinestreams.ir.StreamOpContext
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull

@JvmInline value class TerminalOperationCall private constructor(private val internal: StreamOperationCall) {
	companion object {
		context(data: StreamOpContext)
		operator fun invoke(call: IrCall): Result<TerminalOperationCall> {
			return StreamOperationCall.invoke(call).mapCatching {
				if (it.ir.type.classOrNull == data.inlineStreamClassSymbol)
					throw IllegalArgumentException("Terminal operation returns InlineStream.")
				
				TerminalOperationCall(it)
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
}