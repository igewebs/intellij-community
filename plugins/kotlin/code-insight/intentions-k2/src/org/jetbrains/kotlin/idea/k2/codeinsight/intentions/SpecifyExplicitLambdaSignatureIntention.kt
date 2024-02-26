// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtAnonymousFunctionSymbol
import org.jetbrains.kotlin.analysis.api.types.KtErrorType
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.idea.base.psi.shouldLambdaParameterBeNamed
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinModCommandWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AnalysisActionContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.utils.NamedArgumentUtils
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.k2.refactoring.util.LambdaToAnonymousFunctionUtil
import org.jetbrains.kotlin.idea.k2.refactoring.util.getExplicitLambdaSignature
import org.jetbrains.kotlin.idea.k2.refactoring.util.specifyExplicitLambdaSignature
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal class SpecifyExplicitLambdaSignatureIntention: AbstractKotlinModCommandWithContext<KtLambdaExpression, String>(
    KtLambdaExpression::class
) {

    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("specify.explicit.lambda.signature")

    override fun getActionName(element: KtLambdaExpression, context: String): @IntentionName String = familyName

    context(KtAnalysisSession)
    override fun prepareContext(element: KtLambdaExpression): String? {
        return getExplicitLambdaSignature(element)
    }

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtLambdaExpression> {
        return ApplicabilityRanges.SELF
    }

    override fun isApplicableByPsi(element: KtLambdaExpression): Boolean {
        return element.functionLiteral.arrow == null || !element.valueParameters.all { it.typeReference != null }
    }

    override fun apply(
        element: KtLambdaExpression,
        context: AnalysisActionContext<String>,
        updater: ModPsiUpdater
    ) {
        specifyExplicitLambdaSignature(element, context.analyzeContext)
    }
}