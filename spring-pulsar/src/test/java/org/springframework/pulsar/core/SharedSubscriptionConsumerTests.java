/*
 * Copyright 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.pulsar.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.SubscriptionType;
import org.junit.jupiter.api.Test;

import org.springframework.pulsar.listener.DefaultPulsarMessageListenerContainer;
import org.springframework.pulsar.listener.PulsarContainerProperties;
import org.springframework.pulsar.listener.PulsarRecordMessageListener;
import org.springframework.pulsar.test.support.PulsarTestContainerSupport;

/**
 * Tests for shared subscription types in Pulsar consumer.
 *
 * @author Soby Chacko
 */
public class SharedSubscriptionConsumerTests implements PulsarTestContainerSupport {

	@Test
	void sharedSubscriptionRoundRobinBasicScenario() throws Exception {

		Map<String, Object> config = Map.of("topicNames",
				Collections.singleton("shared-subscription-single-msg-test-topic"), "subscriptionName",
				"shared-subscription-single-msg-test-sub");

		PulsarClient pulsarClient = PulsarClient.builder().serviceUrl(PulsarTestContainerSupport.getPulsarBrokerUrl())
				.build();
		DefaultPulsarConsumerFactory<String> pulsarConsumerFactory = new DefaultPulsarConsumerFactory<>(pulsarClient,
				config);

		CountDownLatch latch1 = new CountDownLatch(1);
		CountDownLatch latch2 = new CountDownLatch(1);
		CountDownLatch latch3 = new CountDownLatch(1);

		PulsarContainerProperties pulsarContainerProperties1 = pulsarContainerProperties(latch1, "hello john doe",
				SubscriptionType.Shared);
		DefaultPulsarMessageListenerContainer<String> container1 = new DefaultPulsarMessageListenerContainer<>(
				pulsarConsumerFactory, pulsarContainerProperties1);
		container1.start();

		PulsarContainerProperties pulsarContainerProperties2 = pulsarContainerProperties(latch2, "hello alice doe",
				SubscriptionType.Shared);
		DefaultPulsarMessageListenerContainer<String> container2 = new DefaultPulsarMessageListenerContainer<>(
				pulsarConsumerFactory, pulsarContainerProperties2);
		container2.start();

		PulsarContainerProperties pulsarContainerProperties3 = pulsarContainerProperties(latch3, "hello buzz doe",
				SubscriptionType.Shared);
		DefaultPulsarMessageListenerContainer<String> container3 = new DefaultPulsarMessageListenerContainer<>(
				pulsarConsumerFactory, pulsarContainerProperties3);
		container3.start();

		Map<String, Object> prodConfig = Map.of("topicName", "shared-subscription-single-msg-test-topic");
		DefaultPulsarProducerFactory<String> pulsarProducerFactory = new DefaultPulsarProducerFactory<>(pulsarClient,
				prodConfig);
		PulsarTemplate<String> pulsarTemplate = new PulsarTemplate<>(pulsarProducerFactory);

		pulsarTemplate.newMessage("hello john doe").sendAsync();
		pulsarTemplate.newMessage("hello alice doe").sendAsync();
		pulsarTemplate.newMessage("hello buzz doe").sendAsync();

		boolean await1 = latch1.await(10, TimeUnit.SECONDS);
		boolean await2 = latch2.await(10, TimeUnit.SECONDS);
		boolean await3 = latch3.await(10, TimeUnit.SECONDS);

		assertThat(await1).isTrue();
		assertThat(await2).isTrue();
		assertThat(await3).isTrue();

		container1.stop();
		container2.stop();
		container3.stop();

		pulsarClient.close();
	}

	private PulsarContainerProperties pulsarContainerProperties(CountDownLatch latch, String message,
			SubscriptionType subscriptionType) {
		PulsarContainerProperties pulsarContainerProperties = new PulsarContainerProperties();
		pulsarContainerProperties.setMessageListener((PulsarRecordMessageListener<?>) (consumer, msg) -> {
			assertThat(msg.getValue()).isEqualTo(message);
			latch.countDown();
		});
		pulsarContainerProperties.setSubscriptionType(subscriptionType);
		pulsarContainerProperties.setSchema(Schema.STRING);
		return pulsarContainerProperties;
	}

}