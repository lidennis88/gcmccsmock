/*
 * Copyright 2015, Dennis Li
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


import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.vysper.xml.fragment.XMLElement;
import org.apache.vysper.xml.fragment.XMLSemanticError;
import org.apache.vysper.xmpp.addressing.Entity;
import org.apache.vysper.xmpp.addressing.EntityFormatException;
import org.apache.vysper.xmpp.modules.core.base.handler.DefaultMessageHandler;
import org.apache.vysper.xmpp.protocol.NamespaceURIs;
import org.apache.vysper.xmpp.server.ServerRuntimeContext;
import org.apache.vysper.xmpp.server.SessionContext;
import org.apache.vysper.xmpp.stanza.*;
import org.json.simple.JSONValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.json.simple.JSONObject;


/**
 * GCMMessageHandler
 *
 * @author dennisli
 * 7/9/15.
 */

public class GCMMessageHandler extends DefaultMessageHandler {

  final Logger logger = LoggerFactory.getLogger(GCMMessageHandler.class);

  // Ack delay in milliseconds (randomized)
  private static final long ACK_DELAY_MS = 1000;
  // delivery receipt delay in milliseconds (randomized)
  private static final long DELIVERY_RECEIPT_DELAY_MS = 2000;

  private Entity moduleDomain;

  public static final String JSON_MESSAGE_TYPE = "message_type";
  public static final String JSON_MESSAGE_ID = "message_id";
  public static final String JSON_FROM = "from";
  public static final String JSON_TO = "to";
  public static final String JSON_RECEIPT = "receipt";
  public static final String JSON_CATEGORY = "category";
  public static final String JSON_ACK = "ack";
  public static final String JSON_NACK = "nack";
  public static final String JSON_CONTROL = "control";
  public static final String JSON_CONTROL_TYPE = "control_type";
  public static final String JSON_MESSAGE_STATUS = "message_status";
  public static final String JSON_MESSAGE_STATUS_SENT_TO_DEVICE = "MESSAGE_SENT_TO_DEVICE";
  public static final String JSON_ORIGINAL_MESSAGE_ID = "original_message_id";
  public static final String JSON_REGISTRATION_ID = "REGISTRATION_ID";
  public static final String JSON_DEVICE_REG_ID = "device_registration_id";
  public static final String JSON_SENT_TIMESTAMP = "message_sent_timestamp";
  public static final String JSON_ERROR = "error";
  public static final String JSON_ERROR_BAD_REGISTRATION = "BAD_REGISTRATION";
  public static final String JSON_ERROR_DESCRIPTION = "error_description";
  
  private ServiceContext serviceContext;
  private String badRegistrationTag;
  private String drainingTag;

  public GCMMessageHandler(ServiceContext serviceContext, Entity moduleDomain) {
    this.serviceContext = serviceContext;
    this.moduleDomain = moduleDomain;
    Config config = serviceContext.getConfig();
    this.badRegistrationTag = config.getProperty("bad.registration.id", "BAD_REGISTRATION");
    this.drainingTag = config.getProperty("draining.registration.id", "DRAIN_ME");
  }

  @Override
  protected boolean verifyNamespace(Stanza stanza) {
    return verifyInnerNamespace(stanza, Constants.NAME_SPACE_GCM);
  }

  @Override
  protected Stanza executeCore(XMPPCoreStanza coreStanza, ServerRuntimeContext serverRuntimeContext,
                               boolean isOutboundStanza, SessionContext sessionContext) {
    MessageStanza stanza = (MessageStanza) coreStanza;

    // seems to be a Vysper bug. isOutbounceStanza really means the opposite
    if (isOutboundStanza)
      return executeMessageLogic(stanza, serverRuntimeContext, sessionContext);
    return coreStanza;
  }

  @Override
  protected Stanza executeMessageLogic(MessageStanza stanza, ServerRuntimeContext serverRuntimeContext,
                                       SessionContext sessionContext) {

    try {
      String incomingMessage = stanza.getInnerElements().get(0).getSingleInnerText().getText();
      logger.info("Received message. stanza.gcm={}", incomingMessage);
      Entity from = extractSenderJID(stanza, sessionContext);

      MessageStanzaType type = stanza.getMessageType();
      if (!MessageStanzaType.NORMAL.equals(type))
        return null; // no immediate response

      GCMMessage gcmMessage = GCMMessage.fromStanza(stanza);
      JSONObject jsonObject = gcmMessage.getJsonObject();

      if (jsonObject.get(JSON_TO).equals(badRegistrationTag)) {
        Stanza outboundStanza = createNackBadRegIdMessageStanza(stanza, jsonObject);
        MessageRelayJobImpl messageRelayJob = new MessageRelayJobImpl(ThreadLocalRandom.current().nextLong(ACK_DELAY_MS), from, outboundStanza, serverRuntimeContext);
        serviceContext.getMessageRelayManager().addJob(messageRelayJob);
        return null;
      }

      Stanza outboundStanza = createAckMessageStanza(stanza, jsonObject);
      MessageRelayJobImpl messageRelayJob = new MessageRelayJobImpl(ThreadLocalRandom.current().nextLong(ACK_DELAY_MS), from, outboundStanza, serverRuntimeContext);
      serviceContext.getMessageRelayManager().addJob(messageRelayJob);

      outboundStanza = createDeliveryReceiptMessageStanza(stanza, jsonObject);
      messageRelayJob = new MessageRelayJobImpl(ThreadLocalRandom.current().nextLong(DELIVERY_RECEIPT_DELAY_MS), from, outboundStanza, serverRuntimeContext,
          (String)jsonObject.get(JSON_TO), incomingMessage);
      serviceContext.getMessageRelayManager().addJob(messageRelayJob);

      // draining control message test
      if (jsonObject.get(JSON_TO).equals(drainingTag)) {
        outboundStanza = createDrainingMessageStanza(stanza, jsonObject);
        messageRelayJob = new MessageRelayJobImpl(500, from, outboundStanza, serverRuntimeContext,
            (String)jsonObject.get(JSON_TO), incomingMessage);
        serviceContext.getMessageRelayManager().addJob(messageRelayJob);
      }
    } catch (Exception ex) {
      logger.error("got exception: ", ex);
    }
    return null;
  }

  /**
   * Create Ack Message

   <message id="">
     <gcm xmlns="google:mobile:data">
     {
       "from":"REGID",
       "message_id":"m-1366082849205"
       "message_type":"ack"
     }
     </gcm>
   </message>

   * @param original
   * @param jsonObject
   * @return
   * @throws EntityFormatException
   */
  private static Stanza createAckMessageStanza(Stanza original, JSONObject jsonObject) throws EntityFormatException {
    Map<String, Object> message = new HashMap<>();
    message.put(JSON_MESSAGE_TYPE, JSON_ACK);
    message.put(JSON_FROM, jsonObject.get(JSON_TO));
    message.put(JSON_MESSAGE_ID, jsonObject.get(JSON_MESSAGE_ID));
    String payload = JSONValue.toJSONString(message);

    // no from & to
    StanzaBuilder builder = new StanzaBuilder("message");
    builder.addAttribute("id", original.getAttributeValue("id") == null ? "": original.getAttributeValue("id"));
    GCMMessage.Builder gcmMessageBuilder = new GCMMessage.Builder();
    gcmMessageBuilder.addText(payload);
    builder.addPreparedElement(gcmMessageBuilder.build());
    return builder.build();
  }

  /**
   * Create Bad RegId Nack Message Stanza
   *
   <message>
     <gcm xmlns="google:mobile:data">
     {
       "message_type":"nack",
       "message_id":"msgId1",
       "from":"SomeInvalidRegistrationId",
       "error":"BAD_REGISTRATION",
       "error_description":"Invalid token on 'to' field: SomeInvalidRegistrationId"
     }
     </gcm>
   </message>

   * @param original
   * @param jsonObject
   * @return
   * @throws EntityFormatException
   */
  private static Stanza createNackBadRegIdMessageStanza(Stanza original, JSONObject jsonObject) throws EntityFormatException {

    Map<String, Object> message = new HashMap<>();
    message.put(JSON_MESSAGE_TYPE, JSON_NACK);
    message.put(JSON_FROM, jsonObject.get(JSON_TO));
    message.put(JSON_MESSAGE_ID, jsonObject.get(JSON_MESSAGE_ID));
    message.put(JSON_ERROR, JSON_ERROR_BAD_REGISTRATION);
    message.put(JSON_ERROR_DESCRIPTION, "Invalid token on 'to' field: "+jsonObject.get(JSON_TO));

    String payload = JSONValue.toJSONString(message);

    StanzaBuilder builder = new StanzaBuilder("message");
    builder.addAttribute("id", original.getAttributeValue("id") == null ? "": original.getAttributeValue("id"));
    GCMMessage.Builder gcmMessageBuilder = new GCMMessage.Builder();
    gcmMessageBuilder.addText(payload);
    builder.addPreparedElement(gcmMessageBuilder.build());
    return builder.build();
  }

  /**
   * Create Delivery Receipt

   <message id="">
     <gcm xmlns="google:mobile:data">
     {
       "category":"com.example.yourapp", // to know which app sent it
       "data":
       {
         “message_status":"MESSAGE_SENT_TO_DEVICE",
         “original_message_id”:”m-1366082849205”,
         “device_registration_id”: “REGISTRATION_ID”,
         "message_sent_timestamp": "1430277821658"
       },
       "message_id":"dr2:m-1366082849205",
       "message_type":"receipt",
       "time_to_live": 0,
       "from":"gcm.googleapis.com"
     }
     </gcm>
   </message>

   * @param original
   * @param jsonObject
   * @return
   * @throws EntityFormatException
   */
  private static Stanza createDeliveryReceiptMessageStanza(Stanza original, JSONObject jsonObject) throws EntityFormatException {

    Map<String, Object> message = new HashMap<>();
    message.put(JSON_MESSAGE_TYPE, JSON_RECEIPT);
    message.put(JSON_FROM, "gcm.googleapis.com");
    // TODO made up
    message.put(JSON_CATEGORY, "com.itsoninc.client");
    message.put(JSON_MESSAGE_ID, "dr2:"+jsonObject.get(JSON_MESSAGE_ID));
    Map<String, Object> data = new HashMap<>();
    data.put(JSON_MESSAGE_STATUS, JSON_MESSAGE_STATUS_SENT_TO_DEVICE);
    data.put(JSON_ORIGINAL_MESSAGE_ID, jsonObject.get(JSON_MESSAGE_ID));
    data.put(JSON_DEVICE_REG_ID, jsonObject.get(JSON_TO));
    data.put(JSON_SENT_TIMESTAMP, Long.toString(System.currentTimeMillis()));
    message.put("data", data);
    String payload = JSONValue.toJSONString(message);

    StanzaBuilder builder = new StanzaBuilder("message");
    builder.addAttribute("id", original.getAttributeValue("id") == null ? "": original.getAttributeValue("id"));
    GCMMessage.Builder gcmMessageBuilder = new GCMMessage.Builder();
    gcmMessageBuilder.addText(payload);
    builder.addPreparedElement(gcmMessageBuilder.build());
    return builder.build();
  }

  /**
   * Create Draining Control Message

   <message>
     <data:gcm xmlns:data="google:mobile:data">
     {
       "message_type":"control"
       "control_type":"CONNECTION_DRAINING"
     }
     </data:gcm>
   </message>

   * @param original
   * @param jsonObject
   * @return
   * @throws EntityFormatException
   */
  private static Stanza createDrainingMessageStanza(Stanza original, JSONObject jsonObject) throws EntityFormatException {
    Map<String, Object> message = new HashMap<>();
    message.put(JSON_MESSAGE_TYPE, JSON_CONTROL);
    message.put(JSON_CONTROL_TYPE, "CONNECTION_DRAINING");
    String payload = JSONValue.toJSONString(message);

    StanzaBuilder builder = new StanzaBuilder("message");
    builder.addAttribute("id", original.getAttributeValue("id") == null ? "": original.getAttributeValue("id"));
    GCMMessage.Builder gcmMessageBuilder = new GCMMessage.Builder();
    gcmMessageBuilder.addText(payload);
    builder.addPreparedElement(gcmMessageBuilder.build());
    return builder.build();
  }

  private String getFieldValue(List<XMLElement> fields, String var) {
    for(XMLElement field : fields) {
      if(var.equals(field.getAttributeValue("var"))) {
        try {
          return field.getSingleInnerElementsNamed("value", NamespaceURIs.JABBER_X_DATA).getInnerText().getText();
        } catch (XMLSemanticError e) {
          return null;
        }
      }
    }
    return null;

  }

  private Stanza createMessageErrorStanza(Entity from, Entity to, String id, StanzaErrorType type,
                                          StanzaErrorCondition errorCondition, Stanza stanza) {
    return createErrorStanza("message", NamespaceURIs.JABBER_CLIENT, from, to, id, type.value(),
        errorCondition.value(), stanza.getInnerElements());
  }

  private Stanza createErrorStanza(String stanzaName, String namespaceUri, Entity from, Entity to, String id,
                                         String type, String errorName, List<XMLElement> innerElements) {

    StanzaBuilder builder = new StanzaBuilder(stanzaName, namespaceUri);
    builder.addAttribute("from", from.getFullQualifiedName());
    builder.addAttribute("to", to.getFullQualifiedName());
    if (id != null)
      builder.addAttribute("id", id);
    builder.addAttribute("type", "error");

    if (innerElements != null) {
      for (XMLElement innerElement : innerElements) {
        builder.addPreparedElement(innerElement);
      }
    }

    builder.startInnerElement("error", NamespaceURIs.JABBER_CLIENT).addAttribute("type", type);
    builder.startInnerElement(errorName, NamespaceURIs.URN_IETF_PARAMS_XML_NS_XMPP_STANZAS).endInnerElement();
    builder.endInnerElement();

    return builder.build();
  }

  protected Stanza createErrorReply(Stanza originalStanza, StanzaErrorType type, StanzaErrorCondition error) {
    StanzaBuilder builder = new StanzaBuilder(originalStanza.getName(), originalStanza.getNamespaceURI());
    builder.addAttribute("from", originalStanza.getTo().getFullQualifiedName());
    builder.addAttribute("to", originalStanza.getFrom().getFullQualifiedName());
    builder.addAttribute("id", originalStanza.getAttributeValue("id"));
    builder.addAttribute("type", "error");

    for (XMLElement inner : originalStanza.getInnerElements()) {
      builder.addPreparedElement(inner);
    }

    builder.startInnerElement("error", NamespaceURIs.JABBER_CLIENT).addAttribute("type", type.value());
    builder.startInnerElement(error.value(), NamespaceURIs.URN_IETF_PARAMS_XML_NS_XMPP_STANZAS).endInnerElement();
    builder.endInnerElement();

    return builder.build();
  }
}
