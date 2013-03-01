/*
 * $HeadURL$
 * $Id$
 * Copyright (c) 2006-2013 by Public Library of Science http://plos.org http://ambraproject.org
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ambraproject.queue;

import org.apache.camel.ProducerTemplate;
import org.springframework.beans.factory.annotation.Required;
import org.w3c.dom.Document;

/**
 * Apache Camel sender.
 * @author Dragisa Krsmanovic
 */
public class CamelSender implements MessageSender {

  private ProducerTemplate producerTemplate;

  @Required
  public void setProducerTemplate(ProducerTemplate producerTemplate) {
    this.producerTemplate = producerTemplate;
  }

  public void sendMessage(String destination, String body) {

    System.err.println(String.format("CALLAWAY: CamelSender.sendMessage sending %d bytes to %s",
        body.length(), destination));

    producerTemplate.sendBody(destination, body);
  }

  public void sendMessage(String destination, Document body) {
    producerTemplate.sendBody(destination, body);
  }
}
