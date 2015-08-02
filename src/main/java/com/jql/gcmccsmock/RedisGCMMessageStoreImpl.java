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

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Jedis;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redis based GCM Message Store
 *
 * @author dennisli
 * 5/10/2015.
 */
public class RedisGCMMessageStoreImpl implements GCMMessageStore {
  private JedisPool pool;
  private AtomicBoolean isActive = new AtomicBoolean(false);

  public RedisGCMMessageStoreImpl(Config config) {
    init(config);
  }

  public void init(Config config) {
    String messageStoreEnabledStr = config.getProperty("message.store.enabled", "true");
    if (!Boolean.parseBoolean(messageStoreEnabledStr))
      return;
    String redisHost = config.getProperty("redis.host");
    String redisPort = config.getProperty("redis.port");
    if (redisHost == null || redisHost.isEmpty()) {
      throw new IllegalArgumentException("null redis.host");
    }
    if (redisPort == null || redisPort.isEmpty()) {
      throw new IllegalArgumentException("null redis.port");
    }
    pool = new JedisPool(
        new JedisPoolConfig(),
        config.getProperty("redis.host"),
        Integer.parseInt(config.getProperty("redis.port")));
    isActive.set(true);
  }

  @Override
  public void shutdown() {
    pool.destroy();
  }

  @Override
  public boolean storeMessage(String regId, String message) {
    if (!isActive.get())
      return false;
    try (Jedis jedis = pool.getResource()) {
      jedis.lpush(regId, message);
    }
    return true;
  }
}
