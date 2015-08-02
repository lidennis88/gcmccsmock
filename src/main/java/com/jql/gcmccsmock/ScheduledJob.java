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

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * @author dennisli
 * 7/10/15.
 */

public class ScheduledJob implements Delayed {
  private final long scheduledTime; // utc in milliseconds

  public ScheduledJob(long delay) {
    if (delay <= 0) {
      throw new IllegalArgumentException("invalid delay: " + delay);
    }

    this.scheduledTime = System.currentTimeMillis() + delay;
  }

  public long getScheduledTime() {
    return this.scheduledTime;
  }

  @Override
  public int compareTo(Delayed o) {
    if (this == o) {
      return 0;
    }

    if (o == null) {
      throw new IllegalArgumentException("null delayed element");
    }

    long delay = this.getScheduledTime();
    long other = ((ScheduledJob)o).getScheduledTime();
    if (delay == other) {
      return 0;
    } else if (delay > other) {
      return 1;
    } else {
      return -1;
    }
  }

  @Override
  public long getDelay(TimeUnit unit) {
    return unit.convert((this.scheduledTime - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
  }

  /**
   * child class overrides this
   *
   * @return
   */
  public boolean execute(MessageRelayManager messageRelayManager) {
    return true;
  }
}
