/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors.telemetry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.commons.OSMetricsGeneratorFactory;
import org.opensearch.performanceanalyzer.commons.collectors.PerformanceAnalyzerMetricsCollector;
import org.opensearch.performanceanalyzer.commons.collectors.TelemetryCollector;
import org.opensearch.performanceanalyzer.commons.config.overrides.ConfigOverridesWrapper;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.commons.metrics.RTFMetrics;
import org.opensearch.performanceanalyzer.commons.metrics_generator.DiskMetricsGenerator;
import org.opensearch.performanceanalyzer.commons.metrics_generator.OSMetricsGenerator;
import org.opensearch.performanceanalyzer.commons.stats.metrics.StatExceptionCode;
import org.opensearch.performanceanalyzer.commons.stats.metrics.StatMetrics;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.telemetry.metrics.Histogram;
import org.opensearch.telemetry.metrics.MetricsRegistry;
import org.opensearch.telemetry.metrics.tags.Tags;

public class RTFDisksCollector extends PerformanceAnalyzerMetricsCollector
        implements TelemetryCollector {

    private Histogram diskWaitTimeMetrics;
    private Histogram diskServiceRateMetrics;
    private Histogram diskUtilizationMetrics;
    private MetricsRegistry metricsRegistry;
    private boolean metricsInitialised;
    private static final Logger LOG = LogManager.getLogger(RTFDisksCollector.class);
    private PerformanceAnalyzerController performanceAnalyzerController;
    private ConfigOverridesWrapper configOverridesWrapper;

    public RTFDisksCollector(
            PerformanceAnalyzerController performanceAnalyzerController,
            ConfigOverridesWrapper configOverridesWrapper) {
        super(
                MetricsConfiguration.CONFIG_MAP.get(RTFDisksCollector.class).samplingInterval,
                "RTFDisksCollector",
                StatMetrics.RTF_DISKS_COLLECTOR_EXECUTION_TIME,
                StatExceptionCode.RTF_DISK_METRICS_COLLECTOR_ERROR);
        this.metricsInitialised = false;
        this.performanceAnalyzerController = performanceAnalyzerController;
        this.configOverridesWrapper = configOverridesWrapper;
    }

    @Override
    public void collectMetrics(long startTime) {
        if (performanceAnalyzerController.isCollectorDisabled(
                configOverridesWrapper, getCollectorName())) {
            LOG.info("RTFDisksCollector is disabled. Skipping collection.");
            return;
        }

        OSMetricsGenerator generator = OSMetricsGeneratorFactory.getInstance();
        if (generator == null) {
            LOG.error("could not get the instance of OSMetricsGeneratorFactory class");
            return;
        }

        metricsRegistry = OpenSearchResources.INSTANCE.getMetricsRegistry();
        if (metricsRegistry == null) {
            LOG.error("could not get the instance of MetricsRegistry class");
            return;
        }

        LOG.debug("Executing collect metrics for RTFDisksCollector");

        initialiseMetricsIfNeeded();
        DiskMetricsGenerator diskMetricsGenerator = generator.getDiskMetricsGenerator();
        diskMetricsGenerator.addSample();

        recordMetrics(diskMetricsGenerator);
    }

    public void recordMetrics(DiskMetricsGenerator diskMetricsGenerator) {
        for (String disk : diskMetricsGenerator.getAllDisks()) {
            Tags diskNameTag =
                    Tags.create().addTag(RTFMetrics.DiskDimension.DISK_NAME.toString(), disk);
            double diskWaitTime = diskMetricsGenerator.getAwait(disk);
            double diskServiceRate = diskMetricsGenerator.getServiceRate(disk);
            double diskUtilization = diskMetricsGenerator.getDiskUtilization(disk);
            diskWaitTimeMetrics.record(diskWaitTime, diskNameTag);
            diskUtilizationMetrics.record(diskUtilization, diskNameTag);
            diskServiceRateMetrics.record(diskServiceRate, diskNameTag);
        }
    }

    private void initialiseMetricsIfNeeded() {
        if (metricsInitialised == false) {
            diskWaitTimeMetrics =
                    metricsRegistry.createHistogram(
                            RTFMetrics.DiskValue.Constants.WAIT_VALUE,
                            "DiskWaitTimeMetrics",
                            RTFMetrics.MetricUnits.MILLISECOND.toString());
            diskServiceRateMetrics =
                    metricsRegistry.createHistogram(
                            RTFMetrics.DiskValue.Constants.SRATE_VALUE,
                            "DiskServiceRateMetrics",
                            RTFMetrics.MetricUnits.MEGABYTE_PER_SEC.toString());
            diskUtilizationMetrics =
                    metricsRegistry.createHistogram(
                            RTFMetrics.DiskValue.Constants.UTIL_VALUE,
                            "DiskUtilizationMetrics",
                            RTFMetrics.MetricUnits.PERCENT.toString());
            metricsInitialised = true;
        }
    }
}
