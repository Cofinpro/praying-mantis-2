package pt.cofinpro.prayingmantis.hello;

import org.springframework.web.bind.annotation.RestController;
import pt.cofinpro.prayingmantis.api.HelloApi;
import pt.cofinpro.prayingmantis.api.model.Hello;

/** Sample endpoint (BE-0.2): the mapping and the DTO come from api/openapi.yaml. */
@RestController
public class HelloController implements HelloApi {

    @Override
    public Hello getHello() {
        return new Hello("Hello from praying-mantis");
    }
}
