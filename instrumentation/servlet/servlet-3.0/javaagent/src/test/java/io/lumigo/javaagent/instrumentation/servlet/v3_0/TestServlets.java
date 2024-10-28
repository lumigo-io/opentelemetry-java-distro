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

import java.io.IOException;
import java.util.stream.Stream;
import javax.servlet.AsyncContext;
import javax.servlet.ServletInputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

class   TestServlets {
  @WebServlet
  public static class EchoStream_single_byte_print extends HttpServlet {
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      int body;
      ServletInputStream inputStream = req.getInputStream();
      while ((body = inputStream.read()) != -1) {
        resp.getOutputStream().write(body);
      }
      resp.setStatus(200);
      resp.setContentType("application/json");
    }
  }

  @WebServlet
  public static class EchoStream_byteArray_print extends HttpServlet {
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      int body;
      ServletInputStream inputStream = req.getInputStream();
      byte[] buffer = new byte[1024];
      while ((body = inputStream.read(buffer)) != -1) {
        resp.getOutputStream().write(buffer, 0, body);
      }
      resp.setStatus(200);
      resp.setContentType("application/json");
    }
  }

  @WebServlet
  public static class EchoStream_byteArrayOffset_print extends HttpServlet {
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      int body;
      ServletInputStream inputStream = req.getInputStream();
      byte[] buffer = new byte[1024];
      int offset = 0;
      while ((body = inputStream.read(buffer, offset, 10)) != -1) {
        offset += body;
      }
      resp.setStatus(200);
      resp.setContentType("application/json");
      resp.getOutputStream().write(buffer, 0, offset);
    }
  }

  @WebServlet
  public static class EchoStream_readLine_print extends HttpServlet {
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      String body;
      ServletInputStream inputStream = req.getInputStream();
      byte[] buffer = new byte[1024];
      int read = 0;
      int bytesRead = 0;
      while ((read = inputStream.readLine(buffer, bytesRead, 10)) != -1) {
        bytesRead += read;
      }

      body = new String(buffer, 0, bytesRead);
      resp.setStatus(200);
      resp.setContentType("application/json");
      resp.getOutputStream().print(body);
    }
  }

  @WebServlet
  public static class EchoReader_read_write extends HttpServlet {
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      int body;
      while ((body = req.getReader().read()) != -1) {
        resp.getWriter().write(body);
      }
      resp.setStatus(200);
      resp.setContentType("application/json");
    }
  }

  @WebServlet
  public static class EchoReader_readCharArray_write extends HttpServlet {
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      int body;
      char[] buffer = new char[1024];
      while ((body = req.getReader().read(buffer)) != -1) {
        resp.getWriter().write(buffer, 0, body);
      }
      resp.setStatus(200);
      resp.setContentType("application/json");
    }
  }

  @WebServlet
  public static class EchoReader_readCharArrayOffset_write extends HttpServlet {
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      int body;
      char[] buffer = new char[1024];
      int offset = 0;
      while ((body = req.getReader().read(buffer, offset, 10)) != -1) {
        offset += body;
      }
      resp.setStatus(200);
      resp.setContentType("application/json");
      resp.getWriter().write(buffer, 0, offset - 20);
      resp.getWriter().write(buffer, offset - 20, 20);
    }
  }

  @WebServlet
  public static class EchoReader_readLine_write extends HttpServlet {
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      String body;
      while ((body = req.getReader().readLine()) != null) {
        resp.getWriter().write(body);
      }
      resp.setStatus(200);
      resp.setContentType("application/json");
    }
  }

  @WebServlet
  public static class EchoReader_readLines_write extends HttpServlet {
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      Stream<String> lines = req.getReader().lines();
      lines.forEach(resp.getWriter()::write);
      resp.setStatus(200);
      resp.setContentType("application/json");
    }
  }

  @WebServlet
  public static class EchoReader_readLine_print extends HttpServlet {
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      String body;
      while ((body = req.getReader().readLine()) != null) {
        resp.getWriter().print(body);
      }
      resp.setStatus(200);
      resp.setContentType("application/json");
    }
  }

  @WebServlet
  public static class EchoReader_readLine_printArray extends HttpServlet {
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      String body;
      while ((body = req.getReader().readLine()) != null) {
        resp.getWriter().print(body.toCharArray());
      }
      resp.setStatus(200);
      resp.setContentType("application/json");
    }
  }

  @WebServlet(asyncSupported = true)
  public static class EchoAsyncResponse_stream extends HttpServlet {
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) {
      AsyncContext asyncContext = req.startAsync();
      asyncContext.start(
          () -> {
            try {
              int body;
              byte[] buffer = new byte[1024];
              int offset = 0;
              ServletInputStream inputStream = req.getInputStream();
              while ((body = inputStream.read()) != -1) {
                buffer[offset++] = (byte) body;
              }

              try {
                Thread.sleep(200);
              } catch (InterruptedException e) {
                e.printStackTrace();
              }

              HttpServletResponse response = (HttpServletResponse) asyncContext.getResponse();
              response.setStatus(200);
              response.setContentType("application/json");
              response.getOutputStream().print(new String(buffer, 0, offset));
            } catch (IOException e) {
              e.printStackTrace();
            }
            asyncContext.complete();
          });
    }
  }

  @WebServlet(asyncSupported = true)
  public static class EchoAsyncResponse_writer extends HttpServlet {
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) {
      AsyncContext asyncContext = req.startAsync();
      asyncContext.start(
          () -> {
            try {
              int body;
              char[] buffer = new char[1024];
              int offset = 0;
              while ((body = req.getReader().read()) != -1) {
                buffer[offset++] = (char) body;
              }

              try {
                Thread.sleep(200);
              } catch (InterruptedException e) {
                e.printStackTrace();
              }

              HttpServletResponse response = (HttpServletResponse) asyncContext.getResponse();
              response.setStatus(200);
              response.setContentType("application/json");
              response.getWriter().print(new String(buffer, 0, offset));
            } catch (IOException e) {
              e.printStackTrace();
            }
            asyncContext.complete();
          });
    }
  }
}
