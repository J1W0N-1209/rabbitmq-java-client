// Copyright (c) 2023-2025 Broadcom. All Rights Reserved. The term "Broadcom" refers to Broadcom
// Inc.
// and/or its subsidiaries.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 2.0 ("MPL"), the GNU General Public License version 2
// ("GPL") and the Apache License version 2 ("ASL"). For the MPL, please see
// LICENSE-MPL-RabbitMQ. For the GPL, please see LICENSE-GPL2.  For the ASL,
// please see LICENSE-APACHE2.
//
// This software is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND,
// either express or implied. See the LICENSE file for specific language governing
// rights and limitations of this software.
//
// If you have any questions regarding licensing, please contact us at
// info@rabbitmq.com.

package com.rabbitmq.client.test;

import static com.rabbitmq.client.test.TestUtils.LatchConditions.completed;
import static org.assertj.core.api.Assertions.assertThat;

import com.rabbitmq.client.*;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

public class MaxInboundMessageSizeTest extends BrokerTestCase {

  String q;

  private static void safeClose(Connection c) {
    try {
      c.close();
    } catch (Exception e) {
      // OK
    }
  }

  @Override
  protected void createResources() throws IOException, TimeoutException {
    q = generateQueueName();
    declareTransientQueue(q);
    super.createResources();
  }

  @CsvSource({
    "20000,5000,true",
    "20000,100000,true",
    "20000,5000,false",
    "20000,100000,false",
  })
  @ParameterizedTest
  void maxInboundMessageSizeMustBeEnforced(int maxMessageSize, int frameMax, boolean basicGet)
      throws Exception {
    ConnectionFactory cf = newConnectionFactory();
    cf.setMaxInboundMessageBodySize(maxMessageSize);
    cf.setRequestedFrameMax(frameMax);
    Connection c = cf.newConnection();
    try {
      Channel ch = c.createChannel();
      ch.confirmSelect();
      byte[] body = new byte[maxMessageSize * 2];
      ch.basicPublish("", q, null, body);
      ch.waitForConfirmsOrDie();
      AtomicReference<Throwable> channelException = new AtomicReference<>();
      CountDownLatch channelErrorLatch = new CountDownLatch(1);
      ch.addShutdownListener(
          cause -> {
            channelException.set(cause.getCause());
            channelErrorLatch.countDown();
          });
      AtomicReference<Throwable> connectionException = new AtomicReference<>();
      CountDownLatch connectionErrorLatch = new CountDownLatch(1);
      c.addShutdownListener(
          cause -> {
            connectionException.set(cause.getCause());
            connectionErrorLatch.countDown();
          });
      if (basicGet) {
        try {
          ch.basicGet(q, true);
        } catch (Exception e) {
          // OK for basicGet
        }
      } else {
        ch.basicConsume(q, new DefaultConsumer(ch));
      }
      assertThat(channelErrorLatch).is(completed());
      assertThat(channelException.get())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Message body is too large");
      assertThat(connectionErrorLatch).is(completed());
      assertThat(connectionException.get())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Message body is too large");
    } finally {
      safeClose(c);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void largeMessageShouldGoBackToQueue(boolean basicGet) throws Exception {
    int maxMessageSize = 5_000;
    int maxFrameSize = maxMessageSize * 4;
    ConnectionFactory cf = newConnectionFactory();
    cf.setMaxInboundMessageBodySize(maxMessageSize);
    cf.setRequestedFrameMax(maxFrameSize);
    String messageId = UUID.randomUUID().toString();
    Connection c = cf.newConnection();
    try {
      Channel ch = c.createChannel();
      ch.confirmSelect();
      AMQP.BasicProperties.Builder propsBuilder = new AMQP.BasicProperties.Builder();
      propsBuilder.messageId(messageId);
      byte[] body = new byte[maxMessageSize * 2];
      ch.basicPublish("", q, propsBuilder.build(), body);
      ch.waitForConfirmsOrDie();
      AtomicReference<Throwable> exception = new AtomicReference<>();
      CountDownLatch errorLatch = new CountDownLatch(1);
      ch.addShutdownListener(
          cause -> {
            exception.set(cause.getCause());
            errorLatch.countDown();
          });
      if (basicGet) {
        try {
          ch.basicGet(q, false);
        } catch (Exception e) {
          // OK for basicGet
        }
      } else {
        ch.basicConsume(q, false, new DefaultConsumer(ch));
      }
      assertThat(errorLatch).is(completed());
      assertThat(exception.get())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Message body is too large");
    } finally {
      safeClose(c);
    }

    cf = newConnectionFactory();
    cf.setMaxInboundMessageBodySize(maxMessageSize * 3);
    cf.setRequestedFrameMax(maxFrameSize * 3);
    try (Connection conn = cf.newConnection()) {
      AtomicReference<String> receivedMessageId = new AtomicReference<>();
      Channel ch = conn.createChannel();
      CountDownLatch consumeLatch = new CountDownLatch(1);
      ch.basicConsume(
          q,
          true,
          (consumerTag, message) -> {
            receivedMessageId.set(message.getProperties().getMessageId());
            consumeLatch.countDown();
          },
          consumerTag -> {});

      assertThat(consumeLatch).is(completed());
      assertThat(receivedMessageId).hasValue(messageId);
    }
  }

  @Override
  protected void releaseResources() throws IOException {
    deleteQueue(q);
    super.releaseResources();
  }
}
