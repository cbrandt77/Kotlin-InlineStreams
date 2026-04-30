package io.github.cbrandt77.kt.inlinestreams.ir.types.calls

import io.github.cbrandt77.kt.inlinestreams.ir.StreamOpContext
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.substitute

@JvmInline value class IntermediateOperationCall private constructor(private val internal: StreamOperationCall) {
	companion object {
		context(data: StreamOpContext)
		operator fun invoke(call: IrCall): Result<IntermediateOperationCall> = with (data) {
			return StreamOperationCall.invoke(call).mapCatching {
				if (it.ir.type.classOrNull != inlineStreamClassSymbol) {
					throw IllegalArgumentException("Intermediate operation does not return an InlineStream.")
				}
				
				IntermediateOperationCall(it)
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
	
	context(data: StreamOpContext)
	val outputElementType: IrType
		get() = with (data) { ir.type.substitute(ir.symbol.owner.typeParameters, ir.typeArguments.filterNotNull()).let { getInlineStreamTypeParameterT(it) } ?: error("Intermediate operation does not return InlineStream<>") }
}