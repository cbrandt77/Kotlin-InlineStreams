package io.github.cbrandt77.kt.inlinestreams.ir.transformers

import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

class LoopCreator : IrElementTransformerVoid() {
	override fun visitCall(expression: IrCall): IrExpression {
		return super.visitCall(expression)
	}
}