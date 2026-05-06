package io.github.cbrandt77.kt.inlinestreams.ir.types.declarations.chain

import io.github.cbrandt77.kt.inlinestreams.ir.StreamOpContext
import io.github.cbrandt77.kt.inlinestreams.ir.types.annotations.InlineStreamFunctionReferenceAnnotation
import io.github.cbrandt77.kt.inlinestreams.util.stableHashCode
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.eraseIfTypeParameter
import org.jetbrains.kotlin.ir.util.isTopLevel
import org.jetbrains.kotlin.ir.util.statements

/**
 * The dsl function, like `x.map{}.flatMap{}.forEach{}`
 */
@JvmInline value class StreamOperationChainFunction private constructor(val ir: IrFunction) {
	companion object {
		context(data: StreamOpContext)
	    operator fun invoke(ir: IrFunction): Result<StreamOperationChainFunction> = with (data) {
			when {
				!ir.isTopLevel -> Result.failure(IllegalArgumentException("Expected to be top-level declaration"))
				!ir.hasInlineStreamReceiver -> Result.failure(IllegalArgumentException("Must take an InlineStream as a receiver"))
				else -> Result.success(StreamOperationChainFunction(ir))
			}
	    }
	}
	
	/**
	 * Get or create the unique hash linking this function with its LoopFunction
	 */
	context(data: StreamOpContext)
	val identifier: Int
		get() = InlineStreamFunctionReferenceAnnotation(ir)?.identifier
		        ?: createIdentifier()
	
	/**
	 * Create a unique identifier for this function and its loop function by hashing its erased parameter types
	 */
	context(data: StreamOpContext)
	private fun createIdentifier(): Int {
		val identifier: Int = arguments.map { it.type.eraseIfTypeParameter().classFqName }.toList().stableHashCode()
		
		ir.annotations += InlineStreamFunctionReferenceAnnotation(identifier).ir
		
		return identifier;
	}
	
	val receiver get() = ir.parameters.first { it.kind == IrParameterKind.ExtensionReceiver }
	
	context(data: StreamOpContext)
	val inputElementType: IrType
		get() = data.getInlineStreamTypeParameterT(receiver.type) ?: error("doesn't act on inline stream!")
	
	val arguments
		get() = ir.parameters.asSequence().filter { it.kind != IrParameterKind.ExtensionReceiver }
}