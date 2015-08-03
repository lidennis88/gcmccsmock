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

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import org.apache.vysper.xml.fragment.XMLElement;
import org.apache.vysper.xmpp.stanza.Stanza;
import org.apache.vysper.xml.fragment.XMLElementBuilder;

/**
 * GCMMessage extracted out of the incoming stanza
 *
 * @author dennisli
 * 7/9/15.
 */
public class GCMMessage {

  private XMLElement xmlElement;

  public static GCMMessage fromStanza(Stanza stanza) {
    List<XMLElement> xElms = stanza.getInnerElementsNamed("gcm");
    XMLElement xElm = null;
    for (XMLElement elm : xElms) {
      if (elm.getNamespaceURI() != null && elm.getNamespaceURI().startsWith(Constants.NAME_SPACE_GCM)) {
        xElm = elm;
        break;
      }
    }
    if (xElm != null) {
      return new GCMMessage(xElm);
    } else {
      return null;
    }
  }

  public GCMMessage(XMLElement element) {
    this.xmlElement = element;
  }

  public JSONObject getJsonObject() throws Exception {
    JSONParser parser = new JSONParser();
    return (JSONObject) parser.parse(this.xmlElement.getSingleInnerText().getText());
  }

  public static final class Builder extends XMLElementBuilder {
    public Builder() {
      super("gcm", Constants.NAME_SPACE_GCM);
    }
  }
}
