/*
 * Copyright (C) 2026 Caleb Brandt
 *
 * This file is part of Inline Streams.
 *
 * Inline Streams is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Inline Streams is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.cbrandt77.kt.inlinestreams.test

import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.template.runners.AbstractJvmDiagnosticTest
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.cfa.AbstractFirPropertyInitializationChecker
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.cfa.FirControlFlowChecker
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.*
import org.jetbrains.kotlin.fir.analysis.checkers.expression.*
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirAnnotationChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.extensions.FirAssignExpressionAltererExtension
import org.jetbrains.kotlin.fir.extensions.FirExtension
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.visitors.IrVisitor
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.directives.CodegenTestDirectives
import org.jetbrains.kotlin.test.directives.FirDiagnosticsDirectives
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.KotlinTestInfo
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.utils.addToStdlib.UnsafeCastFunction
import org.jetbrains.kotlin.utils.addToStdlib.cast
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import java.lang.reflect.Method
import java.util.Arrays
import java.util.stream.Stream
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.reflect.KClass

/*
We want to peek on the FIR at a certain point, to ensure each method is actually doing what it's supposed to
So at that point, we're basically doing this:
	- Each test method is annotated with `@TestData`
	- Code to be run is given as a string?
	- Code is pasted into a box-test kt file and executed by runTest
	- Each test function makes its own test class class with a
	- Checker needs to catch test exceptions and print them? Or how do we hook into IJ's test preview
 */

class TestDeclarationChecker<T : FirDeclaration>(val tester: (decl: T, session: FirSession) -> Unit) : FirDeclarationChecker<T>(MppCheckerKind.Common) {
	context(context: CheckerContext, reporter: DiagnosticReporter)
	override fun check(declaration: T) {
		tester(declaration, context.session)
	}
}

fun <T : FirDeclaration> checkDeclaration(action: (decl: T, session: FirSession) -> Unit): FirDeclarationChecker<T> {
	return TestDeclarationChecker(action)
}


class TestExpressionChecker<T : FirExpression>(val action: (expr: T, session: FirSession) -> Unit) : FirExpressionChecker<T>(MppCheckerKind.Common) {
	context(context: CheckerContext, reporter: DiagnosticReporter)
	override fun check(expression: T) {
		action(expression, context.session)
	}
}

fun <T : FirExpression> checkExpression(action: (expr: T, session: FirSession) -> Unit): FirExpressionChecker<T> {
	return TestExpressionChecker(action)
}


class TestAssignAlterer(session: FirSession, val tester: (assignment: FirVariableAssignment, session: FirSession) -> Unit) : FirAssignExpressionAltererExtension(session) {
	companion object {
		operator fun invoke(tester: (assignment: FirVariableAssignment, session: FirSession) -> Unit) = FirAssignExpressionAltererExtension.Factory { TestAssignAlterer(it, tester) }
	}
	
	override fun transformVariableAssignment(variableAssignment: FirVariableAssignment): FirStatement? {
		tester(variableAssignment, session)
		return null
	}
}

/**
 * Makes a configurable, but trivial, assignment alterer, to check that a function works at that phase of the FIR compilation
 */
fun newAssignmentAlterer(action: (assignment: FirVariableAssignment, session: FirSession) -> Unit): FirExtension.Factory<FirAssignExpressionAltererExtension> {
	return FirAssignExpressionAltererExtension.Factory {
		TestAssignAlterer(it, action)
	}
}

open class IrTestVisitor : IrVisitor<Unit, IrPluginContext>() {
	override fun visitElement(element: IrElement, data: IrPluginContext) {
		element.acceptChildren(this, data)
	}
}

/**
 * Executes all tests that take a single [TestData] parameter (usually a receiver)
 */
abstract class FirUnitTester {
	/**
	 * Additional configuration for the test environment, including adding features, etc
	 */
	open fun configureEnv(builder: TestConfigurationBuilder) {}
	
	private class JvmUnitTest(val testData: TestData, val additionalEnvConfiguration: (TestConfigurationBuilder) -> Unit) : AbstractJvmDiagnosticTest() {
		companion object {
			const val BASE_RESOLVE = $$"$PROJECT_ROOT"
			
			const val BASE_FILE_PATH = "compiler-plugin/testData/generated/unittests"
			
		}
		
		init {
			super.initTestInfo(KotlinTestInfo(testData.owningClass.name, testData.methodName, emptySet()))
		}
		
		private class UnitTestEnvConfigurator(testServices: TestServices, val firExtension: FirExtensionRegistrar, val irExtension: IrGenerationExtension) : EnvironmentConfigurator(testServices) {
			override fun CompilerPluginRegistrar.ExtensionStorage.registerCompilerExtensions(module: TestModule, configuration: CompilerConfiguration) {
				FirExtensionRegistrarAdapter.registerExtension(firExtension)
				IrGenerationExtension.registerExtension(irExtension)
			}
		}
		
		val filePath = kotlin.io.path.Path(BASE_FILE_PATH, testData.owningClass.name.replace('.', File.separatorChar), testData.methodName + ".kt")
		
		
		
		fun genKtFile() {
			if (!filePath.exists()) {
				filePath.createParentDirectories()
				filePath.createFile()
			}
			filePath.writeText(testData.code.trimIndent())
		}
		
		fun runTest() {
			genKtFile()
			runTest(filePath.toString())
		}
		
		override fun configure(builder: TestConfigurationBuilder) {
			super.configure(builder)
			builder.defaultDirectives {
				-FirDiagnosticsDirectives.FIR_DUMP
				
				if (testData.disableFirDiagnostics) {
					+CodegenTestDirectives.IGNORE_FIR_DIAGNOSTICS
				}
			}
			
			additionalEnvConfiguration(builder)
			val (fir, ir) = testData.build()
			builder.useConfigurators({ services -> UnitTestEnvConfigurator(services, fir, ir) })
		}
	}
	
	@Target(AnnotationTarget.FUNCTION)
	annotation class FirTest
	
	class TestData(val owningClass: Class<*>, val methodName: String) {
		class MutableDeclarationCheckers : DeclarationCheckers() {
			override val anonymousFunctionCheckers: MutableSet<FirAnonymousFunctionChecker> = mutableSetOf()
			override val anonymousInitializerCheckers: MutableSet<FirAnonymousInitializerChecker> = mutableSetOf()
			override val anonymousObjectCheckers: MutableSet<FirAnonymousObjectChecker> = mutableSetOf()
			override val backingFieldCheckers: MutableSet<FirBackingFieldChecker> = mutableSetOf()
			override val basicDeclarationCheckers: MutableSet<FirBasicDeclarationChecker> = mutableSetOf()
			override val callableDeclarationCheckers: MutableSet<FirCallableDeclarationChecker> = mutableSetOf()
			override val classCheckers: MutableSet<FirClassChecker> = mutableSetOf()
			override val classLikeCheckers: MutableSet<FirClassLikeChecker> = mutableSetOf()
			override val constructorCheckers: MutableSet<FirConstructorChecker> = mutableSetOf()
			override val controlFlowAnalyserCheckers: MutableSet<FirControlFlowChecker> = mutableSetOf()
			override val enumEntryCheckers: MutableSet<FirEnumEntryChecker> = mutableSetOf()
			override val fileCheckers: MutableSet<FirFileChecker> = mutableSetOf()
			override val functionCheckers: MutableSet<FirFunctionChecker> = mutableSetOf()
			override val propertyAccessorCheckers: MutableSet<FirPropertyAccessorChecker> = mutableSetOf()
			override val propertyCheckers: MutableSet<FirPropertyChecker> = mutableSetOf()
			override val receiverParameterCheckers: MutableSet<FirReceiverParameterChecker> = mutableSetOf()
			override val regularClassCheckers: MutableSet<FirRegularClassChecker> = mutableSetOf()
			override val replSnippetCheckers: MutableSet<FirReplSnippetChecker> = mutableSetOf()
			override val scriptCheckers: MutableSet<FirScriptChecker> = mutableSetOf()
			override val simpleFunctionCheckers: MutableSet<FirSimpleFunctionChecker> = mutableSetOf()
			override val typeAliasCheckers: MutableSet<FirTypeAliasChecker> = mutableSetOf()
			override val typeParameterCheckers: MutableSet<FirTypeParameterChecker> = mutableSetOf()
			override val valueParameterCheckers: MutableSet<FirValueParameterChecker> = mutableSetOf()
			override val variableAssignmentCfaBasedCheckers: MutableSet<AbstractFirPropertyInitializationChecker> = mutableSetOf()
			
			inline operator fun <reified T : FirDeclaration> plusAssign(checker: FirDeclarationChecker<T>) {
				addChecker(T::class.java, checker)
			}
			
			operator fun plusAssign(checker: FirControlFlowChecker) {
				controlFlowAnalyserCheckers += checker
			}
			
			operator fun plusAssign(checker: AbstractFirPropertyInitializationChecker) {
				variableAssignmentCfaBasedCheckers += checker
			}
			
			@OptIn(UnsafeCastFunction::class)
			fun <T : FirDeclaration> addChecker(klass: Class<T>, checker: FirDeclarationChecker<T>) {
				when (klass) {
					FirAnonymousFunction::class.java -> anonymousFunctionCheckers
					FirAnonymousInitializer::class.java -> anonymousInitializerCheckers
					FirAnonymousObject::class.java -> anonymousObjectCheckers
					FirBackingField::class.java -> backingFieldCheckers
					FirDeclaration::class.java -> basicDeclarationCheckers
					FirCallableDeclaration::class.java -> callableDeclarationCheckers
					FirClass::class.java -> classCheckers
					FirClassLikeDeclaration::class.java -> classLikeCheckers
					FirConstructor::class.java -> constructorCheckers
					FirEnumEntry::class.java -> enumEntryCheckers
					FirFunction::class.java -> functionCheckers
					FirPropertyAccessor::class.java -> propertyAccessorCheckers
					FirReceiverParameter::class.java -> receiverParameterCheckers
					FirRegularClass::class.java -> regularClassCheckers
					FirReplSnippet::class.java -> replSnippetCheckers
					FirScript::class.java -> scriptCheckers
					FirSimpleFunction::class.java -> simpleFunctionCheckers
					FirTypeAlias::class.java -> typeAliasCheckers
					FirTypeParameter::class.java -> typeParameterCheckers
					FirValueParameter::class.java -> valueParameterCheckers
					else -> error("No declaration checker type found for $klass")
				}.cast<MutableSet<FirDeclarationChecker<T>>>().add(checker)
			}
		}
		
		class MutableExpressionCheckers : ExpressionCheckers() {
			override val annotationCallCheckers: MutableSet<FirAnnotationCallChecker> = mutableSetOf()
			override val annotationCheckers: MutableSet<FirAnnotationChecker> = mutableSetOf()
			override val arrayLiteralCheckers: MutableSet<FirArrayLiteralChecker> = mutableSetOf()
			override val basicExpressionCheckers: MutableSet<FirBasicExpressionChecker> = mutableSetOf()
			override val blockCheckers: MutableSet<FirBlockChecker> = mutableSetOf()
			override val booleanOperatorExpressionCheckers: MutableSet<FirBooleanOperatorExpressionChecker> = mutableSetOf()
			override val callableReferenceAccessCheckers: MutableSet<FirCallableReferenceAccessChecker> = mutableSetOf()
			override val callCheckers: MutableSet<FirCallChecker> = mutableSetOf()
			override val checkNotNullCallCheckers: MutableSet<FirCheckNotNullCallChecker> = mutableSetOf()
			override val classReferenceExpressionCheckers: MutableSet<FirClassReferenceExpressionChecker> = mutableSetOf()
			override val doWhileLoopCheckers: MutableSet<FirDoWhileLoopChecker> = mutableSetOf()
			override val elvisExpressionCheckers: MutableSet<FirElvisExpressionChecker> = mutableSetOf()
			override val equalityOperatorCallCheckers: MutableSet<FirEqualityOperatorCallChecker> = mutableSetOf()
			override val functionCallCheckers: MutableSet<FirFunctionCallChecker> = mutableSetOf()
			override val getClassCallCheckers: MutableSet<FirGetClassCallChecker> = mutableSetOf()
			override val inaccessibleReceiverCheckers: MutableSet<FirInaccessibleReceiverChecker> = mutableSetOf()
			override val integerLiteralOperatorCallCheckers: MutableSet<FirIntegerLiteralOperatorCallChecker> = mutableSetOf()
			override val literalExpressionCheckers: MutableSet<FirLiteralExpressionChecker> = mutableSetOf()
			override val loopExpressionCheckers: MutableSet<FirLoopExpressionChecker> = mutableSetOf()
			override val loopJumpCheckers: MutableSet<FirLoopJumpChecker> = mutableSetOf()
			override val propertyAccessExpressionCheckers: MutableSet<FirPropertyAccessExpressionChecker> = mutableSetOf()
			override val qualifiedAccessExpressionCheckers: MutableSet<FirQualifiedAccessExpressionChecker> = mutableSetOf()
			override val resolvedQualifierCheckers: MutableSet<FirResolvedQualifierChecker> = mutableSetOf()
			override val returnExpressionCheckers: MutableSet<FirReturnExpressionChecker> = mutableSetOf()
			override val safeCallExpressionCheckers: MutableSet<FirSafeCallExpressionChecker> = mutableSetOf()
			override val smartCastExpressionCheckers: MutableSet<FirSmartCastExpressionChecker> = mutableSetOf()
			override val stringConcatenationCallCheckers: MutableSet<FirStringConcatenationCallChecker> = mutableSetOf()
			override val superReceiverExpressionCheckers: MutableSet<FirSuperReceiverExpressionChecker> = mutableSetOf()
			override val thisReceiverExpressionCheckers: MutableSet<FirThisReceiverExpressionChecker> = mutableSetOf()
			override val throwExpressionCheckers: MutableSet<FirThrowExpressionChecker> = mutableSetOf()
			override val tryExpressionCheckers: MutableSet<FirTryExpressionChecker> = mutableSetOf()
			override val typeOperatorCallCheckers: MutableSet<FirTypeOperatorCallChecker> = mutableSetOf()
			override val variableAssignmentCheckers: MutableSet<FirVariableAssignmentChecker> = mutableSetOf()
			override val whenExpressionCheckers: MutableSet<FirWhenExpressionChecker> = mutableSetOf()
			override val whileLoopCheckers: MutableSet<FirWhileLoopChecker> = mutableSetOf()
			
			inline operator fun <reified T : FirExpression> plusAssign(checker: FirExpressionChecker<T>) {
				addChecker(T::class.java, checker)
			}
			
			@OptIn(UnsafeCastFunction::class)
			fun <T : FirExpression> addChecker(klass: Class<T>, checker: FirExpressionChecker<T>) {
				when (klass) {
					FirAnnotationCall::class.java -> annotationCallCheckers
					FirAnnotation::class.java -> annotationCheckers
					FirArrayLiteral::class.java -> arrayLiteralCheckers
					FirStatement::class.java -> basicExpressionCheckers
					FirBlock::class.java -> blockCheckers
					FirBooleanOperatorExpression::class.java -> booleanOperatorExpressionCheckers
					FirCallableReferenceAccess::class.java -> callableReferenceAccessCheckers
					FirCall::class.java -> callCheckers
					FirCheckNotNullCall::class.java -> checkNotNullCallCheckers
					FirClassReferenceExpression::class.java -> classReferenceExpressionCheckers
					FirDoWhileLoop::class.java -> doWhileLoopCheckers
					FirElvisExpression::class.java -> elvisExpressionCheckers
					FirEqualityOperatorCall::class.java -> equalityOperatorCallCheckers
					FirFunctionCall::class.java -> functionCallCheckers
					FirGetClassCall::class.java -> getClassCallCheckers
					FirInaccessibleReceiverExpression::class.java -> inaccessibleReceiverCheckers
					FirIntegerLiteralOperatorCall::class.java -> integerLiteralOperatorCallCheckers
					FirLiteralExpression::class.java -> literalExpressionCheckers
					FirLoop::class.java -> loopExpressionCheckers
					FirLoopJump::class.java -> loopJumpCheckers
					FirPropertyAccessExpression::class.java -> propertyAccessExpressionCheckers
					FirQualifiedAccessExpression::class.java -> qualifiedAccessExpressionCheckers
					FirResolvedQualifier::class.java -> resolvedQualifierCheckers
					FirReturnExpression::class.java -> returnExpressionCheckers
					FirSafeCallExpression::class.java -> safeCallExpressionCheckers
					FirSmartCastExpression::class.java -> smartCastExpressionCheckers
					FirStringConcatenationCall::class.java -> stringConcatenationCallCheckers
					FirSuperReceiverExpression::class.java -> superReceiverExpressionCheckers
					FirThisReceiverExpression::class.java -> thisReceiverExpressionCheckers
					FirThrowExpression::class.java -> throwExpressionCheckers
					FirTryExpression::class.java -> tryExpressionCheckers
					FirTypeOperatorCall::class.java -> typeOperatorCallCheckers
					FirVariableAssignment::class.java -> variableAssignmentCheckers
					FirWhenExpression::class.java -> whenExpressionCheckers
					FirWhileLoop::class.java -> whileLoopCheckers
					else -> error("No checker kind found for $klass")
				}.cast<MutableSet<FirExpressionChecker<T>>>().add(checker)
			}
		}
		
		/**
		 * The body of the diagnostic test.
		 */
		@Language("kotlin")
		lateinit var code: String
		
		val declCheckers = MutableDeclarationCheckers()
		
		val exprCheckers = MutableExpressionCheckers()
		
		var disableFirDiagnostics = false
		
		
		/**
		 * Other extensions, so we can make sure methods also work in the context of, for example, a transformer, or an analyzer
		 */
		val otherExtensions: MutableList<Pair<KClass<out FirExtension>, FirExtension.Factory<*>>> = mutableListOf()
		
		val irVisitors: MutableList<IrTestVisitor> = mutableListOf()
		
		inline operator fun <reified T : FirExtension> MutableList<Pair<KClass<out FirExtension>, FirExtension.Factory<*>>>.plusAssign(factory: FirExtension.Factory<T>) {
			otherExtensions.add(T::class to factory)
		}
		
		private fun buildCheckerExtension(): FirAdditionalCheckersExtension.Factory {
			return FirAdditionalCheckersExtension.Factory { session ->
				object : FirAdditionalCheckersExtension(session) {
					override val declarationCheckers: DeclarationCheckers = declCheckers
					override val expressionCheckers: ExpressionCheckers = exprCheckers
				}
			}
		}
		
		companion object {
			private val METHODHANDLE_REGISTEREXTENSION: Method by lazy {
				FirExtensionRegistrar::class.java.getDeclaredMethod("registerExtension", KClass::class.java, FirExtension.Factory::class.java).also {
					it.isAccessible = true
				}
			}
			
			/**
			 * Provides access to the private [FirExtensionRegistrar.registerExtension] method,
			 * allowing us to circumvent the strictly-typed `+IndividualExtensionType.Factory` methods with our single list of extensions.
			 */
			private fun <P : FirExtension> FirExtensionRegistrar._registerExtension(kClass: KClass<out P>, factory: FirExtension.Factory<P>) {
				METHODHANDLE_REGISTEREXTENSION(this, kClass, factory)
			}
		}
		
		fun build(): Pair<FirExtensionRegistrar, IrGenerationExtension> {
			return object : FirExtensionRegistrar() {
				override fun ExtensionRegistrarContext.configurePlugin() {
					+buildCheckerExtension()
					otherExtensions.forEach { (type, factory) ->
						_registerExtension(type, factory)
					}
				}
			} to object : IrGenerationExtension {
				override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
					irVisitors.forEach {
						moduleFragment.acceptChildren(it, pluginContext)
					}
				}
			}
		}
	}
	
	@TestFactory
	fun generateFirTests(): Stream<DynamicTest> {
		val ourInstance = this
		
		val subclass = this::class.java
		if (subclass == FirUnitTester::class.java)
			error("Class wasn't extended.")
		
		val ctor = subclass.getConstructor()
		
		val configureEnv = FirUnitTester::configureEnv
		
		return Arrays.stream(subclass.declaredMethods)
			.filter { it.isAnnotationPresent(FirTest::class.java) }
			.map { firTestMethod ->
				require(firTestMethod.parameterTypes.singleOrNull() == TestData::class.java) { "FirTest methods must take a single TestData parameter (usually a receiver)." }
				DynamicTest.dynamicTest(firTestMethod.name) {
					val testData = TestData(subclass, firTestMethod.name).also {
						firTestMethod.invoke(ctor.newInstance(), it)
					}
					
					val jvmUnitTestRunner = JvmUnitTest(testData) {
						configureEnv.invoke(ourInstance, it)
					}
					jvmUnitTestRunner.genKtFile()
					jvmUnitTestRunner.runTest()
				}
			}
	}
}




/*
class MyTests : FirUnitTester() {
	fun TestData.`does a thing`() {
		code = """
			val x = ""
			var foo = 4
		""".trimIndent()
		
		declCheckers += checkDeclaration<FirFunction> { element, session ->
		
		}
	}
}
*/

/*
inline fun buildClass(api: Int = ASM5, builder: ClassNode.() -> Unit): ClassWriter {
	val node = ClassNode(api).apply(builder)
	return ClassWriter(ClassWriter.COMPUTE_FRAMES + ClassWriter.COMPUTE_MAXS).apply {
		node.accept(this)
	}
}

inline fun buildMethod(api: Int = ASM5, builder: MethodNode.() -> Unit): MethodNode {
	return MethodNode(api).apply(builder)
}

context(classNode: ClassNode, _: MethodNode)
fun MutableList<LocalVariableNode>.addThisVariable(start: LabelNode? = null, end: LabelNode? = null) {
	this.add(LocalVariableNode("this", classNode.descriptor, null, start, end, 0))
}

context(insns: InsnList)
operator fun AbstractInsnNode.unaryPlus() {
	insns.add(this)
}

inline fun buildInsnList(action: InsnList.() -> Unit): InsnList {
	return InsnList().apply(action)
}

val ClassNode.descriptor: String
	get() = "L$name;"

val Class<*>.internalName: String
	get() = Type.getInternalName(this)

inline fun InsnList.instantiate(classInternalName: String, leadup: (InsnList.() -> Unit) = {}) {
	+TypeInsnNode(NEW, classInternalName)
	+InsnNode(DUP)
	this.apply(leadup)
	+MethodInsnNode(INVOKESPECIAL, classInternalName, "<init>", "()V", false)
}

context(_: ClassNode)
fun overrideMethod(superMethodRef: Method): MethodNode {
	return buildMethod {
		access = superMethodRef.modifiers
		name = superMethodRef.name
		desc = Type.getMethodDescriptor(superMethodRef)
		parameters = superMethodRef.parameters.mapTo(mutableListOf()) { ParameterNode(it.name, it.modifiers) }
		localVariables = mutableListOf<LocalVariableNode>().apply {
			addThisVariable()
			superMethodRef.parameters.forEachIndexed { i, param ->
				add(LocalVariableNode(param.name, Type.getDescriptor(param.type), null, null, null, i + 1))
			}
		}
	}
}


fun InsnList.invokeMethod(opcode: Int, method: Method, isInterface: Boolean = false) {
	+MethodInsnNode(opcode, Type.getInternalName(method.declaringClass), method.name, Type.getMethodDescriptor(method), isInterface)
}


val ABSTRACT_UNIT_TEST_DESC = JvmUnitTest::class.java.descriptorString()


@ExtendWith

fun generateTestClass(data: FirTestMetadata) {
	val originalTestClassDesc = data.owningClass.descriptorString()
	val genClass = buildClass {
		version = V17
		access = ACC_PUBLIC
		name = "${data.owningClass.packageName.replace('.', '/')}/firtests/${data.owningClass.simpleName}_${data.methodName}"
		
		superName = ABSTRACT_UNIT_TEST_DESC
		
		val FIELD_TESTCLASS_INSTANCE = "inst"
		
		visibleAnnotations.add(AnnotationNode(ASM5, DisplayName::class.java.descriptorString()).apply {
			values = mutableListOf(data.owningClass.simpleName)
		})
		
		fields = mutableListOf(
				FieldNode(ASM5, ACC_PRIVATE, FIELD_TESTCLASS_INSTANCE, originalTestClassDesc, null, null)
		)
		
		methods = mutableListOf(
				buildTestClassCtor(data),
				buildTestMethod(data)
		)
	}
}
*/
/*
// access flags 0x1
  public <init>()V
    ALOAD 0
    NEW org/jetbrains/kotlin/compiler/plugin/template/unittesting/TestData
    DUP
    LDC Lorg/jetbrains/kotlin/compiler/plugin/template/unittesting/MyTests;.class
    LDC "does a thing"
    INVOKESPECIAL org/jetbrains/kotlin/compiler/plugin/template/unittesting/TestData.<init> (Ljava/lang/Class;Ljava/lang/String;)V
    ASTORE 1
    ALOAD 1
    ASTORE 2
    ASTORE 6
    ICONST_0
    ISTORE 3
    NEW org/jetbrains/kotlin/compiler/plugin/template/unittesting/MyTests
    DUP
    INVOKESPECIAL org/jetbrains/kotlin/compiler/plugin/template/unittesting/MyTests.<init> ()V
    ASTORE 4
    ICONST_0
    ISTORE 5
    ALOAD 4
    ALOAD 2
    INVOKEVIRTUAL org/jetbrains/kotlin/compiler/plugin/template/unittesting/MyTests.does a thing (Lorg/jetbrains/kotlin/compiler/plugin/template/unittesting/TestData;)V
    ALOAD 6
    ALOAD 1
    INVOKESPECIAL org/jetbrains/kotlin/compiler/plugin/template/unittesting/JvmUnitTest.<init> (Lorg/jetbrains/kotlin/compiler/plugin/template/unittesting/TestData;)V
    RETURN
   L11
    LOCALVARIABLE $i$a$-with-Something$1$1 I L5 L6 5
    LOCALVARIABLE $this$_init__u24lambda_u240_u240 Lorg/jetbrains/kotlin/compiler/plugin/template/unittesting/MyTests; L4 L6 4
    LOCALVARIABLE $i$a$-also-Something$1 I L3 L8 3
    LOCALVARIABLE it Lorg/jetbrains/kotlin/compiler/plugin/template/unittesting/TestData; L2 L8 2
    LOCALVARIABLE this Lorg/jetbrains/kotlin/compiler/plugin/template/unittesting/Something; L0 L11 0
    MAXSTACK = 5
    MAXLOCALS = 7

  // access flags 0x11
  public final executeFirTest()V
  @Lorg/junit/jupiter/api/Test;()
   L0
    LINENUMBER 477 L0
    ALOAD 0
    LDC "somefilepath"
    INVOKESPECIAL org/jetbrains/kotlin/compiler/plugin/template/unittesting/JvmUnitTest.genKtFile (Ljava/lang/String;)V
   L1
    LINENUMBER 478 L1
    ALOAD 0
    LDC "somefilepath"
    INVOKEVIRTUAL org/jetbrains/kotlin/compiler/plugin/template/unittesting/Something.runTest (Ljava/lang/String;)V
   L2
    LINENUMBER 479 L2
    RETURN
   L3
    LOCALVARIABLE this Lorg/jetbrains/kotlin/compiler/plugin/template/unittesting/Something; L0 L3 0
    MAXSTACK = 2
    MAXLOCALS = 1
}
 *//*


context(classNode: ClassNode)
fun buildTestClassCtor(data: FirTestMetadata) = buildMethod {
	val originalTestClassName = data.owningClass.internalName
	
	access = ACC_PUBLIC
	name = "<init>"
	desc = "()V"
	
	val l0 = LabelNode()
	val l1 = LabelNode()
	val l2 = LabelNode()
	val l3 = LabelNode()
	
	localVariables = mutableListOf()
	localVariables.addThisVariable(l0, l3)
	localVariables.add(LocalVariableNode("testData", TestData::class.java.descriptorString(), null, l0, l3, 1))
	
	instructions = buildInsnList {
		+l0
			instantiate(TestData::class.java.internalName) {
				+LdcInsnNode(Type.getType(data.owningClass))
				+LdcInsnNode(data.methodName)
			}
			+VarInsnNode(ASTORE, 1)
		
		+l1
			instantiate(originalTestClassName)
			+VarInsnNode(ALOAD, 1)
			+MethodInsnNode(INVOKEVIRTUAL, originalTestClassName, data.methodName, "(${TestData::class.java.desc})V", false)
		+l2
			+VarInsnNode(ALOAD, 0)
			+VarInsnNode(ALOAD, 1)
			+MethodInsnNode(INVOKESPECIAL, classNode.superName, "<init>", "()V", false)
			+InsnNode(RETURN)
		+l3
	}
}

context(classNode: ClassNode)
fun buildTestMethod(data: FirTestMetadata) = buildMethod { // test method
	access = ACC_PUBLIC
	name = data.methodName
	desc = "()V"
	visibleAnnotations = mutableListOf(
			AnnotationNode(ASM5, Test::class.java.descriptorString()),
	)
	
	val l0 = LabelNode()
	val l1 = LabelNode()
	val l2 = LabelNode()
	localVariables.addThisVariable(l0, l1)
	instructions = buildInsnList {
		val filepath = data.filePath.toString()
		+l0
			+VarInsnNode(ALOAD, 0)
			+LdcInsnNode(filepath)
			invokeMethod(INVOKEVIRTUAL, JvmUnitTest::genKtFile.javaMethod!!)
		+l1
			+VarInsnNode(ALOAD, 0)
			+LdcInsnNode(filepath)
			val runtest: JvmUnitTest.(String) -> Unit = JvmUnitTest::runTest
			invokeMethod(INVOKEVIRTUAL, (runtest as KFunction<*>).javaMethod!!)
			+InsnNode(RETURN)
		+l2
	}
}


const val FILE_PATH = "somefilepath"

class Something
	: JvmUnitTest(
		TestData(
				MyTests::class.java,
				"does a thing"
		).also {
			with (MyTests()) {
				it.`does a thing`()
			}
		})
{
	@Test
	fun executeFirTest() {
		super.genKtFile(FILE_PATH)
		runTest(FILE_PATH)
	}
}
*/

