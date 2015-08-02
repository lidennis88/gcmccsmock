/*
 * Copyright 2014, Dennis Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jql.gcmccsmock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.DelayQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Singleton;
import javax.inject.Inject;

/**
 * Message Relay Manager to regulate the outbound message/stanza delivery
 * to the GCM provider
 *
 * @author dennisli
 * 7/9/15.
 */
@Singleton
public class MessageRelayManager {
  final Logger logger = LoggerFactory.getLogger(MessageRelayManager.class);

  private final ExecutorService executorService;
  private static final int EXECUTOR_POOL_SIZE = 6;

  private final DelayQueue<ScheduledJob> queue;
  private Future<?> schedulerFuture;
  private AtomicBoolean keepRunning;
  @Inject GCMMessageStore gcmMessageStore;

  @Inject
  public MessageRelayManager() {
    this.queue = new DelayQueue<>();
    this.executorService = Executors.newFixedThreadPool(EXECUTOR_POOL_SIZE);
    this.keepRunning = new AtomicBoolean(true);
    schedulerFuture = executorService.submit(new JobScheduler(this));
  }

  public GCMMessageStore getGCMMessageStore() {
    return gcmMessageStore;
  }

  public boolean shutdown() {
    this.keepRunning.set(false);
    gcmMessageStore.shutdown();
    queue.clear();
    schedulerFuture.cancel(true);
    return true;
  }

  public boolean addJob(ScheduledJob job) {
    return queue.add(job);
  }

  private class JobScheduler implements Runnable {
    private MessageRelayManager messageRelayManager;

    public JobScheduler(MessageRelayManager relayManager) {
      this.messageRelayManager = relayManager;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void run() {
      while (keepRunning.get()) {
        try {
          final ScheduledJob scheduledJob = queue.take();
          executorService.execute(new Runnable() {
            @Override
            public void run() {
              scheduledJob.execute(messageRelayManager);
            }
          });
        } catch (Exception e) {
          logger.error("JobScheduler.run() got exception: {}", e);
          // ignore for now
        }
      }
    }
  }
}
