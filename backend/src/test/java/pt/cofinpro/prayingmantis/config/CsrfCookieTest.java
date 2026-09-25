package pt.cofinpro.prayingmantis.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pt.cofinpro.prayingmantis.hello.HelloController;

/** Web slice with the real filter chain, no database: the SPA must get its CSRF token from any response. */
@WebMvcTest(HelloController.class)
@Import(SecurityConfig.class)
class CsrfCookieTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void anyResponseSetsAReadableXsrfTokenCookie() throws Exception {
        mockMvc.perform(get("/api/hello"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
    }

    @Test
    void anUnauthorizedResponseSetsItToo() throws Exception {
        mockMvc.perform(get("/api/anything"))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }
}
