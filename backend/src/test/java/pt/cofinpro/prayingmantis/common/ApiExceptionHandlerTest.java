package pt.cofinpro.prayingmantis.common;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Standalone MockMvc: only the advice and a test controller, no Spring context. */
class ApiExceptionHandlerTest {

    record NameRequest(@NotBlank String name) {}

    record RangeRequest(int from, int to) {
        @AssertTrue(message = "to must not be before from")
        boolean isOrdered() {
            return to >= from;
        }
    }

    @RestController
    static class FailingController {
        @PostMapping("/test/validated")
        void validated(@Valid @RequestBody NameRequest request) {}

        @PostMapping("/test/range")
        void range(@Valid @RequestBody RangeRequest request) {}

        @GetMapping("/test/param")
        void param(@RequestParam("year") @Min(2000) int year) {}

        @GetMapping("/test/denied")
        void denied() {
            throw new AccessDeniedException("not your request");
        }

        @GetMapping("/test/boom")
        void boom() {
            throw new IllegalStateException("secret internals");
        }
    }

    MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void invalidBodyFieldIsBadRequestWithFieldErrors() throws Exception {
        mockMvc.perform(post("/test/validated").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void crossFieldErrorIsListedToo() throws Exception {
        mockMvc.perform(post("/test/range").contentType(MediaType.APPLICATION_JSON).content("{\"from\":5,\"to\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].message").value("to must not be before from"));
    }

    @Test
    void invalidQueryParamIsBadRequestWithParamName() throws Exception {
        mockMvc.perform(get("/test/param").param("year", "1999"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("year"));
    }

    @Test
    void unreadableBodyIsBadRequestProblem() throws Exception {
        mockMvc.perform(post("/test/validated").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void accessDeniedIsForbiddenNotInternalError() throws Exception {
        mockMvc.perform(get("/test/denied"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("You are not allowed to do this"));
    }

    @Test
    void unexpectedExceptionIsInternalErrorWithoutInternals() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Unexpected error"));
    }
}
