// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.debugger.test.cases;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode;
import org.jetbrains.kotlin.idea.base.test.TestRoot;
import org.jetbrains.kotlin.idea.test.JUnit3RunnerWithInners;
import org.jetbrains.kotlin.idea.test.KotlinTestUtils;
import org.jetbrains.kotlin.test.TestMetadata;
import org.junit.runner.RunWith;

/**
 * This class is generated by {@link org.jetbrains.kotlin.testGenerator.generator.TestGenerator}.
 * DO NOT MODIFY MANUALLY.
 */
@SuppressWarnings("all")
@TestRoot("jvm-debugger/test/k2")
@TestDataPath("$CONTENT_ROOT")
@RunWith(JUnit3RunnerWithInners.class)
@TestMetadata("../testData/breakpointApplicability")
public class K2BreakpointApplicabilityTestGenerated extends AbstractK2BreakpointApplicabilityTest {
    @java.lang.Override
    @org.jetbrains.annotations.NotNull
    public final KotlinPluginMode getPluginMode() {
        return KotlinPluginMode.K2;
    }

    private void runTest(String testDataFilePath) throws Exception {
        KotlinTestUtils.runTest(this::doTest, this, testDataFilePath);
    }

    @TestMetadata("abstractMethods.kt")
    public void testAbstractMethods() throws Exception {
        runTest("../testData/breakpointApplicability/abstractMethods.kt");
    }

    @TestMetadata("constructors.kt")
    public void testConstructors() throws Exception {
        runTest("../testData/breakpointApplicability/constructors.kt");
    }

    @TestMetadata("functions.kt")
    public void testFunctions() throws Exception {
        runTest("../testData/breakpointApplicability/functions.kt");
    }

    @TestMetadata("inlineOnly.kt")
    public void testInlineOnly() throws Exception {
        runTest("../testData/breakpointApplicability/inlineOnly.kt");
    }

    @TestMetadata("lambdaProperty.kt")
    public void testLambdaProperty() throws Exception {
        runTest("../testData/breakpointApplicability/lambdaProperty.kt");
    }

    @TestMetadata("locals.kt")
    public void testLocals() throws Exception {
        runTest("../testData/breakpointApplicability/locals.kt");
    }

    @TestMetadata("properties.kt")
    public void testProperties() throws Exception {
        runTest("../testData/breakpointApplicability/properties.kt");
    }

    @TestMetadata("return.kt")
    public void testReturn() throws Exception {
        runTest("../testData/breakpointApplicability/return.kt");
    }

    @TestMetadata("simple.kt")
    public void testSimple() throws Exception {
        runTest("../testData/breakpointApplicability/simple.kt");
    }
}
