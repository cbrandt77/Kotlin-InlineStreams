package io.github.cbrandt77.kt.inlinestreams.ir.visitors

import io.github.cbrandt77.kt.inlinestreams.NamesAndIds
import io.github.cbrandt77.kt.inlinestreams.NamesAndIds.IntermediateOperations
import io.github.cbrandt77.kt.inlinestreams.ir.StreamOpContext
import io.github.cbrandt77.kt.inlinestreams.ir.types.calls.IntermediateOperationChainCall
import io.github.cbrandt77.kt.inlinestreams.ir.types.calls.StreamOperationChainCall
import io.github.cbrandt77.kt.inlinestreams.ir.types.calls.TerminalOperationChainCall
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.jvm.functionByName
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin.Companion.LOCAL_FUNCTION_FOR_LAMBDA
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.declarations.impl.IrVariableImpl
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin.Companion.FOR_LOOP_INNER_WHILE
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin.Companion.FOR_LOOP_NEXT
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin.Companion.LAMBDA
import org.jetbrains.kotlin.ir.expressions.IrStatementOriginImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnableBlockImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrSetValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrWhileLoopImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrReturnableBlockSymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrVariableSymbolImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.erasedUpperBound
import org.jetbrains.kotlin.ir.util.isSubtypeOfClass
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.Name
import kotlin.to

class InlineStreamLoopCreationException(cause: Throwable) : RuntimeException("Failure creating loop for InlineStream.", cause)

/**
 * Assertion: InlineStreams are never reassigned, they just happen in a single uninterrupted chain.
 */
class LoopCreator(val context: IrPluginContext, val data: StreamOpContext) : IrElementTransformerVoid() {
	companion object {
		val INLINE_STREAM by IrStatementOriginImpl
	}
	
	override fun visitFunction(declaration: IrFunction): IrStatement = with (data) {
		if (declaration.hasInlineStreamReceiver)
			return declaration;
		
		return super.visitFunction(declaration)
	}
	
	
	
	inner class FunctionChecker(val currentFunction: IrFunction) : IrElementTransformerVoid() {
		override fun visitCall(expression: IrCall): IrExpression = with (data) {
			if (expression.callsInlineStreamFunction) {
				return remappedInlineStream(expression, currentFunction)
			}
			
			return super.visitCall(expression)
		}
	}
	
	private fun StreamOpContext.remappedInlineStream(expression: IrCall, currentFunction: IrFunction): IrExpression {
		val terminalOperation = TerminalOperationChainCall(expression).getOrElse {
			throw InlineStreamLoopCreationException(it)
		}
		
		val intermediatesLastToFirst = generateSequence(terminalOperation.previousOperation) { it.previousOperation }.map { it to it.target.getLoopFunction().getOrThrow() }.toList()
		val entranceFunction = (intermediatesLastToFirst.last().first.receiver as? IrCall)?.takeIf { it.callsInlineStreamEntranceFunction } ?: error("Intermediate operations not preceded by entrance function")
		
		val function0Unit = context.symbols.functionN(0).typeWith(context.irBuiltIns.unitType)
		
		val inputCollection = entranceFunction.extensionReceiver_!!
		val originalInputElementType = entranceFunction.typeArguments.first()!!
		val finalOutputType = terminalOperation.outputType
		
		val outerBlock = IrReturnableBlockImpl(-1, -1, finalOutputType, IrReturnableBlockSymbolImpl(), INLINE_STREAM)
		
		// TODO add preamble
		
		val iteratorType = context.irBuiltIns.iteratorClass.typeWith(originalInputElementType)
		
		// add "for" loop
		outerBlock.statements += IrBlockImpl(-1, -1, context.irBuiltIns.unitType, IrStatementOrigin.FOR_LOOP).also { loopBlock ->
			val iterVar = IrVariableImpl(-1, -1, IrDeclarationOrigin.FOR_LOOP_ITERATOR, IrVariableSymbolImpl(), Name.identifier("tmp_1"), iteratorType, isVar = false, isConst = false, isLateinit = false).apply {
				val type = inputCollection.type
				
				initializer = when {
					type.isSubtypeOfClass(context.symbols.iterator) -> inputCollection
					type.isSubtypeOfClass(context.symbols.iterable) -> IrCallImpl(-1, -1, iteratorType, context.irBuiltIns.iterableClass.functionByName("iterator")).withReceiver(inputCollection)
					type.classOrNull == context.irBuiltIns.arrayClass -> IrCallImpl(-1, -1, iteratorType, context.irBuiltIns.arrayClass.functionByName("iterator")).withReceiver(inputCollection)
					else -> error("Attempted to initialize with ${inputCollection.type.erasedUpperBound}")
				}
				
				parent = currentFunction
			}
			
			loopBlock.statements.add(iterVar)
			
			val unitType = context.irBuiltIns.unitType
			
			val theLoop = IrWhileLoopImpl(-1, -1, unitType, FOR_LOOP_INNER_WHILE).apply {
				condition = IrCallImpl(-1, -1, context.irBuiltIns.booleanType, context.symbols.iterator.functionByName("hasNext"), origin = IrStatementOrigin.FOR_LOOP_HAS_NEXT).withReceiver(IrGetValueImpl(-1, -1, iterVar.symbol))
			}.also {
				loopBlock.statements += it
			}
			
			val var_currentElementFromIterator = IrVariableImpl(-1, -1, IrDeclarationOrigin.FOR_LOOP_VARIABLE, IrVariableSymbolImpl(), IntermediateOperations.NAME_element, originalInputElementType, isVar = false, isConst = false, isLateinit = false)
				.withInitializer(
					IrCallImpl(-1, -1, originalInputElementType, context.symbols.iterator.functionByName("next"), origin = FOR_LOOP_NEXT)
						.withReceiver(IrGetValueImpl(-1, -1, iterVar.symbol)))
			
			val loopBody = IrReturnableBlockImpl(-1, -1, context.irBuiltIns.nothingType, IrReturnableBlockSymbolImpl()).apply {
				statements += var_currentElementFromIterator
			}.also {
				theLoop.body = it
			}
			
			fun IrCall.addBreakLoopArg() {
				setArgumentNamed(IntermediateOperations.NAME_breakLoop) { IrFunctionExpressionImpl(-1, -1, function0Unit, makeLambdaFunction {
					body = context.irFactory.createBlockBody(-1, -1)
						.apply {
							statements += IrReturnImpl(-1, -1, context.irBuiltIns.nothingType, loopBody.symbol, IrGetObjectValueImpl(-1, -1, unitType, context.symbols.unit))
						}
				}, LAMBDA) }
			}
			
			fun IrCall.addDownstreamActionArg(chainCall: IntermediateOperationChainCall, nextOperation: IrCall) {
				val typeU = chainCall.outputElementType
				val runner = makeLambdaFunction {
					val element = addValueParameter(name = IntermediateOperations.NAME_element, type = typeU)
					nextOperation.setArgumentNamed(IntermediateOperations.NAME_element) { IrGetValueImpl(-1, -1, element.symbol) }
					nextOperation.setDownstreamActionFunctionParent(this)
					body = context.irFactory.createExpressionBody(-1, -1, nextOperation)
					
				}
				
				this.setArgumentNamed(IntermediateOperations.NAME_downstreamAction) { IrFunctionExpressionImpl(-1, -1, context.irBuiltIns.functionN(1).typeWith(typeU, context.irBuiltIns.unitType), runner, LAMBDA) }
			}
			
			// first do terminal operation // TODO
			var previousStep: IrCall = terminalOperation.ir.apply {
				addDslFunArguments(terminalOperation.internal)
				// TODO wire state
				addBreakLoopArg()
			}
			
			for ((chainCall, loopFun) in intermediatesLastToFirst) {
				// wrap each one in the next operation successively
				previousStep = IrCallImpl(-1, -1, unitType, loopFun.ir.symbol).apply {
					chainCall.ir.typeArguments.forEachIndexed { index, type ->
						typeArguments[index] = type
					} // TODO make sure this works
					
					addDslFunArguments(chainCall.internal)
					addBreakLoopArg()
					addDownstreamActionArg(chainCall, previousStep) // also wires the "element" parameter for the previous (next) step
					// TODO figure out how anonymous function parenting works................
					// TODO wire state
				}
			}
			
			previousStep.setArgumentNamed(IntermediateOperations.NAME_element) { IrGetValueImpl(-1, -1, var_currentElementFromIterator.symbol) }
			
			loopBlock.statements += previousStep
		}
		
		// TODO add epilogue from terminal operation
		
		return outerBlock;
	}
}



fun IrVariable.withInitializer(initializer: IrExpression) = this.also {
	it.initializer = initializer
}

fun IrCall.withReceiver(receiver: IrExpression) = also {
	it.arguments[0] = receiver
}

/**
 * Only runs the [value] lambda if the argument exists, to avoid unnecessary computation.
 */
inline fun IrCall.setArgumentNamed(name: String, value: () -> IrExpression) {
	symbol.owner.parameters.firstOrNull { it.name.asString() == name }?.also {
		arguments[it] = value()
	}
}

/**
 * Only runs the [value] lambda if the argument exists, to avoid unnecessary computation.
 */
inline fun IrCall.setArgumentNamed(name: Name, value: () -> IrExpression) {
	symbol.owner.parameters.firstOrNull { it.name == name }?.also {
		arguments[it] = value()
	}
}

fun IrCall.getArgumentNamed(name: Name): IrExpression? {
	return symbol.owner.parameters.firstOrNull { it.name == name }?.let {
		arguments[it]
	}
}

context(ctx: StreamOpContext)
inline fun makeLambdaFunction(rType: IrType = ctx.context.irBuiltIns.unitType, onNewlyCreatedFunction: IrFunction.() -> Unit = {}): IrSimpleFunction {
	return ctx.context.irFactory.buildFun {
		returnType = rType
		name = ctx.anonymous
		visibility = DescriptorVisibilities.LOCAL
		modality = Modality.FINAL
		origin = LOCAL_FUNCTION_FOR_LAMBDA
	}.apply(onNewlyCreatedFunction)
}

context(ctx: StreamOpContext)
fun makeGetterLambda(varType: IrType, targetVariable: IrVariable): IrExpression {
	return IrFunctionExpressionImpl(-1, -1, ctx.context.irBuiltIns.functionN(0).typeWith(varType), makeLambdaFunction(varType) {
		body = ctx.context.irFactory.createExpressionBody(-1, -1, IrGetValueImpl(-1, -1, targetVariable.symbol))
	}, LAMBDA)
}

context(ctx: StreamOpContext)
fun makeSetterLambda(varType: IrType, targetVariable: IrVariable): IrExpression {
	return IrFunctionExpressionImpl(-1, -1, ctx.context.irBuiltIns.functionN(1).typeWith(varType), makeLambdaFunction {
		val value = addValueParameter(name = StandardNames.DEFAULT_VALUE_PARAMETER, type = varType)
		body = ctx.context.irFactory.createExpressionBody(-1, -1, IrSetValueImpl(-1, -1, targetVariable.type, targetVariable.symbol, IrGetValueImpl(-1, -1, value.symbol), null))
	}, LAMBDA)
}

fun IrCall.setDownstreamActionFunctionParent(parentFun: IrFunction) {
	val funcExpr = (this.getArgumentNamed(IntermediateOperations.NAME_downstreamAction) as IrFunctionExpression?) ?: return;
	
	funcExpr.function.parent = parentFun
}



context(ctx: StreamOpContext)
fun IrCall.addDslFunArguments(chainCall: StreamOperationChainCall) {
	chainCall.ir.arguments.forEachIndexed { i, it ->
		val chainFunParameter = chainCall.ir.symbol.owner.parameters[i]
		setArgumentNamed(ctx.getDslFunParameterName(chainFunParameter.name)) { it ?: chainFunParameter.defaultValue?.expression ?: error("Null argument with no default value on the base function") }
	}
}

context(ctx: StreamOpContext)
fun IrCall.setStateGettersAndSetters(originalStateVariableName: Name, stateVariableOutsideLoop: IrVariable): Unit = with (ctx) {
	val varType = stateVariableOutsideLoop.type
	
	setArgumentNamed(makeGetterNameForState(originalStateVariableName)) { makeGetterLambda(varType, stateVariableOutsideLoop) }
	
	setArgumentNamed(makeSetterNameForState(originalStateVariableName)) { makeSetterLambda(varType, stateVariableOutsideLoop) }
}

