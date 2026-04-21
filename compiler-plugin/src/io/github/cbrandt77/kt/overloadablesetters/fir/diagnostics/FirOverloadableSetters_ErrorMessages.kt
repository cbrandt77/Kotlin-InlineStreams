package io.github.cbrandt77.kt.overloadablesetters.fir.diagnostics

import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticRenderers
import org.jetbrains.kotlin.diagnostics.KtDiagnosticRenderers.TO_STRING
import org.jetbrains.kotlin.diagnostics.KtDiagnosticRenderers.VISIBILITY
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirDiagnosticRenderers
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirDiagnosticRenderers.DECLARATION_NAME
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirDiagnosticRenderers.NAME_OF_DECLARATION_OR_FILE
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirDiagnosticRenderers.RENDER_TYPE

object FirOverloadableSetters_ErrorMessages : BaseDiagnosticRendererFactory() {
	override val MAP by KtDiagnosticFactoryToRendererMap("FIRSetterOverloading") { map ->
		with (map) {
		
		}
	}
}