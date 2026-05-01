package io.github.cbrandt77.kt.inlinestreams.ir.types.calls

import io.github.cbrandt77.kt.inlinestreams.ir.StreamOpContext
import io.github.cbrandt77.kt.inlinestreams.ir.types.declarations.chain.IntermediateOperationChainFunction
import io.github.cbrandt77.kt.inlinestreams.util.flatMap
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.substitute

@JvmInline value class IntermediateOperationChainCall private constructor(val internal: StreamOperationChainCall) {
	companion object {
		context(data: StreamOpContext)
		operator fun invoke(call: IrCall): Result<IntermediateOperationChainCall> = with (data) {
			return StreamOperationChainCall.invoke(call).flatMap {
				if (it.ir.type.classOrNull != inlineStreamClassSymbol) {
					Result.failure(IllegalArgumentException("Intermediate operation does not return an InlineStream."))
				} else {
					Result.success(IntermediateOperationChainCall(it))
				}
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
	
	context(_: StreamOpContext)
	val target get() = IntermediateOperationChainFunction(internal.target)
}