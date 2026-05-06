package org.jetbrains.kotlin.compiler.plugin.template.services

import io.github.cbrandt77.kt.inlinestreams.CompilerPluginRegistrar_InlineStreams
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.builders.configureIrHandlersStep
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.TestServices

fun TestConfigurationBuilder.configurePlugin() {
    useConfigurators(::ExtensionRegistrarConfigurator)
    
    configureAnnotations()
}

private class ExtensionRegistrarConfigurator(testServices: TestServices) : EnvironmentConfigurator(testServices) {
    override fun CompilerPluginRegistrar.ExtensionStorage.registerCompilerExtensions(
        module: TestModule,
        configuration: CompilerConfiguration
    ) {
        with (CompilerPluginRegistrar_InlineStreams()) {
            registerExtensions(configuration)
        }
        
        IrGenerationExtension.registerExtension(object : IrGenerationExtension {
            override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
                moduleFragment.acceptChildrenVoid(IrInspector)
            }
        })
    }
}

object IrInspector : IrVisitorVoid() {
    override fun visitElement(element: IrElement) {
        element.acceptChildrenVoid(this)
    }
    
    override fun visitFunction(declaration: IrFunction) {
        if (declaration.name.asString() == "transformed") {
            println("breakpoint")
        }
    }
}