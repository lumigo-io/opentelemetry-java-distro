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
