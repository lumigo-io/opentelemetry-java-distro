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

import io.opentelemetry.instrumentation.test.utils.PortUtils;
import java.io.File;
import java.nio.file.Files;
import javax.servlet.Servlet;
import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

public class TomcatServlet30Test extends AbstractServlet30Test {
  private static Tomcat server;

  @BeforeAll
  static void setUp() throws Exception {
    server = new Tomcat();

    File baseDir = Files.createTempDirectory("tomcat").toFile();
    baseDir.deleteOnExit();
    server.setBaseDir(baseDir.getAbsolutePath());

    int serverPort = PortUtils.findOpenPort();
    buildUrl(serverPort);
    server.setPort(serverPort);
    server.getConnector().setEnableLookups(true); // get localhost instead of 127.0.0.1

    File appDir = new File(baseDir, "/webapps/ROOT");
    if (!appDir.exists()) {
      appDir.mkdirs();
      appDir.deleteOnExit();
    }

    Context servletContext = server.addWebapp("", appDir.getAbsolutePath());
    // Speed up start by disabling jar scanning
    servletContext.getJarScanner().setJarScanFilter((jarScanType, jarName) -> false);

    setupServlets((servletClass, path) -> addServlet(servletContext, path, servletClass));

    server.start();
  }

  private static void addServlet(
      Context servletContext, String path, Class<? extends Servlet> servletClass) {
    Wrapper wrapper =
        Tomcat.addServlet(servletContext, servletClass.getSimpleName(), servletClass.getName());
    if (path.contains("async")) {
      wrapper.setAsyncSupported(true);
    }
    servletContext.addServletMappingDecoded(path, servletClass.getSimpleName());
  }

  @AfterAll
  static void tearDown() throws Exception {
    try {
      server.stop();
    } catch (Exception e) {
      // ignore
    }
    server.destroy();
  }
}
