package io.github.cbrandt77.kt.inlinestreams.ir.visitors

import io.github.cbrandt77.kt.inlinestreams.NamesAndIds.IntermediateOperations.NAME_element
import io.github.cbrandt77.kt.inlinestreams.ir.StreamOpContext
import io.github.cbrandt77.kt.inlinestreams.ir.types.calls.TerminalOperationChainCall
import io.github.cbrandt77.kt.inlinestreams.ir.utils.LoopTranspositionUtils
import io.github.cbrandt77.kt.inlinestreams.ir.utils.LoopTranspositionUtils.IntermediateOperationWithState
import io.github.cbrandt77.kt.inlinestreams.ir.utils.LoopTranspositionUtils.OperationWithState
import io.github.cbrandt77.kt.inlinestreams.ir.utils.LoopTranspositionUtils.TerminalOperationWithState
import io.github.cbrandt77.kt.inlinestreams.ir.utils.LoopTranspositionUtils.callLoopOperationRecursive
import io.github.cbrandt77.kt.inlinestreams.ir.utils.LoopTranspositionUtils.createEpilogueStatements
import io.github.cbrandt77.kt.inlinestreams.ir.utils.LoopTranspositionUtils.createPreambleForLoop
import io.github.cbrandt77.kt.inlinestreams.ir.utils.LoopTranspositionUtils.makeLambdaFunction
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.jvm.functionByName
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin.Companion.FOR_LOOP_VARIABLE
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.declarations.impl.IrVariableImpl
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrSetValue
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin.Companion.FOR_LOOP_HAS_NEXT
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin.Companion.FOR_LOOP_INNER_WHILE
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin.Companion.FOR_LOOP_ITERATOR
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin.Companion.FOR_LOOP_NEXT
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin.Companion.LAMBDA
import org.jetbrains.kotlin.ir.expressions.IrStatementOriginImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrWhileLoopImpl
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.symbols.IrValueSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrVariableSymbolImpl
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.types.mergeNullability
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.AbstractTypeRemapper
import org.jetbrains.kotlin.ir.util.isSubtypeOfClass
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrTransformer
import org.jetbrains.kotlin.name.Name
import kotlin.collections.plus

class InlineStreamLoopCreationException(cause: Throwable) : RuntimeException("Failure creating loop for InlineStream.", cause)

object LoopOperationStateVariable : GeneratedDeclarationKey() {
	val origin = IrDeclarationOrigin.GeneratedByPlugin(this)
}

/**
 * Assertion: InlineStreams are never reassigned, they just happen in a single uninterrupted chain.
 */
class LoopCreator(val context: IrPluginContext, val data: StreamOpContext) : IrElementTransformerVoid() {
	companion object {
		val INLINE_STREAM_OPERATION by IrStatementOriginImpl
	}
	
	data class Data(val streamOpContext: StreamOpContext, val currentFunction: IrFunction)
	
	override fun visitFunction(declaration: IrFunction): IrStatement = with (data) {
		if (!declaration.hasInlineStreamReceiver)
			declaration.transformChildren(FunctionChecker, Data(data, declaration))
		
		return declaration;
	}
	
	
	
	object FunctionChecker : IrTransformer<Data>() {
		override fun visitFunction(declaration: IrFunction, data: Data): IrStatement {
			declaration.transformChildren(this, data.copy(currentFunction=declaration))
			return declaration;
		}
		
		override fun visitCall(expression: IrCall, data: Data): IrElement = with (data.streamOpContext) {
			if (expression.callsInlineStreamFunction) {
				return remappedInlineStream(expression, data.currentFunction)
			}
			
			return super.visitCall(expression, data)
		}
		
		
		
		
		
		
		private fun StreamOpContext.remappedInlineStream(expression: IrCall, currentFunction: IrFunction): IrExpression {
			val terminalOperationChainCall = TerminalOperationChainCall(expression).getOrElse {
				throw InlineStreamLoopCreationException(it)
			}
			val terminalOperation = TerminalOperationWithState(terminalOperationChainCall)
			
			
			val intermediatesLastToFirst = generateSequence(terminalOperationChainCall.previousOperation) { it.previousOperation }
				.map { IntermediateOperationWithState(it) }
				.toList()
			val entranceFunction = (intermediatesLastToFirst.last().chainCall.receiver as? IrCall)
				                       ?.takeIf { it.callsInlineStreamEntranceFunction }
			                                ?: error("Intermediate operations not preceded by entrance function")
			
			
			val inputCollection = entranceFunction.extensionReceiver_!!
			val originalInputElementType = entranceFunction.typeArguments.first()!!
			val finalOutputType = terminalOperationChainCall.outputType
			
			val iteratorType = irBuiltIns.iteratorClass.typeWith(originalInputElementType)
			
			
			val allOperations: List<OperationWithState> = intermediatesLastToFirst.asReversed() + terminalOperation
			
			
			
			
			// I'm a never-nester, but this is just such a deep tree that I HAVE TO nest it like it's actually represented
			
			
			return IrCallImpl(-1, -1, finalOutputType, kotlinRunFunction).also { outermostRunCall ->
				outermostRunCall.typeArguments[0] = finalOutputType
				outermostRunCall.arguments[0] = IrFunctionExpressionImpl(
					startOffset = -1, endOffset = -1,
					type = symbols.functionN(0).typeWith(finalOutputType),
					origin = LAMBDA,
					function = makeLambdaFunction(finalOutputType).also { outerReturnTarget ->
						outerReturnTarget.parent = currentFunction
						outerReturnTarget.body = irFactory.createBlockBody(-1, -1).also { outerBlock ->
							// create preamble for each operation, give them all their remapped state variables
							outerBlock.statements += allOperations.flatMapIndexed { index, state -> state.createPreambleForLoop(index, outerReturnTarget) }
							
							outerBlock.statements += IrCallImpl(-1, -1, irBuiltIns.unitType, kotlinRunFunction).also { breakLoopRunBlock ->
								breakLoopRunBlock.typeArguments[0] = irBuiltIns.unitType
								breakLoopRunBlock.arguments[0] = IrFunctionExpressionImpl(
									startOffset = -1, endOffset = -1,
									type = function0Unit,
									origin = LAMBDA,
									function = makeLambdaFunction(irBuiltIns.unitType).also { breakLoopReturnTarget ->
										breakLoopReturnTarget.parent = outerReturnTarget
										fun makeBreakLoopArg(parent: IrDeclarationParent) = IrFunctionExpressionImpl(-1, -1, function0Unit, makeLambdaFunction().apply {
											body = irFactory.createBlockBody(-1, -1)
												.apply {
													statements += IrReturnImpl(-1, -1, irBuiltIns.nothingType, breakLoopReturnTarget.symbol, IrGetObjectValueImpl(-1, -1, unitType, symbols.unit)) // TODO figure out how to make it not return the literal Unit
												}
											this.parent = parent
										}, LAMBDA)
										
										breakLoopReturnTarget.body = irFactory.createBlockBody(-1, -1).apply {
											statements += IrBlockImpl(-1, -1, irBuiltIns.unitType, IrStatementOrigin.FOR_LOOP).also { forLoopWrapperBlock ->
												val iterVar = IrVariableImpl(-1, -1, IrDeclarationOrigin.FOR_LOOP_ITERATOR, IrVariableSymbolImpl(), Name.identifier("tmp_1"), iteratorType, isVar = false, isConst = false, isLateinit = false)
													.withParent(breakLoopReturnTarget)
													.withInitializer(inputCollection.type.let { type -> when {
														type.isSubtypeOfClass(symbols.iterator) -> inputCollection
														type.isSubtypeOfClass(symbols.iterable) -> IrCallImpl(-1, -1, iteratorType, irBuiltIns.iterableClass.functionByName("iterator"), origin=FOR_LOOP_ITERATOR).withReceiver(inputCollection)
														type.classOrNull == irBuiltIns.arrayClass -> IrCallImpl(-1, -1, iteratorType, irBuiltIns.arrayClass.functionByName("iterator"), origin=FOR_LOOP_ITERATOR).withReceiver(inputCollection)
														else -> error("Attempted to initialize with $type")
													}})
												forLoopWrapperBlock.statements += iterVar
												
												forLoopWrapperBlock.statements += IrWhileLoopImpl(-1, -1, unitType, FOR_LOOP_INNER_WHILE).also { forLoopWhileBody ->
													forLoopWhileBody.condition = IrCallImpl(-1, -1, irBuiltIns.booleanType, symbols.iterator.functionByName("hasNext"), origin = FOR_LOOP_HAS_NEXT).withReceiver(IrGetValueImpl(-1, -1, iterVar.symbol))
													forLoopWhileBody.body = IrBlockImpl(-1, -1, unitType, FOR_LOOP_INNER_WHILE).also { whileBlock ->
														val var_currentElementFromIterator = IrVariableImpl(-1, -1, FOR_LOOP_VARIABLE, IrVariableSymbolImpl(), NAME_element, originalInputElementType, isVar = false, isConst = false, isLateinit = false)
															.withParent(breakLoopReturnTarget)
															.withInitializer(
																IrCallImpl(-1, -1, originalInputElementType, symbols.iterator.functionByName("next"), origin = FOR_LOOP_NEXT)
																	.withReceiver(IrGetValueImpl(-1, -1, iterVar.symbol)))
														
														whileBlock.statements += var_currentElementFromIterator
														
														whileBlock.statements += IrBlockImpl(-1, -1, irBuiltIns.nothingType).also { innermostBlock ->
															callLoopOperationRecursive(breakLoopReturnTarget, var_currentElementFromIterator.symbol, allOperations.iterator(), ::makeBreakLoopArg) {
																innermostBlock.statements += it
															}
														}
													}
												};
												
											}
										}
									}
								)
							}
							outerBlock.statements += terminalOperation.createEpilogueStatements(currentFunction, outerReturnTarget)
						}
					}
				)
			}
		}
	}
	
	
	/**
	 * Fills in type parameters with the type arguments provided
	 */
	class TypeArgRealizer(private val chainCall: IrCall) : AbstractTypeRemapper() {
		override fun remapTypeOrNull(type: IrType): IrType? {
			if (type !is IrSimpleType)
				return null;
			
			(type.classifierOrNull as? IrTypeParameterSymbol)?.let {
				return chainCall.typeArguments[it.owner.index]?.mergeNullability(type)
			}
			
			val newArgs = remapTypeArguments(type.arguments) ?: type.arguments
			return IrSimpleTypeImpl(type.classifier, type.nullability, newArgs, type.annotations)
		}
	}
	
	
	/**
	 * Converts references to variables within the DSL function to their corresponding variables outside the loop
	 */
	class StateVariableReferenceRedirector(val oldVarsToNewVarsMap: Map<IrValueSymbol, IrValueSymbol>) : IrElementTransformerVoid() {
		override fun visitElement(element: IrElement): IrElement {
			element.transformChildrenVoid()
			return element;
		}
		
		override fun visitGetValue(expression: IrGetValue): IrExpression {
			oldVarsToNewVarsMap[expression.symbol]?.let {
				expression.symbol = it
			}
			
			return super.visitGetValue(expression)
		}
		
		override fun visitSetValue(expression: IrSetValue): IrExpression {
			oldVarsToNewVarsMap[expression.symbol]?.let {
				expression.symbol = it
			}
			
			return super.visitSetValue(expression)
		}
	}
}

fun IrVariable.withInitializer(initializer: IrExpression) = this.also {
	it.initializer = initializer
}

fun IrVariable.withParent(parent: IrDeclarationParent) = this.also {
	it.parent = parent
}

fun IrCall.withReceiver(receiver: IrExpression) = also {
	it.arguments[0] = receiver
}


