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

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
 
import java.time.Duration;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class MirrorClient implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(MirrorClient.class);

    private AdminClient adminClient;
    private ReplicationPolicy replicationPolicy;
    private Map<String, Object> consumerConfig;

    public MirrorClient(Map<String, Object> props) {
        MirrorClientConfig config = new MirrorClientConfig(props);
        adminClient = AdminClient.create(config.adminConfig());
        consumerConfig = config.consumerConfig();
        replicationPolicy = config.replicationPolicy();
    }

    // for testing
    MirrorClient(AdminClient adminClient, ReplicationPolicy replicationPolicy,
            Map<String, Object> consumerConfig) {
        this.adminClient = adminClient;
        this.replicationPolicy = replicationPolicy;
        this.consumerConfig = consumerConfig;
    }

    public void close() {
        adminClient.close();
    }

    public int replicationHops(String upstreamClusterAlias)
            throws InterruptedException, ExecutionException {
        return heartbeatTopics().stream()
            .map(x -> countHopsForTopic(x, upstreamClusterAlias))
            .filter(x -> x != -1)
            .mapToInt(x -> x)
            .min()
            .orElse(-1);
    }

    public Set<String> heartbeatTopics()
            throws InterruptedException, ExecutionException {
        return listTopics().stream()
            .filter(this::isHeartbeatTopic)
            .collect(Collectors.toSet());
    }

    public Set<String> checkpointTopics()
            throws InterruptedException, ExecutionException {
        return listTopics().stream()
            .filter(this::isCheckpointTopic)
            .collect(Collectors.toSet());
    }

    /** Finds upstream clusters based on incoming heartbeats */
    public Set<String> upstreamClusters()
            throws InterruptedException, ExecutionException {
        return listTopics().stream()
            .filter(this::isHeartbeatTopic)
            .map(replicationPolicy::topicSource)
            .collect(Collectors.toSet());
    }

    public Map<TopicPartition, OffsetAndMetadata> remoteConsumerOffsets(String consumerGroupId,
            String remoteClusterAlias, Duration timeout)
            throws InterruptedException, ExecutionException, TimeoutException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        KafkaConsumer<byte[], byte[]> consumer = new KafkaConsumer<>(consumerConfig,
            new ByteArrayDeserializer(), new ByteArrayDeserializer());
        Map<TopicPartition, OffsetAndMetadata> offsets = new HashMap<>();
        String checkpointTopic = replicationPolicy.formatRemoteTopic(remoteClusterAlias,
            MirrorClientConfig.CHECKPOINTS_TOPIC);
        List<TopicPartition> checkpointAssignment =
            Collections.singletonList(new TopicPartition(checkpointTopic, 0));
        consumer.assign(checkpointAssignment);
        consumer.seekToBeginning(checkpointAssignment);
        while (System.currentTimeMillis() < deadline && !endOfStream(consumer, checkpointAssignment)) {
            ConsumerRecords<byte[], byte[]> records = consumer.poll(timeout);
            for (ConsumerRecord<byte[], byte[]> record : records) {
                Checkpoint checkpoint = Checkpoint.deserializeRecord(record);
                if (checkpoint.consumerGroupId().equals(consumerGroupId)) {
                    offsets.put(checkpoint.topicPartition(), checkpoint.offsetAndMetadata());
                }
            }
        }
        log.info("Consumed {} checkpoint records for {} from {}.", offsets.size(),
            consumerGroupId, checkpointTopic);
        consumer.close();
        return offsets;
    }

    // visible for testing
    protected Set<String> listTopics()
            throws InterruptedException, ExecutionException {
        return adminClient.listTopics().names().get();
    }

    int countHopsForTopic(String topic, String sourceClusterAlias) {
        int hops = 0;
        while (true) {
            hops++;
            String source = replicationPolicy.topicSource(topic);
            if (source == null) {
                return -1;
            }
            if (source.equals(sourceClusterAlias)) {
                return hops;
            }
            topic = replicationPolicy.upstreamTopic(topic);
        } 
    }

    boolean isHeartbeatTopic(String topic) {
        return MirrorClientConfig.HEARTBEATS_TOPIC.equals(replicationPolicy.originalTopic(topic));
    }

    boolean isCheckpointTopic(String topic) {
        // checkpoint topics must have a source
        return replicationPolicy.topicSource(topic) != null
            && MirrorClientConfig.CHECKPOINTS_TOPIC.equals(replicationPolicy.originalTopic(topic));
    }

    static protected boolean endOfStream(Consumer<?, ?> consumer, Collection<TopicPartition> assignments)
            throws InterruptedException, TimeoutException {
        Map<TopicPartition, Long> endOffsets = consumer.endOffsets(assignments);
        for (TopicPartition topicPartition : assignments) {
            if (consumer.position(topicPartition) < endOffsets.get(topicPartition)) {
                return false;
            }
        }
        return true;
    }
}
