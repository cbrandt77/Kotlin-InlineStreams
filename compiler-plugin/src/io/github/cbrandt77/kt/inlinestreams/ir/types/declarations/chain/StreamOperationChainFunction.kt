package io.github.cbrandt77.kt.inlinestreams.ir.types.declarations.chain

import io.github.cbrandt77.kt.inlinestreams.NamesAndIds
import io.github.cbrandt77.kt.inlinestreams.ir.StreamOpContext
import io.github.cbrandt77.kt.inlinestreams.ir.types.annotations.InlineStreamFunctionReferenceAnnotation
import io.github.cbrandt77.kt.inlinestreams.util.stableHashCode
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConstKind
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.defaultConstructor
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.eraseIfTypeParameter
import org.jetbrains.kotlin.ir.util.erasedUpperBound
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.isTopLevel
import org.jetbrains.kotlin.ir.util.isTypeParameter
import org.jetbrains.kotlin.ir.util.statements

/**
 * The dsl function, like `x.map{}.flatMap{}.forEach{}`
 */
@JvmInline value class StreamOperationChainFunction private constructor(val ir: IrFunction) {
	companion object {
		context(data: StreamOpContext)
	    operator fun invoke(ir: IrFunction): Result<StreamOperationChainFunction> = data.runCatching {
			require(ir.isTopLevel) { "Expected to be top-level declaration" }
			require(ir.hasInlineStreamReceiver) { "Must take an InlineStream as a receiver" }
			
			StreamOperationChainFunction(ir)
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
	
	context(data: StreamOpContext)
	val preamble: Sequence<IrStatement>
		get() = with (data) {
			ir.body?.statements?.asSequence()
				.orEmpty()
				.takeWhile { it !is IrReturnImpl && (it as? IrCall)?.callsInlineStreamFunction != true }
		}
	
	val arguments
		get() = ir.parameters.asSequence().filter { it.kind != IrParameterKind.ExtensionReceiver }
}