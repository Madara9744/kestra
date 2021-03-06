package io.kestra.runner.kafka.services;

import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.runners.Executor;
import io.kestra.core.runners.FlowExecutorInterface;
import io.kestra.core.services.TaskDefaultService;
import io.kestra.core.tasks.flows.Template;
import io.kestra.runner.kafka.serializers.JsonSerde;
import io.kestra.runner.kafka.streams.FlowJoinerTransformer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueStore;
import org.slf4j.Logger;

@Singleton
public class KafkaStreamSourceService {
    public static final String TOPIC_FLOWLAST = "flowlast";
    public static final String TOPIC_EXECUTOR_WORKERINSTANCE = "executorworkerinstance";

    @Inject
    private KafkaAdminService kafkaAdminService;

    @Inject
    private TaskDefaultService taskDefaultService;

    @Inject
    private FlowExecutorInterface flowExecutorInterface;

    @Inject
    private Template.TemplateExecutorInterface templateExecutorInterface;

    public KStream<String, Executor> executorKStream(StreamsBuilder builder) {
        return builder
            .stream(
                kafkaAdminService.getTopicName(Executor.class),
                Consumed.with(Serdes.String(), JsonSerde.of(Executor.class)).withName("KStream.Executor")
            );
    }

    public GlobalKTable<String, Trigger> triggerGlobalKTable(StreamsBuilder builder) {
        return builder
            .globalTable(
                kafkaAdminService.getTopicName(Trigger.class),
                Consumed.with(Serdes.String(), JsonSerde.of(Trigger.class)).withName("GlobalKTable.Trigger"),
                Materialized.<String, Trigger, KeyValueStore<Bytes, byte[]>>as("trigger")
                    .withKeySerde(Serdes.String())
                    .withValueSerde(JsonSerde.of(Trigger.class))
            );
    }

    public KStream<String, Executor> executorWithFlow(KStream<String, Executor> executionKStream, boolean withDefaults) {
        return executionKStream
            .filter((key, value) -> value != null, Named.as("ExecutorWithFlow.filterNotNull"))
            .transformValues(
                () -> new FlowJoinerTransformer(flowExecutorInterface, templateExecutorInterface, withDefaults, taskDefaultService)
            );
    }

    public static <T> KStream<String, T> logIfEnabled(Logger log, KStream<String, T> stream, ForeachAction<String, T> action, String name) {
        if (log.isDebugEnabled()) {
            return stream
                .filter((key, value) -> value != null, Named.as(name + "Log.filterNotNull"))
                .peek(action, Named.as(name + "Log.peek"));
        } else {
            return stream;
        }
    }
}
