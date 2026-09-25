package pt.cofinpro.prayingmantis.hello;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pt.cofinpro.prayingmantis.config.SecurityConfig;

@WebMvcTest(HelloController.class)
@Import(SecurityConfig.class)
class HelloControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void returnsGreetingUnderApiPrefixWithoutLogin() throws Exception {
        mockMvc.perform(get("/api/hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Hello from praying-mantis"));
    }
}
