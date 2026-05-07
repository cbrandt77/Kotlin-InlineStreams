package io.github.cbrandt77.kt.inlinestreams.ir.visitors

import io.github.cbrandt77.kt.inlinestreams.NamesAndIds.IntermediateOperations
import io.github.cbrandt77.kt.inlinestreams.ir.StreamOpContext
import io.github.cbrandt77.kt.inlinestreams.ir.types.calls.TerminalOperationChainCall
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.jvm.functionByName
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin.Companion.LOCAL_FUNCTION_FOR_LAMBDA
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.declarations.impl.IrVariableImpl
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrSetValue
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
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.symbols.IrVariableSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrReturnableBlockSymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrVariableSymbolImpl
import org.jetbrains.kotlin.ir.transformStatement
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.AbstractTypeRemapper
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.isSubtypeOfClass
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrTransformer
import org.jetbrains.kotlin.name.Name

class InlineStreamLoopCreationException(cause: Throwable) : RuntimeException("Failure creating loop for InlineStream.", cause)

object LoopOperationStateVariable : GeneratedDeclarationKey() {
	val origin = IrDeclarationOrigin.GeneratedByPlugin(this)
}

/**
 * Assertion: InlineStreams are never reassigned, they just happen in a single uninterrupted chain.
 */
class LoopCreator(val context: IrPluginContext, val data: StreamOpContext) : IrElementTransformerVoid() {
	companion object {
		val INLINE_STREAM by IrStatementOriginImpl
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
			val terminalOperationChainFun = terminalOperationChainCall.target.getOrThrow()
			val terminalOperationLoopFun = terminalOperationChainFun.getLoopFunction().getOrThrow()
			
			val intermediatesLastToFirst = generateSequence(terminalOperationChainCall.previousOperation) { it.previousOperation }.map { val chainFun = it.target; Triple(it, chainFun, chainFun.getLoopFunction().getOrThrow()) }.toList()
			val entranceFunction = (intermediatesLastToFirst.last().first.receiver as? IrCall)?.takeIf { it.callsInlineStreamEntranceFunction } ?: error("Intermediate operations not preceded by entrance function")
			
			val function0Unit = context.symbols.functionN(0).typeWith(context.irBuiltIns.unitType)
			
			val inputCollection = entranceFunction.extensionReceiver_!!
			val originalInputElementType = entranceFunction.typeArguments.first()!!
			val finalOutputType = terminalOperationChainCall.outputType
			
			val outerBlock = IrReturnableBlockImpl(-1, -1, finalOutputType, IrReturnableBlockSymbolImpl(), INLINE_STREAM)
			
			
			val iteratorType = context.irBuiltIns.iteratorClass.typeWith(originalInputElementType)
			
			val fullPreamble = mutableListOf<IrStatement>()
			
			val forLoopBlock = IrBlockImpl(-1, -1, context.irBuiltIns.unitType, IrStatementOrigin.FOR_LOOP).also { loopBlock ->
				val iterVar = IrVariableImpl(-1, -1, IrDeclarationOrigin.FOR_LOOP_ITERATOR, IrVariableSymbolImpl(), Name.identifier("tmp_1"), iteratorType, isVar = false, isConst = false, isLateinit = false)
					.withParent(currentFunction)
					.withInitializer(inputCollection.type.let { type -> when {
						type.isSubtypeOfClass(context.symbols.iterator) -> inputCollection
						type.isSubtypeOfClass(context.symbols.iterable) -> IrCallImpl(-1, -1, iteratorType, context.irBuiltIns.iterableClass.functionByName("iterator")).withReceiver(inputCollection)
						type.classOrNull == context.irBuiltIns.arrayClass -> IrCallImpl(-1, -1, iteratorType, context.irBuiltIns.arrayClass.functionByName("iterator")).withReceiver(inputCollection)
						else -> error("Attempted to initialize with $type")
					}})
				
				loopBlock.statements.add(iterVar)
				
				val unitType = context.irBuiltIns.unitType
				
				val theLoop = IrWhileLoopImpl(-1, -1, unitType, FOR_LOOP_INNER_WHILE).apply {
					condition = IrCallImpl(-1, -1, context.irBuiltIns.booleanType, context.symbols.iterator.functionByName("hasNext"), origin = IrStatementOrigin.FOR_LOOP_HAS_NEXT).withReceiver(IrGetValueImpl(-1, -1, iterVar.symbol))
				}.also {
					loopBlock.statements += it
				}
				
				val var_currentElementFromIterator = IrVariableImpl(-1, -1, IrDeclarationOrigin.FOR_LOOP_VARIABLE, IrVariableSymbolImpl(), IntermediateOperations.NAME_element, originalInputElementType, isVar = false, isConst = false, isLateinit = false)
					.withParent(currentFunction)
					.withInitializer(
						IrCallImpl(-1, -1, originalInputElementType, context.symbols.iterator.functionByName("next"), origin = FOR_LOOP_NEXT)
							.withReceiver(IrGetValueImpl(-1, -1, iterVar.symbol)))
				
				
				val loopBody = IrReturnableBlockImpl(-1, -1, context.irBuiltIns.nothingType, IrReturnableBlockSymbolImpl()).apply {
					statements += var_currentElementFromIterator
				}.also {
					theLoop.body = it
				}
				
				val breakLoopFunc = makeLambdaFunction(Name.special("<lambda-breakLoop>")) {
					body = context.irFactory.createBlockBody(-1, -1)
						.apply {
							statements += IrReturnImpl(-1, -1, context.irBuiltIns.nothingType, loopBody.symbol, IrGetObjectValueImpl(-1, -1, unitType, context.symbols.unit)) // TODO figure out how to make it not return the literal Unit
						}
					parent = currentFunction
				}
				
				fun makeBreakLoopArg() = IrFunctionExpressionImpl(-1, -1, function0Unit, breakLoopFunc, LAMBDA)
				
				
				// just have the terminal operation if empty
				if (intermediatesLastToFirst.isEmpty()) {
					loopBlock.statements += IrCallImpl(-1, -1, unitType, terminalOperationLoopFun.ir.symbol).also {
						//TODO just have the terminal operation if empty
					}
					return@also;
				}
				
				
				val intermediates = intermediatesLastToFirst.reversed()
				
				val generatedCalls = intermediates.map { (chainCall, _, loopFun) ->
					IrCallImpl(-1, -1, unitType, loopFun.ir.symbol).apply {
						chainCall.ir.typeArguments.forEachIndexed { index, type ->
							typeArguments[index] = type
						}
					}
				} + IrCallImpl(-1, -1, unitType, terminalOperationLoopFun.ir.symbol).apply {
					terminalOperationChainCall.ir.typeArguments.forEachIndexed { index, type ->
						typeArguments[index] = type
					}
				}
				
				/** Each operation's `downstreamAction` argument: an element consumer that runs the next operation */
				val downstreamActionLambdas = intermediates.mapIndexed { i, (chainCall, _, _) ->
					val typeU = chainCall.outputElementType
					IrFunctionExpressionImpl(-1, -1, context.irBuiltIns.functionN(1).typeWith(typeU, context.irBuiltIns.unitType), makeLambdaFunction(Name.special("<lambda-downstreamAction>")) {
						addValueParameter(name = IntermediateOperations.NAME_element, type = typeU)
						
						val nextCall = generatedCalls.getOrNull(i + 1) ?: TODO("call terminal operation")
						
						body = context.irFactory.createExpressionBody(-1, -1, nextCall)
					}, LAMBDA)
				}
				
				downstreamActionLambdas.forEachIndexed { i, lambda ->
					// set each lambda's parents to be the previous operation's lambda
					lambda.function.parent = when (i) {
						0 -> currentFunction
						else -> downstreamActionLambdas[i - 1].function
					}
				}
				
				
				val operations = intermediates.map { Triple(it.first.internal, it.second.internal, it.third.internal) } + Triple(terminalOperationChainCall.internal, terminalOperationChainFun.internal, terminalOperationLoopFun.internal)
				
				assert(generatedCalls.size == operations.size)
				assert(downstreamActionLambdas.size == intermediates.size)
				
				for (operationIndex in operations.indices) {
					val (chainCall, chainFun, loopFun) = operations[operationIndex]
					val callingThisFunction = generatedCalls[operationIndex]
					
					// argument order: [...chainCall args], el, breakLoop, (downstreamAction), [...state getters and setters]
					sequenceOf(
						chainFun.arguments.map { chainCall.ir.arguments[it] ?: it.defaultValue!!.expression },
						// wire the element given to us to the call
						run {
							val previousStepsElement = if (operationIndex == 0) var_currentElementFromIterator else downstreamActionLambdas[operationIndex - 1].function.parameters[0]
							sequenceOf(IrGetValueImpl(-1, -1, previousStepsElement.symbol), makeBreakLoopArg())
						},
						// if is terminal operation, no downstreamActionLambda
						if (operationIndex == intermediates.size) emptySequence() else sequenceOf(downstreamActionLambdas[operationIndex])
					).flatten().forEach { arg ->
						callingThisFunction.arguments += arg
					}
					
					val typeArgRealizer = TypeArgRealizer(chainCall.ir)
					
					fun makeStateVariableName(varName: Name): Name {
						return Name.identifier("${operationIndex}$${chainFun.ir.name}$${varName.asStringStripSpecialMarkers()}")
					}
					
					val numArgsBeforeState = callingThisFunction.arguments.size
					
					val loopFunStateParameters = loopFun.parameters.drop(numArgsBeforeState).toList()
					
					if (loopFunStateParameters.isEmpty()) {
						loopBlock.statements.addAll(chainFun.preamble.map { it.deepCopyWithSymbols(currentFunction) { typeArgRealizer } })
					} else {
						val newlyAddedVars = mutableMapOf<IrVariableSymbol, IrVariableSymbol>()
						
						val variableRenamer = VariableRenamer(newlyAddedVars)
						
						for (statement in chainFun.preamble) {
							val newStatement = statement.deepCopyWithSymbols(currentFunction) { typeArgRealizer }
							if (statement is IrVariable) {
								val newStatement = newStatement as IrVariable
								
								newStatement.name = makeStateVariableName(statement.name)
								newlyAddedVars[statement.symbol] = newStatement.symbol
								
								// wire state getters and setters
								val getterName = makeGetterNameForState(statement.name)
								loopFunStateParameters.firstOrNull { it.name == getterName }?.let {
									callingThisFunction.arguments[it] = makeGetterLambda(newStatement.type, newStatement)
								}
								
								val setterName = makeSetterNameForState(statement.name)
								loopFunStateParameters.firstOrNull { it.name == setterName }?.let {
									callingThisFunction.arguments[it] = makeSetterLambda(newStatement.type, newStatement)
								}
							}
							fullPreamble += newStatement.transformStatement(variableRenamer)
						}
					}
				}
			}
			
			outerBlock.statements.addAll(fullPreamble)
			outerBlock.statements.add(forLoopBlock)
			
			// can add the epilogue directly to the body since only one terminal operation can exist, so there's no chance of variable naming conflicts
			// Also, no state getters/setters
			val typeArgRealizer = TypeArgRealizer(terminalOperationChainCall.ir)
			val newlyAddedVars = mutableMapOf<IrVariableSymbol, IrVariableSymbol>()
			val variableRenamer = VariableRenamer(newlyAddedVars)
			for (statement in terminalOperationChainFun.epilogue) {
				val newStatement = statement.deepCopyWithSymbols(currentFunction) { typeArgRealizer }
				if (statement is IrVariable) {
					val newStatement = newStatement as IrVariable
					newStatement.name = Name.identifier("terminal$" + statement.name)
					
					newlyAddedVars[statement.symbol] = newStatement.symbol
				}
				
				outerBlock.statements += newStatement.transformStatement(variableRenamer)
			}
			return outerBlock;
		}
		
		class TypeArgRealizer(private val chainCall: IrCall) : AbstractTypeRemapper() {
			override fun remapTypeOrNull(type: IrType): IrType? {
				if (type !is IrSimpleType)
					return null;
				
				(type.classifierOrNull as? IrTypeParameterSymbol)?.let {
					return chainCall.typeArguments[it.owner.index]
				}
				
				val newArgs = remapTypeArguments(type.arguments) ?: type.arguments
				return IrSimpleTypeImpl(type.classifier, type.nullability, newArgs, type.annotations)
			}
		}
	}
	
	
	class VariableRenamer(val oldVarsToNewVarsMap: Map<IrVariableSymbol, IrVariableSymbol>) : IrElementTransformerVoid() {
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
inline fun makeLambdaFunction(name: Name, rType: IrType = ctx.context.irBuiltIns.unitType, onNewlyCreatedFunction: IrFunction.() -> Unit = {}): IrSimpleFunction {
	return ctx.context.irFactory.buildFun {
		returnType = rType
		this.name = name
		visibility = DescriptorVisibilities.LOCAL
		modality = Modality.FINAL
		origin = LOCAL_FUNCTION_FOR_LAMBDA
	}.apply(onNewlyCreatedFunction)
}

context(ctx: StreamOpContext)
fun makeGetterLambda(varType: IrType, targetVariable: IrVariable): IrExpression {
	return IrFunctionExpressionImpl(-1, -1, ctx.context.irBuiltIns.functionN(0).typeWith(varType), makeLambdaFunction(Name.special("<getter-${targetVariable.name.asString()}>"), varType) {
		body = ctx.context.irFactory.createExpressionBody(-1, -1, IrGetValueImpl(-1, -1, targetVariable.symbol))
	}, LAMBDA)
}

context(ctx: StreamOpContext)
fun makeSetterLambda(varType: IrType, targetVariable: IrVariable): IrExpression {
	return IrFunctionExpressionImpl(-1, -1, ctx.context.irBuiltIns.functionN(1).typeWith(varType), makeLambdaFunction(Name.special("<setter-${targetVariable.name.asString()}>")) {
		val value = addValueParameter(name = StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME, type = varType)
		body = ctx.context.irFactory.createExpressionBody(-1, -1, IrSetValueImpl(-1, -1, targetVariable.type, targetVariable.symbol, IrGetValueImpl(-1, -1, value.symbol), null))
	}, LAMBDA)
}

