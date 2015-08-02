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


import org.apache.vysper.xml.fragment.XMLSemanticError;
import org.apache.vysper.xmpp.addressing.Entity;
import org.apache.vysper.xmpp.delivery.failure.DeliveryException;
import org.apache.vysper.xmpp.delivery.failure.IgnoreFailureStrategy;
import org.apache.vysper.xmpp.server.ServerRuntimeContext;
import org.apache.vysper.xmpp.stanza.Stanza;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ScheduledJob for relaying an outbound message/stanza to the provider
 *
 * @author dennisli
 * 7/12/15.
 */
public class MessageRelayJobImpl extends ScheduledJob {
  final Logger logger = LoggerFactory.getLogger(MessageRelayJobImpl.class);

  private Entity receiver;
  private Stanza stanza;
  private ServerRuntimeContext serverRuntimeContext;
  private String regId;
  private String incomingMessage;

  public MessageRelayJobImpl(long delay, Entity receiver, Stanza stanza, ServerRuntimeContext serverRuntimeContext) {
    super(delay);
    this.receiver = receiver;
    this.stanza = stanza;
    this.serverRuntimeContext = serverRuntimeContext;
  }

  public MessageRelayJobImpl(long delay, Entity receiver, Stanza stanza, ServerRuntimeContext serverRuntimeContext, String regId, String incomingMessage) {
    this(delay, receiver, stanza, serverRuntimeContext);
    this.regId = regId;
    this.incomingMessage = incomingMessage;
  }

  @Override
  public boolean execute(MessageRelayManager messageRelayManager) {
    try {
      if (incomingMessage != null) {
        logger.debug("storing message into message store. regId={}, incomingMessage={}", regId, incomingMessage);
        messageRelayManager.getGCMMessageStore().storeMessage(regId, incomingMessage);
      }
      logger.info("relaying message: {}", stanza.getInnerElements().get(0).getSingleInnerText().getText());
      serverRuntimeContext.getStanzaRelay().relay(receiver, stanza, new IgnoreFailureStrategy());
    } catch (DeliveryException | XMLSemanticError e) {
      logger.warn("failed to relay message", e);
      return false;
    }

    return true;
  }
}
