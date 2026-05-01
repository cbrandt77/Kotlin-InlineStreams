package io.github.cbrandt77.kt.inlinestreams.ir.types.annotations

import io.github.cbrandt77.kt.inlinestreams.NamesAndIds
import io.github.cbrandt77.kt.inlinestreams.ir.StreamOpContext
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstKind
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.defaultConstructor
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.primaryConstructor

@JvmInline value class InlineStreamFunctionReferenceAnnotation(val ir: IrConstructorCall) {
	companion object {
		context(data: StreamOpContext)
	    operator fun invoke(identifier: Int): InlineStreamFunctionReferenceAnnotation {
			return InlineStreamFunctionReferenceAnnotation(IrConstructorCallImpl(
				startOffset = -1, endOffset = -1,
				type = data.linkingAnnotationClassSymbol.defaultType,
				symbol = data.linkingAnnotationClassSymbol.owner.primaryConstructor!!.symbol,
				typeArgumentsCount = 0, constructorTypeArgumentsCount = 0
			)).also {
				it.identifier = identifier
			}
	    }
		
		operator fun invoke(irFunc: IrFunction): InlineStreamFunctionReferenceAnnotation? {
			return irFunc.getAnnotation(NamesAndIds.linkingAnnotationFqn)?.let { InlineStreamFunctionReferenceAnnotation(it) }
		}
	}
	
	context(data: StreamOpContext)
	var identifier
		get() = (ir.arguments[0] as IrConst).value as Int?
		set(value) {
			val arg = ir.arguments.getOrNull(0) as IrConst?
			          ?: IrConstImpl(-1, -1, data.context.irBuiltIns.intType, IrConstKind.Int, value).also {
				          ir.arguments[0] = it
			          }
			arg.value = value
		}
}