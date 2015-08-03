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


import java.util.List;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.vysper.xmpp.addressing.Entity;
import org.apache.vysper.xmpp.addressing.EntityUtils;
import org.apache.vysper.xmpp.modules.DefaultDiscoAwareModule;
import org.apache.vysper.xmpp.modules.servicediscovery.management.ComponentInfoRequestListener;
import org.apache.vysper.xmpp.modules.servicediscovery.management.InfoElement;
import org.apache.vysper.xmpp.modules.servicediscovery.management.InfoRequest;
import org.apache.vysper.xmpp.modules.servicediscovery.management.Item;
import org.apache.vysper.xmpp.modules.servicediscovery.management.ItemRequestListener;
import org.apache.vysper.xmpp.modules.servicediscovery.management.ServiceDiscoveryRequestException;
import org.apache.vysper.xmpp.protocol.HandlerDictionary;
import org.apache.vysper.xmpp.protocol.NamespaceHandlerDictionary;
import org.apache.vysper.xmpp.protocol.StanzaProcessor;
import org.apache.vysper.xmpp.server.ServerRuntimeContext;
import org.apache.vysper.xmpp.server.components.Component;
import org.apache.vysper.xmpp.server.components.ComponentStanzaProcessor;


/**
 * GCMModule to add GCM extension to XMPP
 *
 * @author dennisli
 * 7/10/15.
 */
@Singleton
public class GCMModule extends DefaultDiscoAwareModule implements Component, ComponentInfoRequestListener,
    ItemRequestListener {

  private final Logger logger = LoggerFactory.getLogger(GCMModule.class);

  private String subdomain = "gcm";
  private Entity fullDomain;
  private ServerRuntimeContext serverRuntimeContext;
  private ComponentStanzaProcessor stanzaProcessor;
  private GCMMessageHandler messageHandler;

  private ServiceContext serviceContext;

  public GCMModule(ServiceContext serviceContext) {
    this.serviceContext = serviceContext;
  }

  /**
   * Initializes the module, configuring the storage providers.
   */
  @Override
  public void initialize(ServerRuntimeContext serverRuntimeContext) {
    super.initialize(serverRuntimeContext);
    this.serverRuntimeContext = serverRuntimeContext;

    fullDomain = EntityUtils.createComponentDomain(subdomain, serverRuntimeContext);

    this.stanzaProcessor = new ComponentStanzaProcessor(serverRuntimeContext);
    stanzaProcessor.addHandler(getMessageHandler());

    logger.info("GCMModule is initialized");
  }

  @Override
  public String getName() {
    return "GCM";
  }

  @Override
  public String getVersion() {
    return "1.0";
  }

  /**
   * Make this object available for disco#items requests.
   */
  @Override
  protected void addItemRequestListeners(List<ItemRequestListener> itemRequestListeners) {
    itemRequestListeners.add(this);
  }

  public List<InfoElement> getComponentInfosFor(InfoRequest request) throws ServiceDiscoveryRequestException {
    if (!fullDomain.getDomain().equals(request.getTo().getDomain()))
      return null;

    logger.info("GCMModule.getComponentInfosFor(): {}", request.toString());

    // TODO
    return null;
  }

  @Override
  protected void addComponentInfoRequestListeners(List<ComponentInfoRequestListener> componentInfoRequestListeners) {
    componentInfoRequestListeners.add(this);
  }

  /**
   * Implements the getItemsFor method from the {@link ItemRequestListener} interface.
   * Makes this modules available via disco#items and returns the associated nodes.
   *
   * @see ItemRequestListener#getItemsFor(InfoRequest)
   */
  public List<Item> getItemsFor(InfoRequest request) throws ServiceDiscoveryRequestException {
    Entity to = request.getTo();
    // TODO
    return null;
  }

  public String getSubdomain() {
    return subdomain;
  }

  public StanzaProcessor getStanzaProcessor() {
    return stanzaProcessor;
  }

  @Override
  protected void addHandlerDictionaries(List<HandlerDictionary> dictionary) {
    logger.info("GCMModule.addHandlerDictionaries()");
    dictionary.add(new NamespaceHandlerDictionary(Constants.NAME_SPACE_GCM, getMessageHandler()));
  }

  private synchronized GCMMessageHandler getMessageHandler() {
    if (this.messageHandler == null)
      this.messageHandler = new GCMMessageHandler(serviceContext, fullDomain);
    return this.messageHandler;
  }
}
