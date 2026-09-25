package pt.cofinpro.prayingmantis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import jakarta.servlet.http.Cookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * CSRF for unsafe test requests, the way the FE does it: take the XSRF-TOKEN cookie from any response and
 * send it back as the X-XSRF-TOKEN header too. Not spring-security-test's csrf(): it swaps the token
 * repository for the whole cached test context (see learnings.md).
 */
public final class XsrfToken {

    private XsrfToken() {
    }

    public static RequestPostProcessor from(MockMvc mockMvc) {
        return request -> {
            try {
                Cookie token = mockMvc.perform(get("/api/hello")).andReturn().getResponse().getCookie("XSRF-TOKEN");
                assertThat(token).as("XSRF-TOKEN cookie").isNotNull();
                request.setCookies(token);
                request.addHeader("X-XSRF-TOKEN", token.getValue());
                return request;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
    }
}
