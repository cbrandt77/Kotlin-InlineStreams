package io.github.cbrandt77.kt.inlinestreams.fir.diagnostics

import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer


object FirErrorTypes_InlineStreams : KtDiagnosticsContainer() {
	
	override fun getRendererFactory() = FirErrorMessages_InlineStreams
}

