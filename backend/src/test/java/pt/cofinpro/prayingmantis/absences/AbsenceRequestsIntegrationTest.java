package pt.cofinpro.prayingmantis.absences;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import pt.cofinpro.prayingmantis.TestcontainersConfiguration;
import pt.cofinpro.prayingmantis.users.User;
import pt.cofinpro.prayingmantis.users.UserRepository;

/** GET /api/me/absence-requests end to end (BE-2.3), against the dev seed: Carla has one request per status. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AbsenceRequestsIntegrationTest {

    private static final String CARLA = "carla.mendes@cofinpro.pt";
    private static final String DIOGO = "diogo.pereira@cofinpro.pt";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AbsenceRequestRepository requests;

    @Autowired
    AbsenceTypeRepository types;

    @Autowired
    UserRepository users;

    @Test
    @WithUserDetails(CARLA)
    void carlasYearHasAllStatusesInDateOrder() throws Exception {
        int year = LocalDate.now().getYear();

        range(year + "-01-01", year + "-12-31")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                // sick (Jan), vacation (Feb, Mar, Jul), vacation pending (Nov)
                .andExpect(jsonPath("$[*].type", contains("SICK", "VACATION", "VACATION", "VACATION", "VACATION")))
                .andExpect(jsonPath("$[*].status", contains("APPROVED", "APPROVED", "APPROVED", "REJECTED", "PENDING")));
    }

    @Test
    @WithUserDetails(CARLA)
    void aRequestHasItsDetailsAndTheApprover() throws Exception {
        int year = LocalDate.now().getYear();

        // The rejected request, Thu–Fri of the week of 16 July
        range(year + "-07-01", year + "-07-31")
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].startPart").value("FULL"))
                .andExpect(jsonPath("$[0].endPart").value("FULL"))
                .andExpect(jsonPath("$[0].workingDays").value(2.0))
                .andExpect(jsonPath("$[0].reason").value("Long weekend"))
                .andExpect(jsonPath("$[0].approver.name").value("Ana Silva"))
                .andExpect(jsonPath("$[0].decidedAt").isString())
                .andExpect(jsonPath("$[0].decisionComment").value("Release week"))
                .andExpect(jsonPath("$[0].createdAt").isString());
    }

    @Test
    @WithUserDetails(CARLA)
    void sickLeaveHasNoApprover() throws Exception {
        int year = LocalDate.now().getYear();

        range(year + "-01-01", year + "-01-31")
                .andExpect(jsonPath("$[0].type").value("SICK"))
                .andExpect(jsonPath("$[0].approver").doesNotExist());
    }

    @Test
    @WithUserDetails(DIOGO)
    void aRequestThatOnlyPartlyOverlapsComesBackWhole() throws Exception {
        // Mon 23 to Fri 27 Nov 2026; the range starts on the Thursday
        saveForDiogo("2026-11-23", "2026-11-27");

        range("2026-11-26", "2026-12-31")
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].startDate").value("2026-11-23"))
                .andExpect(jsonPath("$[0].endDate").value("2026-11-27"));
    }

    @Test
    @WithUserDetails(DIOGO)
    void bothEndsOfTheRangeAreInclusive() throws Exception {
        saveForDiogo("2026-11-23", "2026-11-27");

        range("2026-11-27", "2026-11-27").andExpect(jsonPath("$", hasSize(1)));
        range("2026-11-16", "2026-11-23").andExpect(jsonPath("$", hasSize(1)));
        range("2026-11-28", "2026-12-31").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithUserDetails(DIOGO)
    void otherPeoplesRequestsArentIncluded() throws Exception {
        int year = LocalDate.now().getYear();

        // Carla has five this year, Diogo none
        range(year + "-01-01", year + "-12-31").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithUserDetails(DIOGO)
    void aRangeEndingBeforeItStartsIsAValidationError() throws Exception {
        range("2026-10-10", "2026-10-09")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Request has invalid fields"))
                .andExpect(jsonPath("$.errors[0].field").value("to"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be before from"));
    }

    @Test
    @WithUserDetails(DIOGO)
    void aRangeCanBe366DaysButNotMore() throws Exception {
        // 2028 is a leap year: 1 Jan to 31 Dec is 366 days
        range("2028-01-01", "2028-12-31").andExpect(status().isOk());
        range("2028-01-01", "2029-01-01")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("to"));
    }

    @Test
    @WithUserDetails(DIOGO)
    void fromAndToAreRequired() throws Exception {
        mockMvc.perform(get("/api/me/absence-requests").param("from", "2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @WithUserDetails(DIOGO)
    void aDateThatIsntIsoIsABadRequest() throws Exception {
        range("01/10/2026", "2026-10-31")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void myRequestsNeedALogin() throws Exception {
        range("2026-10-01", "2026-10-31").andExpect(status().isUnauthorized());
    }

    private ResultActions range(String from, String to) throws Exception {
        return mockMvc.perform(get("/api/me/absence-requests").param("from", from).param("to", to));
    }

    private void saveForDiogo(String start, String end) {
        User diogo = users.findByEmail(DIOGO).orElseThrow();
        User ana = users.findByEmail("ana.silva@cofinpro.pt").orElseThrow();
        requests.saveAndFlush(new AbsenceRequest(diogo, types.findByCode(AbsenceTypeCode.VACATION).orElseThrow(),
                LocalDate.parse(start), DayPart.FULL, LocalDate.parse(end), DayPart.FULL, new BigDecimal("5"),
                AbsenceStatus.PENDING, ana));
    }
}
