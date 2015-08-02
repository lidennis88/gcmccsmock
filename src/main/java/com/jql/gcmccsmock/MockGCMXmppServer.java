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

import java.io.FileInputStream;
import java.io.InputStream;
import javax.inject.Inject;

import dagger.ObjectGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.vysper.mina.TCPEndpoint;
import org.apache.vysper.storage.StorageProviderRegistry;
import org.apache.vysper.storage.inmemory.MemoryStorageProviderRegistry;
import org.apache.vysper.xmpp.authorization.AccountManagement;
import org.apache.vysper.xmpp.addressing.EntityImpl;
import org.apache.vysper.xmpp.server.XMPPServer;


/**
 * Mock GCM CCS/XMPP Server
 *
 * @author dennisli
 * 7/8/2015
 */
public class MockGCMXmppServer {

  private static final Logger logger = LoggerFactory.getLogger(MockGCMXmppServer.class);

  private static final String DEFAULT_PORT = "5235";
  private static final String DEFAULT_CERT_FILE = "mockgcm.cert";

  public static final String REGEX_COMMA = "\\s*,\\s*";

  @Inject ServiceContext serviceContext;

  public MockGCMXmppServer() {
  }

  public void run() {

    try {
      StorageProviderRegistry providerRegistry = new MemoryStorageProviderRegistry();
      Config config = serviceContext.getConfig();
      String domain = config.getProperty("xmpp.domain");
      if (domain == null || domain.isEmpty()) {
        throw new IllegalArgumentException("null xmpp.domain");
      }
      final AccountManagement accountManagement = (AccountManagement) providerRegistry.retrieve(AccountManagement.class);
      String usersString = config.getProperty("xmpp.users");
      String passwordsString = config.getProperty("xmpp.passwords");
      if (usersString == null || passwordsString == null || usersString.isEmpty() || passwordsString.isEmpty()) {
        throw new IllegalArgumentException("null xmpp.users or xmpp.passwords");
      }
      String[] users = usersString.split(REGEX_COMMA);
      String[] passwords = passwordsString.split(REGEX_COMMA);
      if (users.length != passwords.length) {
        throw new IllegalArgumentException("xmpp.users and xmpp.passwords do not match");
      }
      if (users.length == 0) {
        throw new IllegalArgumentException("xmpp.users is not configured");
      }
      for (int i = 0; i < users.length; i++) {
        accountManagement.addUser(EntityImpl.parse(users[i] + "@" + domain), passwords[i]);
      }

      int port = Integer.parseInt(config.getProperty("xmpp.port", DEFAULT_PORT));
      final XMPPServer server = new XMPPServer(domain);
      TCPEndpoint tcpEndpoint = new TCPEndpoint();
      tcpEndpoint.setPort(port);
      server.addEndpoint(tcpEndpoint);
      server.setStorageProviderRegistry(providerRegistry);

      final ClassLoader resourceLoader = Thread.currentThread().getContextClassLoader();
      String certFile = config.getProperty("keystore.path", DEFAULT_CERT_FILE);
      try (InputStream is = (certFile.contains("/")) ? new FileInputStream(certFile) :
          resourceLoader.getResourceAsStream(certFile)) {
        server.setTLSCertificateInfo(is, config.getProperty("keystore.password", "password"));
        server.start();
        server.addModule(new GCMModule(serviceContext));
        logger.info("server started at port: {}", port);
      }

      Runtime.getRuntime().addShutdownHook(new Thread() {
        @Override
        public void run() {
          try {
            server.stop();
            serviceContext.getMessageRelayManager().shutdown();
            logger.info("mock GCM XMPP server is shut down.");
          } catch (Exception ex) {
            logger.error("Caught an exception during shutdown", ex);
          }
        }
      });

    } catch (Exception ex) {
      logger.error("Caught exception", ex);
    }
  }

  public static void main(String[] argv) {
    ObjectGraph objectGraph = ObjectGraph.create(new MockServerModule());
    MockGCMXmppServer mockGCMXmppServer = objectGraph.get(MockGCMXmppServer.class);
    mockGCMXmppServer.run();
  }
}