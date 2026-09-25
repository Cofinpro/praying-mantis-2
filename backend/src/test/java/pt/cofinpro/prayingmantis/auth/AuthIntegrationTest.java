package pt.cofinpro.prayingmantis.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import pt.cofinpro.prayingmantis.TestcontainersConfiguration;

/**
 * Login, logout and /me end to end, against the dev seed users (password "password").
 * CSRF is done the way the browser does it (cookie + header), not with spring-security-test's csrf():
 * that helper swaps the CSRF token repository for the whole cached test context, after which no real
 * XSRF-TOKEN cookie comes back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void loginReturnsTheUserAndStartsASessionThatMeUses() throws Exception {
        MockHttpSession session = login("ana.silva@cofinpro.pt", "password");

        mockMvc.perform(get("/api/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana.silva@cofinpro.pt"))
                .andExpect(jsonPath("$.name").value("Ana Silva"))
                .andExpect(jsonPath("$.client").value("DKB"))
                .andExpect(jsonPath("$.level").value("SENIOR_ARCHITECT"))
                .andExpect(jsonPath("$.isTeamLead").value(true))
                .andExpect(jsonPath("$.isAdmin").value(false));
    }

    @Test
    void loginBodyIsTheCurrentUser() throws Exception {
        mockMvc.perform(loginRequest("alex.admin@cofinpro.pt", "password"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAdmin").value(true))
                .andExpect(jsonPath("$.isTeamLead").value(false));
    }

    @Test
    void emailIsCaseInsensitive() throws Exception {
        mockMvc.perform(loginRequest("Carla.Mendes@COFINPRO.pt", "password"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("carla.mendes@cofinpro.pt"));
    }

    @Test
    void wrongPasswordIsUnauthorizedWithAGenericMessage() throws Exception {
        mockMvc.perform(loginRequest("ana.silva@cofinpro.pt", "wrong"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void unknownEmailGetsTheSameAnswerAsAWrongPassword() throws Exception {
        mockMvc.perform(loginRequest("nobody@cofinpro.pt", "password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void invalidEmailIsAValidationError() throws Exception {
        mockMvc.perform(loginRequest("not-an-email", "password"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("email"));
    }

    @Test
    void loginWithoutCsrfTokenIsForbidden() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("ana.silva@cofinpro.pt", "password")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Missing or invalid CSRF token"));
    }

    @Test
    void meWithoutSessionIsUnauthorizedProblem() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void loginChangesTheSessionId() throws Exception {
        MockHttpSession before = new MockHttpSession();
        String idBefore = before.getId();

        MvcResult result = mockMvc.perform(loginRequest("ana.silva@cofinpro.pt", "password").session(before))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getRequest().getSession(false).getId()).isNotEqualTo(idBefore);
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        MockHttpSession session = login("ana.silva@cofinpro.pt", "password");

        mockMvc.perform(post("/api/auth/logout").with(xsrfToken()).session(session))
                .andExpect(status().isNoContent());

        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void logoutWithoutSessionIsStillNoContent() throws Exception {
        mockMvc.perform(post("/api/auth/logout").with(xsrfToken()))
                .andExpect(status().isNoContent());
    }

    /** The FE flow without test helpers: read the XSRF-TOKEN cookie, send it back as the header. */
    @Test
    void csrfCookieFromAnyResponseLetsTheBrowserLogIn() throws Exception {
        Cookie token = mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(token).isNotNull();

        mockMvc.perform(post("/api/auth/login")
                        .cookie(token)
                        .header("X-XSRF-TOKEN", token.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("ana.silva@cofinpro.pt", "password")))
                .andExpect(status().isOk());
    }

    /** Login rotates the token: the response must carry the new one, and it must work for the next POST. */
    @Test
    void loginSendsANewXsrfTokenThatTheNextPostCanUse() throws Exception {
        Cookie before = mockMvc.perform(get("/api/hello")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .cookie(before)
                        .header("X-XSRF-TOKEN", before.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("ana.silva@cofinpro.pt", "password")))
                .andExpect(status().isOk())
                .andReturn();

        Cookie after = lastXsrfCookie(login);
        assertThat(after.getValue()).isNotBlank().isNotEqualTo(before.getValue());

        mockMvc.perform(post("/api/auth/logout")
                        .session((MockHttpSession) login.getRequest().getSession(false))
                        .cookie(after)
                        .header("X-XSRF-TOKEN", after.getValue()))
                .andExpect(status().isNoContent());
    }

    /** Logout clears the token; the response must carry a fresh one, so logging in again works straight away. */
    @Test
    void logoutSendsAFreshXsrfTokenForTheNextLogin() throws Exception {
        MvcResult logout = mockMvc.perform(post("/api/auth/logout").with(xsrfToken()))
                .andExpect(status().isNoContent())
                .andReturn();

        Cookie fresh = lastXsrfCookie(logout);
        assertThat(fresh.getValue()).isNotBlank();

        mockMvc.perform(post("/api/auth/login")
                        .cookie(fresh)
                        .header("X-XSRF-TOKEN", fresh.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("ana.silva@cofinpro.pt", "password")))
                .andExpect(status().isOk());
    }

    /** A rotation writes two Set-Cookie headers: the deletion first, then the new token. */
    private static Cookie lastXsrfCookie(MvcResult result) {
        return Arrays.stream(result.getResponse().getCookies())
                .filter(cookie -> cookie.getName().equals("XSRF-TOKEN"))
                .reduce((first, last) -> last)
                .orElseThrow(() -> new AssertionError("no XSRF-TOKEN cookie in the response"));
    }

    /** Like the FE: take the XSRF-TOKEN cookie from any response and send it back as the header too. */
    private RequestPostProcessor xsrfToken() {
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

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(loginRequest(email, password))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MockHttpServletRequestBuilder loginRequest(String email, String password) {
        return post("/api/auth/login")
                .with(xsrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(email, password));
    }

    private static String json(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }
}
