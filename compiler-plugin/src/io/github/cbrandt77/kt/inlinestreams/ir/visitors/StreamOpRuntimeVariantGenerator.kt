package io.github.cbrandt77.kt.inlinestreams.ir.visitors

import io.github.cbrandt77.kt.inlinestreams.NamesAndIds
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.moveBodyTo
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.declarations.addTypeParameter
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.declarations.DelicateIrParameterIndexSetter
import org.jetbrains.kotlin.ir.declarations.IrDeclarationContainer
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrTypeParameter
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrSetValue
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.IrValueAccessExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.symbols.IrValueParameterSymbol
import org.jetbrains.kotlin.ir.symbols.IrVariableSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.callableId
import org.jetbrains.kotlin.ir.util.copyParametersFrom
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.invokeFun
import org.jetbrains.kotlin.ir.util.isTopLevel
import org.jetbrains.kotlin.ir.util.parents
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.Name

object StreamOpGeneratedLoopActionDeclarationKey : GeneratedDeclarationKey() {
	val origin = IrDeclarationOrigin.GeneratedByPlugin(this)
}

/**
 * From the `fun InlineStream<T>.someOperation` function the user writes,
 * generate the `inline fun someOperation(el: T, downstream: () -> Unit): Unit`
 * that actually gets called at runtime.
 */
class StreamOpRuntimeVariantGenerator(
	val context: IrPluginContext,
	/**
	 * Map of `InlineStream.definedOperation` to `definedOperation_runtime`
	 */
	val generatedFunctions: MutableMap<IrFunction, IrFunction>
) : IrElementTransformerVoid() {
	val inlineStreamClassSymbol by lazy {
		context.referenceClass(NamesAndIds.InlineStreamClassId)
	}
	
	override fun visitElement(element: IrElement): IrElement {
		element.transformChildrenVoid(this)
		return element;
	}
	
	override fun visitFunction(declaration: IrFunction): IrStatement {
		if (declaration.actsOnInlineStream)
			FunctionVisitor().visitFunction(declaration)
		
		return super.visitFunction(declaration)
	}
	
	private val IrFunction.actsOnInlineStream: Boolean
		get() = parameters.firstOrNull { it.kind == IrParameterKind.ExtensionReceiver }?.type?.classOrNull == inlineStreamClassSymbol
	
	/**
	 * Visits `InlineStream.something` functions
	 */
	inner class FunctionVisitor : IrVisitorVoid() {
		lateinit var func: IrFunction
		
		override fun visitFunction(declaration: IrFunction) {
			func = declaration
			
			for (statement in declaration.body?.statements ?: TODO("custom error message")) {
				val callsFunction = statement.let {
					when (it) {
						is IrReturn -> it.value
						else -> it
					}
				} as? IrCall
	                    ?: continue;
				
				val generatedFunction = when {
					callsFunction.isIntermediateOpBlock -> IntermediateOperationDefinition(context, declaration).visitIntermediateOperationBlock()
					callsFunction.isTerminalOpBlock -> visitTerminalOperationBlock(callsFunction)
					else -> continue;
				}
				
				generatedFunctions[declaration] = generatedFunction
				
				break;
			}
		}
		
		
		fun IntermediateOperationDefinition.visitIntermediateOperationBlock(): IrFunction {
			return this.makeLoopFunction()
		}
		
		fun visitTerminalOperationBlock(statement: IrCall): IrFunction {
			TODO()
		}
	}
}

class IntermediateOperationDefinition(val context: IrPluginContext, val dslFunction: IrFunction) {
	val loopAction: IntermediateOperationAction
	
	init {
		require(dslFunction.isTopLevel) { "Stream operations must be top level." }
		requireNotNull(dslFunction.body) { "Must have body" }
		var tempLoopAction: IntermediateOperationAction? = null
		
		dslFunction.body!!.statements.forEach {
			if (it is IrReturn) {
				val value = it.value
				require(value is IrCall && value.isIntermediateOpBlock) { "Intermediate operations must return their `intermediateOperation` call." }
				require(tempLoopAction == null) { "Intermediate operations must only have one return statement." }
				tempLoopAction = Call_intermediateOperation(it.value as IrCall).action
			}
		}
		
		loopAction = tempLoopAction!!
	}
	
	/**
	 * Create the inline function to be executed within the loop
	 * - Replace GET_VAR variableDeclaredOutsideBlock with call to getter from parameters
	 *   - Add getter to parameters also
	 * - Replace SET_VAR variableDeclaredOutsideBlock with call to setter from parameters
	 *   - Add setter to parameters also
	 */
	fun makeLoopFunction(): IrFunction {
		return LoopFunctionGenerator().buildLoopActionFun()
	}
	
	inner class LoopFunctionGenerator {
		private val newFun = context.irFactory.buildFun {
			startOffset = loopAction.ir.startOffset
			endOffset = loopAction.ir.endOffset
			name = Name.identifier("${dslFunction.name.identifier}_loopOperation") // TODO generate proper name
			returnType = context.irBuiltIns.unitType
			isInline = true
//			originalDeclaration = dslFunction
			origin = StreamOpGeneratedLoopActionDeclarationKey.origin
		}.apply {
			body = loopAction.ir.body?.deepCopyWithSymbols(this)
		}
		
		
		
		inner class ElementTransformer : IrElementTransformerVoid() {
			private val IrVariable.isDeclaredOutsideLoopAction: Boolean
				get() = newFun !in this.parents // TODO figure out how we do this, I think we might need to do a custom copier
			
			private val IrValueAccessExpression.takeTargetIfNeedsRemapping: IrVariable?
				get() = (symbol as? IrVariableSymbol)
					?.owner
					?.takeIf { it.isDeclaredOutsideLoopAction }
			
			
			
			private val getters = object {
				private val functions: Object2ObjectOpenHashMap<IrVariable, IrValueParameter> = Object2ObjectOpenHashMap()
				
				private val getterType by lazy {
					context.irBuiltIns.functionN(0)
				}
				private val getterInvokeFun by lazy {
					getterType.invokeFun!!.symbol
				}
				
				private fun getOrCreateGetterParameter(variable: IrVariable): IrValueParameter {
					return functions.computeIfAbsent(variable) {
						newFun.addValueParameter {
							name = Name.identifier("get-${variable.name.identifier}")
							type = getterType.typeWith(variable.type)
							origin = StreamOpGeneratedLoopActionDeclarationKey.origin
						}
					}
				}
				
				fun remapToGetterIfNeeded(value: IrGetValue): IrExpression {
					val targetVariable = value.takeTargetIfNeedsRemapping
					                     ?: return value;
					
					return IrCallImpl(value.startOffset, value.endOffset, value.type, getterInvokeFun).also {
						it.dispatchReceiver = IrGetValueImpl(value.startOffset, value.endOffset, getOrCreateGetterParameter(targetVariable).symbol, IrStatementOrigin.VARIABLE_AS_FUNCTION)
					}
				}
			}
			
			private val setters = object {
				private val functions = Object2ObjectOpenHashMap<IrVariable, IrValueParameter>()
				
				private val setterType by lazy {
					context.irBuiltIns.functionN(1)
				}
				private val setterInvokeFun by lazy {
					setterType.invokeFun!!.symbol
				}
				
				private fun getOrCreateParameter(variable: IrVariable): IrValueParameter {
					return functions.computeIfAbsent(variable) { it: IrVariable ->
						newFun.addValueParameter {
							name = Name.identifier("set-${it.name.identifier}")
							type = setterType.typeWith(it.type, context.irBuiltIns.unitType)
							origin = StreamOpGeneratedLoopActionDeclarationKey.origin
						}
					}
				}
				
				/** If [value] sets a variable declared outside the loopAction, remap it to a call to a setter parameter. */
				fun remapToSetterIfNeeded(value: IrSetValue): IrExpression {
					val variableBeingSet = value.takeTargetIfNeedsRemapping ?: return value;
					
					return IrCallImpl(value.startOffset, value.endOffset, context.irBuiltIns.unitType, setterInvokeFun, origin = IrStatementOrigin.INVOKE).also {
						it.dispatchReceiver = IrGetValueImpl(value.startOffset, value.endOffset, getOrCreateParameter(variableBeingSet).symbol, origin = IrStatementOrigin.VARIABLE_AS_FUNCTION) // TODO replace with arguments.add
						it.arguments += value.value
					};
				}
			}
			
			@OptIn(DelicateIrParameterIndexSetter::class)
			private val parameters = object {
				private val oldFuncToNewFuncMap: Map<IrValueParameterSymbol, IrValueParameterSymbol> = (loopAction.ir.parameters + dslFunction.parameters).filter { it.kind != IrParameterKind.ExtensionReceiver }.map { it.symbol }.associateWith {
					newFun.addValueParameter {
						updateFrom(it.owner)
						name = it.owner.name
					}.symbol
				}
				
				fun remap(parameter: IrValueParameterSymbol): IrValueParameterSymbol {
					return oldFuncToNewFuncMap[parameter]
					       ?: TODO()
				}
			}
			
			// as we encounter type parameters, add them to this function
			private val typeParameters = object {
				private val added = Object2ObjectOpenHashMap<IrTypeParameterSymbol, IrTypeParameterSymbol>()
				
				private fun getOrCreateTypeParam(fromDslFunc: IrTypeParameterSymbol): IrTypeParameterSymbol {
					return added.computeIfAbsent(fromDslFunc) { it: IrTypeParameterSymbol ->
						newFun.addTypeParameter {
							updateFrom(it.owner)
							name = it.owner.name
						}.symbol
					}
				}
				
				fun remap(parameter: IrTypeParameterSymbol): IrTypeParameterSymbol {
					return getOrCreateTypeParam(parameter)
				}
			}
			
			override fun visitGetValue(expression: IrGetValue): IrExpression {
				return when (val target = expression.symbol) {
					is IrValueParameterSymbol -> {
						expression.symbol = parameters.remap(target)
						expression
					}
					is IrVariableSymbol -> getters.remapToGetterIfNeeded(expression)
				}
			}
			
			override fun visitSetValue(expression: IrSetValue): IrExpression {
				return setters.remapToSetterIfNeeded(expression)
			}
			
			override fun visitTypeParameter(declaration: IrTypeParameter): IrStatement {
				return typeParameters.remap(declaration.symbol).owner
			}
		}
		
		
		fun buildLoopActionFun(): IrSimpleFunction {
			(dslFunction.parent as IrDeclarationContainer).also {
				it.declarations += newFun
				newFun.parent = it
			}
			
			newFun.body?.transformChildrenVoid(ElementTransformer())
			
			return newFun;
		}
	}
	
	
	/** The type of the stream it acts on. `InlineStream<T>.doAction` */
	val inputType: IrTypeParameter
		get() = dslFunction.typeParameters[0]
	
	/** The type of the stream it returns. `doAction(): InlineStream<U>` */
	val outputType: IrTypeParameter
		get() = this@IntermediateOperationDefinition.dslFunction.typeParameters[1]
	
	/** External state, preprocessing, etc, happening outside the loop. */
	val preamble: Sequence<IrStatement>
		get() = this@IntermediateOperationDefinition.dslFunction.body!!.statements.asSequence().takeWhile {
			!(it is IrReturn || (it is IrCall && it.isIntermediateOpBlock))
		}
	
	
	class Call_intermediateOperation(val call: IrCall) {
		/** The type of the stream it acts on. `InlineStream<T>.doAction` */
		val inputType: IrType
			get() = call.typeArguments[0]!!
		
		/** The type of the stream it returns. `doAction(): InlineStream<U>` */
		val outputType: IrType
			get() = call.typeArguments[1]!!
		
		/** The thing that should actually be executed inside the loop */
		val action: IntermediateOperationAction
			get() = IntermediateOperationAction((call.arguments[1] as IrFunctionExpression).function)
	}
	
	/**
	 * The lambda passed to `intermediateOperation`,
	 * the action actually being performed within the loop.
	 */
	class IntermediateOperationAction(val ir: IrFunction) {
		val pEl: IrValueParameter
			get() = ir.parameters[0]
		
		val pBreakLoop: IrValueParameter
			get() = ir.parameters[1]
		
		val pDownstreamAction: IrValueParameter
			get() = ir.parameters[2]
		
		val statements: List<IrStatement>
			get() = ir.body!!.statements
	}
}



private val IrCall.isIntermediateOpBlock: Boolean
	get() = this.symbol.owner.callableId == NamesAndIds.intermediateOperation

private val IrCall.isTerminalOpBlock: Boolean
	get() = this.symbol.owner.callableId == NamesAndIds.terminalOperation