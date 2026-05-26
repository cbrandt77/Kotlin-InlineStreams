package io.github.cbrandt77.kt.inlinestreams.ir.utils

import io.github.cbrandt77.kt.inlinestreams.NamesAndIds.IntermediateOperations.NAME_element
import io.github.cbrandt77.kt.inlinestreams.ir.StreamOpContext
import io.github.cbrandt77.kt.inlinestreams.ir.types.calls.IntermediateOperationChainCall
import io.github.cbrandt77.kt.inlinestreams.ir.types.calls.StreamOperationChainCall
import io.github.cbrandt77.kt.inlinestreams.ir.types.calls.TerminalOperationChainCall
import io.github.cbrandt77.kt.inlinestreams.ir.types.declarations.chain.IntermediateOperationChainFunction
import io.github.cbrandt77.kt.inlinestreams.ir.types.declarations.chain.StreamOperationChainFunction
import io.github.cbrandt77.kt.inlinestreams.ir.types.declarations.chain.TerminalOperationChainFunction
import io.github.cbrandt77.kt.inlinestreams.ir.types.declarations.chain.evaluateValue
import io.github.cbrandt77.kt.inlinestreams.ir.types.declarations.loop.IntermediateOperationLoopFunction
import io.github.cbrandt77.kt.inlinestreams.ir.types.declarations.loop.StreamOperationLoopFunction
import io.github.cbrandt77.kt.inlinestreams.ir.types.declarations.loop.TerminalOperationLoopFunction
import io.github.cbrandt77.kt.inlinestreams.ir.visitors.LoopCreator
import io.github.cbrandt77.kt.inlinestreams.ir.visitors.LoopCreator.Companion.INLINE_STREAM_OPERATION
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.declarations.impl.IrVariableImpl
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin.Companion.LAMBDA
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrSetValueImpl
import org.jetbrains.kotlin.ir.symbols.IrValueSymbol
import org.jetbrains.kotlin.ir.symbols.IrVariableSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrVariableSymbolImpl
import org.jetbrains.kotlin.ir.transformStatement
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.irError
import org.jetbrains.kotlin.ir.util.isFunctionOrKFunction
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.utils.addToStdlib.butIf
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract


/**
 * Operations to convert a chain of DSL-functions (e.g. `stream.map(...).flatMap(...).firstOrNull()`)
 * to nested loop-action functions (`map(el) { flatMap(it) { firstOrNull(it) } })
 */
object LoopTranspositionUtils {
	/**
	 * Gets all statements for the current operation that should be run outside the loop.
	 *
	 * Any arguments to the function that aren't "inline functors" are saved to variables in the preamble, named `<operationIndex>$arg$<operationName>$<variableName>`
	 * Any variables declared in the preamble are renamed to `<operationIndex>$<operationName>$<variableName>` to avoid conflicts.
	 */
	context(ctx: StreamOpContext)
	fun OperationWithState.createPreambleForLoop(operationIndex: Int, parentScope: IrSimpleFunction): Sequence<IrStatement> = with (ctx) {
		val typeArgRealizer = LoopCreator.TypeArgRealizer(chainCall_general.ir)
		val stateRedirector = LoopCreator.StateVariableReferenceRedirector(stateVariablesMap)
		
		return chainFunc_general.run {
			parameters.butIf(ir.isInline) { params -> params.filterNot { it.type.isFunctionOrKFunction() && !it.isNoinline && !it.isCrossinline } }
		}.map { param ->
			IrVariableImpl(
				startOffset = -1, endOffset = -1,
				origin = IrDeclarationOrigin.DEFINED,
				symbol = IrVariableSymbolImpl(),
				name = makeParameterName(operationIndex, chainFunc_general.ir.name, param.name),
				type = param.type.let(typeArgRealizer::remapType),
				isVar = false,
				isConst = false,
				isLateinit = false
			).apply {
				parent = parentScope
				initializer = param.evaluateValue(chainCall_general.ir).deepCopyWithSymbols(parentScope) { typeArgRealizer }
				
				stateVariablesMap[param.symbol] = symbol
			}
		} + preamble.map { preambleStatement ->
			preambleStatement.deepCopyWithSymbols(parentScope) { typeArgRealizer }
				.apply {
					if (this is IrVariable) {
						val preambleStatement = preambleStatement as IrVariable
						name = makeStateVariableName(operationIndex, chainFunc_general.ir.name, preambleStatement.name)
						stateVariablesMap[preambleStatement.symbol] = symbol
					}
				}
				.transformStatement(stateRedirector)
		};
	}
	
	/**
	 * Get the statements to be run after the loop finishes.
	 *
	 * Changes variable names to `terminal$<name>` (and adds it to the operation's [TerminalOperationWithState.stateVariablesMap],
	 * and changes any return statements to return@ [loopReturnTarget].
	 */
	context(ctx: StreamOpContext)
	fun TerminalOperationWithState.createEpilogueStatements(variableParent: IrFunction, loopReturnTarget: IrSimpleFunction): Sequence<IrStatement> {
		val typeArgRealizer = LoopCreator.TypeArgRealizer(chainCall.ir)
		val variableRedirector = LoopCreator.StateVariableReferenceRedirector(stateVariablesMap)
		return chainFunction.epilogue.map {
			it.deepCopyWithSymbols(variableParent) { typeArgRealizer }
				.apply {
					if (this is IrVariable) {
						name = Name.identifier("terminal$" + name.asStringStripSpecialMarkers())
						stateVariablesMap[(it as IrVariable).symbol] = symbol
					} else if (this is IrReturn) {
						returnTargetSymbol = loopReturnTarget.symbol
					}
				}
				.transformStatement(variableRedirector)
		}
	}
	
	/**
	 * Make the name for a variable in the preamble where a DSL function's parameter should be saved.
	 */
	fun makeParameterName(operationIndex: Int, chainFunName: Name, paramName: Name): Name {
		return Name.identifier("${operationIndex}\$arg$${chainFunName.asStringStripSpecialMarkers()}$${paramName.asStringStripSpecialMarkers()}")
	}
	
	/**
	 * Make the name for a variable in the preamble originally defined inside a DSL function.
	 */
	fun makeStateVariableName(operationIndex: Int, chainFunName: Name, varName: Name): Name {
		return Name.identifier("${operationIndex}$${chainFunName.asStringStripSpecialMarkers()}$${varName.asStringStripSpecialMarkers()}")
	}
	
	/**
	 * Helper to create a lambda function with the name `<anonymous>` and a return type [rType].
	 */
	fun StreamOpContext.makeLambdaFunction(rType: IrType = irBuiltIns.unitType): IrSimpleFunction {
		return irFactory.buildFun {
			returnType = rType
			name = anonymous
			visibility = DescriptorVisibilities.LOCAL
			modality = Modality.FINAL
			origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
		}
	}
	
	/**
	 * Make a lambda that returns [this@makeGetterLambda]: `() -> targetVariable`
	 */
	context(ctx: StreamOpContext)
	fun IrVariable.makeGetterLambda(varType: IrType, parent: IrDeclarationParent): IrExpression {
		return IrFunctionExpressionImpl(
			-1, -1,
			ctx.irBuiltIns.functionN(0).typeWith(varType),
			ctx.makeLambdaFunction(varType)
				.apply {
				body = ctx.irFactory.createBlockBody(-1, -1).also { it.statements += IrReturnImpl(-1, -1, ctx.irBuiltIns.nothingType, symbol, IrGetValueImpl(-1, -1, this@makeGetterLambda.symbol)) }
				this.parent = parent
			},
			LAMBDA
		)
	}
	
	context(ctx: StreamOpContext)
	fun IrVariable.makeSetterLambda(varType: IrType, parent: IrDeclarationParent): IrExpression {
		return IrFunctionExpressionImpl(
			-1, -1,
			ctx.irBuiltIns.functionN(1).typeWith(varType, ctx.unitType),
			ctx.makeLambdaFunction()
				.apply {
					val value = addValueParameter(name = StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME, type = varType)
					body = ctx.irFactory.createBlockBody(-1, -1).also {
							it.statements += IrSetValueImpl(-1, -1, ctx.unitType, this@makeSetterLambda.symbol, IrGetValueImpl(-1, -1, value.symbol), null)
						}
					this.parent = parent
				},
			LAMBDA
		)
	}
	
	sealed class OperationWithState(
		/**
		 * Map of "variables declared inside the dsl function" to "their new location outside the loop"
		 */
		val stateVariablesMap: MutableMap<IrValueSymbol, IrVariableSymbol> = mutableMapOf()
	) {
		operator fun component4() = stateVariablesMap
		
		val isTerminal get() = this is TerminalOperationWithState
		
		context(_: StreamOpContext)
		val preamble get() = when (this) {
			is TerminalOperationWithState -> chainFunction.preamble
			is IntermediateOperationWithState -> chainFunction.preamble
		}
		
		val chainCall_general: StreamOperationChainCall get() = when (this) {
			is IntermediateOperationWithState -> chainCall.internal
			is TerminalOperationWithState -> chainCall.internal
		}
		
		val chainFunc_general: StreamOperationChainFunction get() = when (this) {
			is IntermediateOperationWithState -> chainFunction.internal
			is TerminalOperationWithState -> chainFunction.internal
		}
		
		val loopFunc_general: StreamOperationLoopFunction get() = when (this) {
			is IntermediateOperationWithState -> loopFunction.internal
			is TerminalOperationWithState -> loopFunction.internal
		}
	}
	
	class IntermediateOperationWithState(
		val chainCall: IntermediateOperationChainCall,
		val chainFunction: IntermediateOperationChainFunction,
		val loopFunction: IntermediateOperationLoopFunction,
	) : OperationWithState() {
		operator fun component1() = chainCall
		operator fun component2() = chainFunction
		operator fun component3() = loopFunction
	}
	class TerminalOperationWithState(
		val chainCall: TerminalOperationChainCall,
		val chainFunction: TerminalOperationChainFunction,
		val loopFunction: TerminalOperationLoopFunction,
	) : OperationWithState() {
		operator fun component1() = chainCall
		operator fun component2() = chainFunction
		operator fun component3() = loopFunction
	}
	
	
	/**
	 * Given a list of [IntermediateOperationWithState]s ending with a [TerminalOperationWithState], such as `[flatMap, map, drop, firstOrNull]`, nest them like this:
	 * ```kotlin
	 * flatMap(item, { return@loop }, ...) { el ->
	 *   map(el, { return@loop }, ...) { el2 ->
	 *      drop(el2, { return@loop }, ...) { el3 ->
	 *          firstOrNull(el3, { return@loop })
	 *      }
	 *   }
	 * }
	 * ```
	 *
	 * @param parentScope The function containing this loop call. Used for function parenting.
	 * @param parentElSymbol The symbol representing the "element" in the iterator that the first function should receive.  e.g. `for (el in ...)`
	 * @param operations An iterator over a list of operations: `[(...Intermediate), Terminal]
	 * @param makeBreakLoopArg Factory for a `() -> Unit` lambda that'll break the loop. See [io.github.cbrandt77.kt.inlinestreams.ir.visitors.LoopCreator.FunctionChecker.remappedInlineStream]
	 */
	@OptIn(ExperimentalContracts::class)
	tailrec fun StreamOpContext.callLoopOperationRecursive(parentScope: IrFunction, parentElSymbol: IrValueSymbol, operations: Iterator<OperationWithState>, makeBreakLoopArg: (IrDeclarationParent) -> IrExpression, commitStatement: (IrStatement) -> Unit) {
		contract {
			callsInPlace(commitStatement, InvocationKind.EXACTLY_ONCE)
		}
		
		val operation = operations.next()
		
		val chainCallGeneral = operation.chainCall_general
		val chainFunGeneral = operation.chainFunc_general
		val loopFunGeneral = operation.loopFunc_general
		
		val loopActionCall = IrCallImpl(chainCallGeneral.ir.startOffset, chainCallGeneral.ir.endOffset, unitType, loopFunGeneral.ir.symbol, origin=INLINE_STREAM_OPERATION).apply {
			assert(loopFunGeneral.ir.typeParameters.size == chainFunGeneral.ir.typeParameters.size) { "Expected generated loop-action function to have exact same type parameters as the original chain function." }
			
			chainCallGeneral.ir.typeArguments.forEachIndexed { index, type ->
				typeArguments[index] = type
			}
		}
		
		
		var ourDownstreamActionLambdaElementSymbol: IrValueSymbol? = null
		var ourDownstreamActionLambda: IrFunction? = null
		val downstreamActionArg = when (operation) {
			is IntermediateOperationWithState -> {
				val uType = operation.chainCall.outputElementType
				val downstreamActionLambda = makeLambdaFunction().apply {
					parent = parentScope
					ourDownstreamActionLambdaElementSymbol = addValueParameter(NAME_element, uType).symbol
				}
				ourDownstreamActionLambda = downstreamActionLambda
				sequenceOf(IrFunctionExpressionImpl(-1, -1, irBuiltIns.functionN(1).typeWith(uType, unitType), downstreamActionLambda, LAMBDA))
			}
			else -> emptySequence()
		}
		
		// add parameters: [...chainFunArgs], el, breakloop, (downstreamAction), [...state functions]
		var numArgsBeforeState = 0
		sequenceOf(
			chainFunGeneral.parameters.map { param ->
				operation.stateVariablesMap[param.symbol]?.let { IrGetValueImpl(-1, -1, it) } // non-inline parameters get remapped to variables outside the loop
				?: chainCallGeneral.ir.arguments[param.indexInParameters]?.deepCopyWithSymbols(parentScope) // inline function parameters are just transplanted raw
			},
			sequenceOf(
				IrGetValueImpl(-1, -1, parentElSymbol),
				makeBreakLoopArg(parentScope)
			),
			downstreamActionArg
		).flatten().forEachIndexed { index, expression ->
			++numArgsBeforeState
			loopActionCall.arguments[index] = expression
		}
		
		
		val `needs -etters` = loopFunGeneral.parameters.drop(numArgsBeforeState).toList()
		if (`needs -etters`.isNotEmpty()) {
			val currentVariablesByName: Map<String, IrVariableSymbol> = operation.stateVariablesMap.mapKeys {
				it.key.owner.name.asString()
			}
			
			`needs -etters`.forEach { param ->
				param.name.nameThisIsGetterFor?.let {
					val correspondingVar = currentVariablesByName[it] ?: irError("No variable found for requested getter.") {
						withIrEntry("loop_function", loopFunGeneral.ir)
						withIrEntry("getter_parameter", param)
					}
					loopActionCall.arguments[param] = correspondingVar.owner.makeGetterLambda(correspondingVar.owner.type, parentScope)
				} ?: param.name.nameThisIsSetterFor?.let {
					val correspondingVar = currentVariablesByName[it]
					                       ?: irError("No variable found for requested setter.") {
						                       withIrEntry("loop_function", loopFunGeneral.ir)
						                       withIrEntry("setter_parameter", param)
					                       }
					loopActionCall.arguments[param] = correspondingVar.owner.makeSetterLambda(correspondingVar.owner.type, parentScope)
				} ?: irError("Parameter is not a getter nor setter") {
					withIrEntry("parameter", param)
					withIrEntry("loop_function", loopFunGeneral.ir)
				}
			}
		}
		
		commitStatement(loopActionCall)
		
		if (operations.hasNext())
			callLoopOperationRecursive(ourDownstreamActionLambda!!, ourDownstreamActionLambdaElementSymbol!!, operations, makeBreakLoopArg) { statement ->
				ourDownstreamActionLambda.body = irFactory.createBlockBody(-1, -1).also {
					it.statements += statement
				}
			}
	}
	
}

