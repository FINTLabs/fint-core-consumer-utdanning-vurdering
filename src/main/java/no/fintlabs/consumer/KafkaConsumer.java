package no.fintlabs.consumer;

import lombok.extern.slf4j.Slf4j;
import no.fintlabs.kafka.common.ListenerBeanRegistrationService;
import no.fintlabs.kafka.common.OffsetSeekingTrigger;
import no.fintlabs.kafka.entity.EntityConsumerConfiguration;
import no.fintlabs.kafka.entity.EntityConsumerFactoryService;
import no.fintlabs.kafka.entity.topic.EntityTopicNameParameters;
import no.fintlabs.kafka.entity.topic.EntityTopicService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.kafka.listener.CommonLoggingErrorHandler;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

@Slf4j
public abstract class KafkaConsumer<V> {

    private final EntityConsumerFactoryService entityConsumerFactoryService;
    private final ListenerBeanRegistrationService listenerBeanRegistrationService;
    private final EntityTopicService entityTopicService;
    private final String resourceName;
    private final OffsetSeekingTrigger resetTrigger;

    public KafkaConsumer(
            String resourceName, EntityConsumerFactoryService entityConsumerFactoryService,
            ListenerBeanRegistrationService listenerBeanRegistrationService,
            EntityTopicService entityTopicService) {
        this.entityConsumerFactoryService = entityConsumerFactoryService;
        this.listenerBeanRegistrationService = listenerBeanRegistrationService;
        this.entityTopicService = entityTopicService;
        this.resourceName = resourceName;
        resetTrigger = new OffsetSeekingTrigger();
    }

    public long registerListener(Class<V> clazz, Consumer<ConsumerRecord<String, V>> consumer) {
        EntityTopicNameParameters topicNameParameters = EntityTopicNameParameters
                .builder()
                .resource(resourceName)
                .build();

        long retention = getRetention(topicNameParameters);
        // TODO: 11/03/2022 What to do if fails to get retention
        // TODO: 11/05/2022 What if the adapter re-register and the retention change

        ConcurrentMessageListenerContainer<String, V> messageListenerContainer =
                entityConsumerFactoryService
                        .createFactory(
                                clazz,
                                consumer,
                                EntityConsumerConfiguration
                                        .builder()
                                        .errorHandler(new CommonLoggingErrorHandler())
                                        .offsetSeekingTrigger(resetTrigger)
                                        .build()
                        )
                        .createContainer(topicNameParameters);

        listenerBeanRegistrationService.registerBean(messageListenerContainer);

        return retention;
    }

    public void seekToBeginning() {
        resetTrigger.seekToBeginning();
    }

    private long getRetention(EntityTopicNameParameters topicNameParameters) {
        try {
            Map<String, String> config = entityTopicService.getTopicConfig(topicNameParameters);
            String output = config.get(TopicConfig.RETENTION_MS_CONFIG);
            return Long.parseLong(output);

        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }

        return 0;
    }
}
