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
import org.jetbrains.kotlin.idea.base.psi.moveInsideParenthesesAndReplaceWith
import org.jetbrains.kotlin.idea.base.psi.shouldLambdaParameterBeNamed
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinModCommandWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AnalysisActionContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.utils.NamedArgumentUtils
import org.jetbrains.kotlin.idea.k2.refactoring.util.LambdaToAnonymousFunctionUtil
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtLabeledExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.utils.exceptions.errorWithAttachment
import org.jetbrains.kotlin.utils.exceptions.withPsiEntry

internal class LambdaToAnonymousFunctionIntention: AbstractKotlinModCommandWithContext<KtLambdaExpression, LambdaToAnonymousFunctionIntention.LambdaToFunctionContext>(
    KtLambdaExpression::class
) {

    class LambdaToFunctionContext(val signature: String, val lambdaArgumentName: Name?)

    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("convert.lambda.expression.to.anonymous.function")

    override fun getActionName(
        element: KtLambdaExpression,
        context: LambdaToFunctionContext
    ): @IntentionName String = KotlinBundle.message("convert.to.anonymous.function")

    context(KtAnalysisSession)
    override fun prepareContext(element: KtLambdaExpression): LambdaToFunctionContext? {
        val declarationSymbol = element.functionLiteral.getSymbol() as? KtAnonymousFunctionSymbol ?: return null
        if (declarationSymbol.valueParameters.any { it.returnType is KtErrorType }) return null

        // anonymous suspend functions are forbidden in Kotlin
        if ((element.functionLiteral.getKtType() as? KtFunctionalType)?.isSuspend == true) return null

        val signature = LambdaToAnonymousFunctionUtil.prepareFunctionText(element) ?: return null
        val parent = element.functionLiteral.parent
        val lambdaArgumentName = if (parent is KtLambdaArgument && shouldLambdaParameterBeNamed(parent)) {
            NamedArgumentUtils.getStableNameFor(parent)
        } else null
        return LambdaToFunctionContext(signature, lambdaArgumentName)
    }

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtLambdaExpression> {
        return applicabilityRange { lambdaExpression: KtLambdaExpression ->
            val lastElement = lambdaExpression.functionLiteral.arrow ?: lambdaExpression.functionLiteral.lBrace
            TextRange(0, lastElement.textRangeInParent.endOffset)
        }
    }

    override fun isApplicableByPsi(element: KtLambdaExpression): Boolean {
        if (element.functionLiteral.valueParameters.any { it.destructuringDeclaration != null}) return false
        val argument = element.getStrictParentOfType<KtValueArgument>()
        val call = argument?.getStrictParentOfType<KtCallElement>()
        return call?.getStrictParentOfType<KtFunction>()?.hasModifier(KtTokens.INLINE_KEYWORD) != true
    }

    override fun apply(
        element: KtLambdaExpression,
        context: AnalysisActionContext<LambdaToFunctionContext>,
        updater: ModPsiUpdater
    ) {
        val lambdaToFunctionContext = context.analyzeContext
        val resultingFunction = LambdaToAnonymousFunctionUtil.convertLambdaToFunction(element, lambdaToFunctionContext.signature) ?: return

        var parent = resultingFunction.parent
        if (parent is KtLabeledExpression) {
            parent = parent.replace(resultingFunction).parent
        }

        val argument = parent as? KtLambdaArgument ?: return

        val replacement = argument.getArgumentExpression()
            ?: errorWithAttachment("no argument expression for $argument") {
                withPsiEntry("lambdaExpression", argument)
            }
        argument.moveInsideParenthesesAndReplaceWith(replacement, lambdaToFunctionContext.lambdaArgumentName)
    }
}