// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.diagnostic

/** Counterpart of com.intellij.tools.ide.metrics.collector.OpenTelemetryMeterCollector */
class TelemetryMeterCollector(val metricsAggregation: MetricsAggregation, val metersFilter: (Map.Entry<String, List<Long>>) -> Boolean)