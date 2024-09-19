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
package io.lumigo.javaagent.smoketest;

import static io.lumigo.javaagent.common.ProcessEnvironmentScrubber.LUMIGO_SECRET_MASKING_REGEX_ENVIRONMENT;
import static io.lumigo.javaagent.common.SecretScrubber.DEFAULT_REGEX_KEYS;
import static io.lumigo.javaagent.common.SecretScrubber.LUMIGO_SECRET_MASKING_ALL_MAGIC;
import static io.lumigo.javaagent.common.SecretScrubber.LUMIGO_SECRET_MASKING_REGEX;
import static io.lumigo.javaagent.common.SecretScrubber.SCRUBBED_VALUE;
import static io.lumigo.javaagent.resources.ProcessEnvironmentResource.PROCESS_ENVIRONMENT;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.lumigo.javaagent.junitextensions.OkHttpClientExtension;
import io.lumigo.javaagent.junitextensions.TestAppExtension;
import io.lumigo.javaagent.junitextensions.TestAppExtension.Configuration;
import io.lumigo.javaagent.junitextensions.TestAppExtension.EnvVar;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith({TestAppExtension.class, OkHttpClientExtension.class})
class EnvironmentVariableSmokeTest {
  JsonFactory JSON_FACTORY = new JsonFactory();

  private static final String SECRET_KEY = "secret.key";
  private static final String SECRET_VALUE = "MY_SECRET";
  private static final String CREDENTIAL_KEY = "credential.key";
  private static final String CREDENTIAL_VALUE = "MY_CREDENTIAL";
  private static final String MY_KEY = "my.special";
  private static final String MY_VALUE = "my.value";

  protected void executeTest(
      TestAppExtension.TestApplication target,
      final OkHttpClient client,
      Consumer<List<JsonNode>> assertions)
      throws IOException {

    assertThat(
        target.getLogs(),
        containsString("SimpleSpanProcessor{spanExporter=io.lumigo.javaagent.FileSpanExporter"));

    final String url = String.format("http://localhost:%d/greeting", target.getMappedPort(8080));
    final Request request = new Request.Builder().url(url).get().build();

    try (final Response response = client.newCall(request).execute()) {
      ResponseBody body = response.body();
      assertThat(body, is(notNullValue()));
      assertThat(body.string(), is("Hi!"));
    }

    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              List<JsonNode> traceData = target.getTraces();

              // Verify environment variables are masked
              assertions.accept(traceData);
            });
  }

  protected String getProcessEnvironment(List<JsonNode> traceData) {
    if (traceData.size() == 0) {
      // No traces found
      return null;
    }

    JsonNode jsonNode =
        traceData.get(0).get("resourceSpans").get(0).get("resource").get("attributes");
    if (jsonNode.isArray()) {
      ArrayNode arrayNode = (ArrayNode) jsonNode;
      for (Iterator<JsonNode> it = arrayNode.elements(); it.hasNext(); ) {
        JsonNode node = it.next();
        if (node.get("key").textValue().equals(PROCESS_ENVIRONMENT)) {
          return node.get("value").get("stringValue").asText();
        }
      }
    }

    return null;
  }

  protected void assertTestResults(
      List<JsonNode> traceData,
      String expectedSecretValue,
      String expectedCredentialValue,
      String expectedMyValue) {

    String result = getProcessEnvironment(traceData);
    if (result == null || result.isEmpty()) {
      Assertions.fail();
    }

    String secretKeyValue = null, credentialKeyValue = null, myValue = null;

    try (JsonParser parser = JSON_FACTORY.createParser(result)) {
      while (parser.nextToken() != JsonToken.END_OBJECT) {
        if (parser.getCurrentToken().equals(JsonToken.FIELD_NAME)) {
          if (parser.getCurrentName().equals(SECRET_KEY)) {
            parser.nextValue();
            secretKeyValue = parser.getValueAsString();
          } else if (parser.getCurrentName().equals(CREDENTIAL_KEY)) {
            parser.nextValue();
            credentialKeyValue = parser.getValueAsString();
          } else if (parser.getCurrentName().equals(MY_KEY)) {
            parser.nextValue();
            myValue = parser.getValueAsString();
          }
        }
      }
    } catch (IOException e) {
      Assertions.fail(e);
    }

    assertThat(secretKeyValue, is(expectedSecretValue));
    assertThat(credentialKeyValue, is(expectedCredentialValue));
    assertThat(myValue, is(expectedMyValue));
  }

  @Test
  public void testValidityOfDefaultRegex() {
    List<Pattern> patterns = new ArrayList<>();
    try {
      patterns =
          DEFAULT_REGEX_KEYS.stream()
              .map(expr -> Pattern.compile(expr, Pattern.CASE_INSENSITIVE))
              .collect(Collectors.toList());
    } catch (PatternSyntaxException pse) {
      Assertions.fail(pse);
    }

    assertThat(DEFAULT_REGEX_KEYS.size(), is(patterns.size()));
  }

  @Test
  public void testDefaultScrubbing(
      final @Configuration(
              env = {
                @EnvVar(key = SECRET_KEY, value = SECRET_VALUE),
                @EnvVar(key = CREDENTIAL_KEY, value = CREDENTIAL_VALUE),
                @EnvVar(key = MY_KEY, value = MY_VALUE)
              }) TestAppExtension.TestApplication target,
      final OkHttpClient client)
      throws IOException {
    executeTest(
        target,
        client,
        (traceNodes) -> assertTestResults(traceNodes, SCRUBBED_VALUE, SCRUBBED_VALUE, MY_VALUE));
  }

  @Test
  public void testDefaultScrubbingWithInvalidJson(
      final @Configuration(
              env = {
                @EnvVar(key = SECRET_KEY, value = SECRET_VALUE),
                @EnvVar(key = CREDENTIAL_KEY, value = CREDENTIAL_VALUE),
                @EnvVar(key = MY_KEY, value = MY_VALUE),
                @EnvVar(key = LUMIGO_SECRET_MASKING_REGEX, value = "['.*\"my.*']")
              }) TestAppExtension.TestApplication target,
      final OkHttpClient client)
      throws IOException {
    executeTest(
        target,
        client,
        (traceNodes) -> {
          assertTestResults(traceNodes, SCRUBBED_VALUE, SCRUBBED_VALUE, MY_VALUE);

          assertThat(target.getLogs(), containsString("Failed to parse the regex:"));
        });
  }

  @Test
  public void testDefaultScrubbingWithInvalidRegexp(
      final @Configuration(
              env = {
                @EnvVar(key = SECRET_KEY, value = SECRET_VALUE),
                @EnvVar(key = CREDENTIAL_KEY, value = CREDENTIAL_VALUE),
                @EnvVar(key = MY_KEY, value = MY_VALUE),
                @EnvVar(key = LUMIGO_SECRET_MASKING_REGEX, value = "[\"(ad\"]")
              }) TestAppExtension.TestApplication target,
      final OkHttpClient client)
      throws IOException {
    executeTest(
        target,
        client,
        (traceNodes) -> {
          assertTestResults(traceNodes, SCRUBBED_VALUE, SCRUBBED_VALUE, MY_VALUE);

          assertThat(target.getLogs(), containsString("Failed to parse the regex:"));
        });
  }

  @Test
  public void testEnvironmentSpecificRegexAndNotGeneralRegexScrubbing(
      final @Configuration(
              env = {
                @EnvVar(key = SECRET_KEY, value = SECRET_VALUE),
                @EnvVar(key = CREDENTIAL_KEY, value = CREDENTIAL_VALUE),
                @EnvVar(key = MY_KEY, value = MY_VALUE),
                @EnvVar(key = LUMIGO_SECRET_MASKING_REGEX, value = LUMIGO_SECRET_MASKING_ALL_MAGIC),
                @EnvVar(key = LUMIGO_SECRET_MASKING_REGEX_ENVIRONMENT, value = "[\".*special.*\"]")
              }) TestAppExtension.TestApplication target,
      final OkHttpClient client)
      throws IOException {
    executeTest(
        target,
        client,
        (traceNodes) ->
            assertTestResults(traceNodes, SECRET_VALUE, CREDENTIAL_VALUE, SCRUBBED_VALUE));
  }

  @Test
  public void testEmptyEnvironmentSpecificRegexScrubbing(
      final @Configuration(
              env = {
                @EnvVar(key = SECRET_KEY, value = SECRET_VALUE),
                @EnvVar(key = CREDENTIAL_KEY, value = CREDENTIAL_VALUE),
                @EnvVar(key = MY_KEY, value = MY_VALUE),
                @EnvVar(key = LUMIGO_SECRET_MASKING_REGEX_ENVIRONMENT, value = "[]")
              }) TestAppExtension.TestApplication target,
      final OkHttpClient client)
      throws IOException {
    executeTest(
        target,
        client,
        (traceNodes) -> assertTestResults(traceNodes, SECRET_VALUE, CREDENTIAL_VALUE, MY_VALUE));
  }

  @Test
  public void testAllMagicScrubbing(
      final @Configuration(
              env = {
                @EnvVar(key = SECRET_KEY, value = SECRET_VALUE),
                @EnvVar(key = CREDENTIAL_KEY, value = CREDENTIAL_VALUE),
                @EnvVar(key = MY_KEY, value = MY_VALUE),
                @EnvVar(
                    key = LUMIGO_SECRET_MASKING_REGEX_ENVIRONMENT,
                    value = LUMIGO_SECRET_MASKING_ALL_MAGIC)
              }) TestAppExtension.TestApplication target,
      final OkHttpClient client)
      throws IOException {
    executeTest(
        target,
        client,
        (traceNodes) -> assertThat(getProcessEnvironment(traceNodes), is(SCRUBBED_VALUE)));
  }
}
