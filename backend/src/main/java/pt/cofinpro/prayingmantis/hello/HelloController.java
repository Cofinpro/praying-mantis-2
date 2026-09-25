package pt.cofinpro.prayingmantis.hello;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hello")
public class HelloController {

    @GetMapping
    public HelloResponse hello(@RequestParam(defaultValue = "world") String name) {
        return new HelloResponse("Hello, " + name + "!");
    }

    public record HelloResponse(String message) {
    }
}
