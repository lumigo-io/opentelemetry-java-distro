/*
 * Copyright 2024 Lumigo LTD
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

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class GreetingController {

  private int count = 0;

  @GetMapping("/hello")
  public Mono<String> sayHello() {
    return Mono.just("Hello, World!");
  }

  @PostMapping("/greet")
  public Mono<Greeting> greet(@RequestBody Person person) {
    String message = String.format("Hello %s %s!", person.getFirstName(), person.getLastName());
    Greeting greeting = new Greeting().setMessage(message).setCount(++count);
    return Mono.just(greeting);
  }
}
