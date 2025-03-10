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
package io.lumigo.spring.webflux;

public class Greeting {
  private int count;
  private String message;

  public int getCount() {
    return count;
  }

  public Greeting setCount(int count) {
    this.count = count;
    return this;
  }

  public String getMessage() {
    return message;
  }

  public Greeting setMessage(String message) {
    this.message = message;
    return this;
  }
}
