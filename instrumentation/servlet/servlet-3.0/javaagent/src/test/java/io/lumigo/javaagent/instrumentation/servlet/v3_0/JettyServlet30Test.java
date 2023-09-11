/*
 * Copyright 2023 Lumigo LTD
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.lumigo.javaagent.instrumentation.servlet.v3_0;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

public class JettyServlet30Test extends AbstractServlet30Test {

  private static Server server;

  @BeforeAll
  static void setUp() throws Exception {
    ServletContextHandler handler = new ServletContextHandler();

    setupServlets(handler::addServlet);

    server = new Server(0);
    server.setHandler(handler);
    server.start();
    int serverPort = server.getConnectors()[0].getLocalPort();
    buildUrl(serverPort);
  }

  @AfterAll
  static void tearDown() throws Exception {
    server.stop();
  }
}
