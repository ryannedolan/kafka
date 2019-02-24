/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.kafka.connect.mirror;

import org.apache.kafka.common.MetricNameTemplate;
import org.apache.kafka.common.utils.Time;
import org.apache.kafka.common.metrics.Metrics;
import org.apache.kafka.common.metrics.MetricConfig;
import org.apache.kafka.common.metrics.MetricsReporter;
import org.apache.kafka.common.metrics.Sensor;
import org.apache.kafka.common.metrics.JmxReporter;
import org.apache.kafka.common.metrics.stats.Count;
import org.apache.kafka.common.metrics.stats.Value;
import org.apache.kafka.common.metrics.stats.Rate;
import org.apache.kafka.common.metrics.stats.Min;
import org.apache.kafka.common.metrics.stats.Max;
import org.apache.kafka.common.metrics.stats.Avg;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

class MirrorMetrics {

    private static final String JMX_PREFIX = "kafka.connect.mirror";
    private static final String RECORD_GROUP = "record-metrics";
    private static final String CHECKPOINT_GROUP = "checkpoint-metrics";
    private static final String HEARTBEAT_GROUP = "heartbeat-metrics";

    private static Map<SourceAndTarget, MirrorMetrics> metricsGroups = new HashMap<>();

    private static final Set<String> SOURCE_TARGET_TAGS = new HashSet<>(Arrays.asList("source", "target"));
    private static final MetricNameTemplate RECORD_COUNT = new MetricNameTemplate(
            "record-count", RECORD_GROUP,
            "Number of source records replicated by this connector.",
            SOURCE_TARGET_TAGS);
    private static final MetricNameTemplate RECORD_AGE = new MetricNameTemplate(
            "record-age-ms", RECORD_GROUP,
            "The age of incoming source records seen by this connector.",
            SOURCE_TARGET_TAGS);
    private static final MetricNameTemplate RECORD_AGE_MAX = new MetricNameTemplate(
            "record-age-ms-max", RECORD_GROUP,
            "The age of incoming source records seen by this connector.",
            SOURCE_TARGET_TAGS);
    private static final MetricNameTemplate RECORD_AGE_MIN = new MetricNameTemplate(
            "record-age-ms-min", RECORD_GROUP,
            "The age of incoming source records seen by this connector.",
            SOURCE_TARGET_TAGS);
    private static final MetricNameTemplate RECORD_AGE_AVG = new MetricNameTemplate(
            "record-age-ms-avg", RECORD_GROUP,
            "The age of incoming source records seen by this connector.",
            SOURCE_TARGET_TAGS);
    private static final MetricNameTemplate HEARTBEAT_RATE = new MetricNameTemplate(
            "heartbeat-rate", HEARTBEAT_GROUP,
            "Rate of heartbeats emitted from this connector.",
            SOURCE_TARGET_TAGS);
    private static final MetricNameTemplate HEARTBEAT_COUNT = new MetricNameTemplate(
            "heartbeat-count", HEARTBEAT_GROUP,
            "Number of heartbeats sent from source to target cluster.",
            SOURCE_TARGET_TAGS);
    private static final MetricNameTemplate REPLICATION_LAG = new MetricNameTemplate(
            "replication-lag-ms", HEARTBEAT_GROUP,
            "Time it takes heartbeats to get replicated from source to target clusters.",
            SOURCE_TARGET_TAGS);
    private static final MetricNameTemplate REPLICATION_LAG_MAX = new MetricNameTemplate(
            "replication-lag-ms-max", HEARTBEAT_GROUP,
            "Time it takes heartbeats to get replicated from source to target clusters.",
            SOURCE_TARGET_TAGS);
    private static final MetricNameTemplate REPLICATION_LAG_MIN = new MetricNameTemplate(
            "replication-lag-ms-min", HEARTBEAT_GROUP,
            "Time it takes heartbeats to get replicated from source to target clusters.",
            SOURCE_TARGET_TAGS);
    private static final MetricNameTemplate REPLICATION_LAG_AVG = new MetricNameTemplate(
            "replication-lag-ms-avg", HEARTBEAT_GROUP,
            "Time it takes heartbeats to get replicated from source to target clusters.",
            SOURCE_TARGET_TAGS);
    private static final MetricNameTemplate CHECKPOINT_COUNT = new MetricNameTemplate(
            "checkpoint-count", CHECKPOINT_GROUP,
            "Number of checkpoints sent from source to target cluster.",
            SOURCE_TARGET_TAGS);
    private static final MetricNameTemplate CHECKPOINT_RATE = new MetricNameTemplate(
            "checkpoint-rate", CHECKPOINT_GROUP,
            "Rate of checkpoints sent from source to target cluster.",
            SOURCE_TARGET_TAGS);
    private static final MetricNameTemplate CHECKPOINT_LAG = new MetricNameTemplate(
            "checkpoint-lag-ms", CHECKPOINT_GROUP,
            "Time it takes consumer state to get replicated from source to target clusters.",
            SOURCE_TARGET_TAGS);
    private static final MetricNameTemplate CHECKPOINT_LAG_MAX = new MetricNameTemplate(
            "checkpoint-lag-ms-max", CHECKPOINT_GROUP,
            "Time it takes consumer state to get replicated from source to target clusters.",
            SOURCE_TARGET_TAGS);
    private static final MetricNameTemplate CHECKPOINT_LAG_MIN = new MetricNameTemplate(
            "checkpoint-lag-ms-min", CHECKPOINT_GROUP,
            "Time it takes consumer state to get replicated from source to target clusters.",
            SOURCE_TARGET_TAGS);
    private static final MetricNameTemplate CHECKPOINT_LAG_AVG = new MetricNameTemplate(
            "checkpoint-lag-ms-avg", CHECKPOINT_GROUP,
            "Time it takes consumer state to get replicated from source to target clusters.",
            SOURCE_TARGET_TAGS);
 
 
    private final Metrics metrics; 
    private final Sensor recordSensor;
    private final Sensor recordAgeSensor;
    private final Sensor replicationLagSensor;
    private final Sensor checkpointSensor;
    private final Sensor checkpointLagSensor;
    private final Sensor heartbeatSensor;
 
    MirrorMetrics(SourceAndTarget sourceAndTarget, MetricConfig config, 
            List<MetricsReporter> metricsReporters, Time time) {
        metrics = new Metrics(config, metricsReporters, time);
        Map<String, String> tags = new HashMap<>();
        tags.put("source", sourceAndTarget.source());
        tags.put("target", sourceAndTarget.target());

        recordSensor = metrics.sensor("record-count");
        recordSensor.add(metrics.metricInstance(RECORD_COUNT, tags), new Count());

        recordAgeSensor = metrics.sensor("record-age");
        recordAgeSensor.add(metrics.metricInstance(RECORD_AGE, tags), new Value());
        recordAgeSensor.add(metrics.metricInstance(RECORD_AGE_MAX, tags), new Max());
        recordAgeSensor.add(metrics.metricInstance(RECORD_AGE_MIN, tags), new Min());
        recordAgeSensor.add(metrics.metricInstance(RECORD_AGE_AVG, tags), new Avg());

        checkpointSensor = metrics.sensor("checkpoint");
        checkpointSensor.add(metrics.metricInstance(CHECKPOINT_COUNT, tags), new Count());
        checkpointSensor.add(metrics.metricInstance(CHECKPOINT_RATE, tags), new Rate());

        heartbeatSensor = metrics.sensor("heartbeat");
        heartbeatSensor.add(metrics.metricInstance(HEARTBEAT_COUNT, tags), new Count());
        heartbeatSensor.add(metrics.metricInstance(HEARTBEAT_RATE, tags), new Rate());

        replicationLagSensor = metrics.sensor("replication-lag");
        replicationLagSensor.add(metrics.metricInstance(REPLICATION_LAG, tags), new Value());
        replicationLagSensor.add(metrics.metricInstance(REPLICATION_LAG_MAX, tags), new Max());
        replicationLagSensor.add(metrics.metricInstance(REPLICATION_LAG_MIN, tags), new Min());
        replicationLagSensor.add(metrics.metricInstance(REPLICATION_LAG_AVG, tags), new Avg());

        checkpointLagSensor = metrics.sensor("checkpoint-lag");
        checkpointLagSensor.add(metrics.metricInstance(CHECKPOINT_LAG, tags), new Value());
        checkpointLagSensor.add(metrics.metricInstance(CHECKPOINT_LAG_MAX, tags), new Max());
        checkpointLagSensor.add(metrics.metricInstance(CHECKPOINT_LAG_MIN, tags), new Min());
        checkpointLagSensor.add(metrics.metricInstance(CHECKPOINT_LAG_AVG, tags), new Avg());
    }

    void countRecord() {
        recordSensor.record();
    }

    void recordAge(long ageMillis) {
        recordAgeSensor.record((double) ageMillis);
    }

    void replicationLag(long millis) {
        replicationLagSensor.record((double) millis);
    }

    void countCheckpoint() {
        checkpointSensor.record();
    }

    void checkpointLag(long millis) {
        checkpointLagSensor.record((double) millis);
    }

    void countHeartbeat() {
        heartbeatSensor.record();
    }

    public static MirrorMetrics metricsFor(String source, String target) {
        return metricsFor(new SourceAndTarget(source, target));
    }

    public static MirrorMetrics metricsFor(SourceAndTarget sourceAndTarget) {
        return metricsGroups.computeIfAbsent(sourceAndTarget, x -> makeMetrics(x));
    }

    private static MirrorMetrics makeMetrics(SourceAndTarget sourceAndTarget) {
        return new MirrorMetrics(sourceAndTarget, new MetricConfig(), 
            Collections.singletonList(new JmxReporter(JMX_PREFIX)), Time.SYSTEM);
    }
}