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

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration initialized from the config file
 *
 * @author dennisli
 * 5/8/15.
 */
@Singleton
public class Config extends Properties {
  private static final Logger logger = LoggerFactory.getLogger(MockGCMXmppServer.class);

  private static final String DEFAULT_CONFIG_FILE = "config.properties";

  @Inject
  public Config() {
    String configFile = System.getProperty("configFile", DEFAULT_CONFIG_FILE);
    final ClassLoader resourceLoader = Thread.currentThread().getContextClassLoader();
    try (InputStream is = (configFile.contains("/")) ?
        new FileInputStream(configFile) : resourceLoader.getResourceAsStream(configFile)) {
      this.load(is);
    } catch (Exception ex) {
      logger.error("config init error: ", ex);
    }
  }
}
