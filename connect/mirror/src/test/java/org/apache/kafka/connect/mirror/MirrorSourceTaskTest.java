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

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.record.TimestampType;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.connect.source.SourceRecord;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class MirrorSourceTaskTest {

    @Test
    public void testSerde() {
        byte[] key = new byte[]{'a', 'b', 'c', 'd', 'e'};
        byte[] value = new byte[]{'f', 'g', 'h', 'i', 'j', 'k'};
        TimestampType timestampType = TimestampType.CREATE_TIME;
        ConsumerRecord<byte[], byte[]> consumerRecord = new ConsumerRecord<>("topic1", 2, 3L, 4L,
            TimestampType.CREATE_TIME, 0, 5, 6, key, value);
        MirrorSourceTask mirrorSourceTask = new MirrorSourceTask("cluster7",
            new DefaultReplicationPolicy(), 50);
        SourceRecord sourceRecord = mirrorSourceTask.convertRecord(consumerRecord);
        assertEquals("cluster7.topic1", sourceRecord.topic());
        assertEquals(2, sourceRecord.kafkaPartition().intValue());
        assertEquals(new TopicPartition("topic1", 2), MirrorUtils.unwrapPartition(sourceRecord.sourcePartition()));
        assertEquals(3L, MirrorUtils.unwrapOffset(sourceRecord.sourceOffset()).longValue());
        assertEquals(4L, sourceRecord.timestamp().longValue());
        assertEquals(key, sourceRecord.key());
        assertEquals(value, sourceRecord.value());
    }

    @Test
    public void testOffsetSync() {
        MirrorSourceTask.PartitionState partitionState = new MirrorSourceTask.PartitionState(50);

        assertTrue(partitionState.update(0, 100));  // always emit offset sync on the first update
        assertTrue(partitionState.update(2, 102));  // upstream offset skipped -> resync
        assertFalse(partitionState.update(3, 153));
        assertFalse(partitionState.update(4, 154));
        assertFalse(partitionState.update(5, 155));
        assertTrue(partitionState.update(6, 207));  // one past target offset
        assertTrue(partitionState.update(2, 208));  // upstream reset
        assertFalse(partitionState.update(3, 209));
        assertTrue(partitionState.update(4, 3));    // downstream reset
        assertFalse(partitionState.update(5, 4));
    }
}
