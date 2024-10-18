package io.opentelemetry.javaagent.instrumentation.spring.webmvc.v3_1.util;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

public class CachedBodyHttpServletResponse extends HttpServletResponseWrapper {

  private final ByteArrayOutputStream cachedBodyOutputStream = new ByteArrayOutputStream();
  private final PrintWriter printWriter = new PrintWriter(cachedBodyOutputStream);

  public CachedBodyHttpServletResponse(HttpServletResponse response) {
    super(response);
  }

  public String getResponseBody() {
    return cachedBodyOutputStream.toString();
  }

  @Override
  public ServletOutputStream getOutputStream() throws IOException {
    return new ServletOutputStream() {
      @Override
      public void write(int b) throws IOException {
        cachedBodyOutputStream.write(b);
      }

      @Override
      public boolean isReady() {
        return true;
      }

      @Override
      public void setWriteListener(WriteListener listener) {}
    };
  }

  @Override
  public PrintWriter getWriter() throws IOException {
    return printWriter;
  }

  @Override
  public void flushBuffer() throws IOException {
    printWriter.flush();
    super.flushBuffer();
  }
}

