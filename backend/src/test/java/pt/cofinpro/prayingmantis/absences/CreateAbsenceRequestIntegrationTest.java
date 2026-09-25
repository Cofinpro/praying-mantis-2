package pt.cofinpro.prayingmantis.absences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import pt.cofinpro.prayingmantis.TestcontainersConfiguration;
import pt.cofinpro.prayingmantis.XsrfToken;
import pt.cofinpro.prayingmantis.users.User;
import pt.cofinpro.prayingmantis.users.UserRepository;

/**
 * POST /api/me/absence-requests end to end (BE-3.2). Dates are in 2027 (Mon 8 Feb 2027 has no holidays
 * that week), with entitlements each test sets up, so the year the seed was created in doesn't matter.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class CreateAbsenceRequestIntegrationTest {

    private static final String DIOGO = "diogo.pereira@cofinpro.pt";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AbsenceTypeRepository types;

    @Autowired
    AbsenceEntitlementRepository entitlements;

    @Autowired
    AbsenceRequestRepository requests;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from absence_entitlements where user_id in (select id from users where email <> 'carla.mendes@cofinpro.pt')");
    }

    @Test
    @WithUserDetails(DIOGO)
    void aVacationRequestIsPendingWithTheTeamLeadAsApprover() throws Exception {
        entitle(DIOGO, 2027, "22");

        create("VACATION", "2027-02-08", "FULL", "2027-02-12", "FULL", "Ski trip")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.type").value("VACATION"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.workingDays").value(5))
                .andExpect(jsonPath("$.reason").value("Ski trip"))
                .andExpect(jsonPath("$.approver.name").value("Ana Silva"))
                .andExpect(jsonPath("$.decidedAt").doesNotExist())
                .andExpect(jsonPath("$.createdAt").isString());

        assertThat(jdbc.queryForObject("select count(*) from absence_requests r join users u on u.id = r.user_id where u.email = ?",
                Integer.class, DIOGO)).isEqualTo(1);
    }

    @Test
    @WithUserDetails(DIOGO)
    void theBackendComputesWorkingDaysWithHolidaysAndHalfDays() throws Exception {
        entitle(DIOGO, 2027, "22");

        // Thu 25 Mar afternoon to Tue 30 Mar 2027 morning: 0.5 + Good Friday 0 + weekend + 1 + 0.5
        create("VACATION", "2027-03-25", "AFTERNOON", "2027-03-30", "MORNING", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workingDays").value(2.0));
    }

    @Test
    @WithUserDetails(DIOGO)
    void sickLeaveIsApprovedStraightAwayWithoutApproverOrBalance() throws Exception {
        // No SICK entitlement, and it's in the past: both fine (decision #29)
        create("SICK", "2026-01-05", "FULL", "2026-01-06", "FULL", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approver").doesNotExist())
                .andExpect(jsonPath("$.decidedAt").isString());
    }

    @Test
    @WithUserDetails(DIOGO)
    void aTypeThatDoesntDeductNeedsNoEntitlement() throws Exception {
        create("TRAINING", "2027-02-08", "FULL", "2027-02-09", "FULL", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @WithUserDetails("bruno.costa@cofinpro.pt")
    void aTeamLeadsRequestGoesToTheirOwnLead() throws Exception {
        entitle("bruno.costa@cofinpro.pt", 2027, "22");

        create("VACATION", "2027-02-08", "FULL", "2027-02-08", "FULL", null)
                .andExpect(jsonPath("$.approver.name").value("Ana Silva"));
    }

    @Test
    @WithUserDetails("gabriela.lopes@cofinpro.pt")
    void withoutATeamLeadTheRequestGoesToAnAdmin() throws Exception {
        entitle("gabriela.lopes@cofinpro.pt", 2027, "22");

        create("VACATION", "2027-02-08", "FULL", "2027-02-08", "FULL", null)
                .andExpect(jsonPath("$.approver.name").value("Alex Admin"));
    }

    @Test
    @WithUserDetails("alex.admin@cofinpro.pt")
    void theSoleAdminWithoutATeamLeadHasNoApprover() throws Exception {
        entitle("alex.admin@cofinpro.pt", 2027, "22");

        create("VACATION", "2027-02-08", "FULL", "2027-02-08", "FULL", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/no-approver"));
    }

    @Test
    @WithUserDetails(DIOGO)
    void overlappingAnOwnPendingRequestIsAConflict() throws Exception {
        entitle(DIOGO, 2027, "22");
        create("VACATION", "2027-02-08", "FULL", "2027-02-12", "FULL", null).andExpect(status().isCreated());

        create("VACATION", "2027-02-12", "FULL", "2027-02-15", "FULL", null)
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("/problems/absence-overlap"))
                .andExpect(jsonPath("$.detail").value("These days overlap another pending or approved request of yours"));
    }

    @Test
    @WithUserDetails(DIOGO)
    void aRejectedRequestDoesntBlockItsDays() throws Exception {
        entitle(DIOGO, 2027, "22");
        save(DIOGO, "2027-02-08", "2027-02-12", "5", AbsenceStatus.REJECTED);

        create("VACATION", "2027-02-08", "FULL", "2027-02-12", "FULL", null).andExpect(status().isCreated());
    }

    @Test
    @WithUserDetails(DIOGO)
    void moreDaysThanAreLeftIsAConflict() throws Exception {
        entitle(DIOGO, 2027, "4");

        create("VACATION", "2027-02-08", "FULL", "2027-02-12", "FULL", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/insufficient-balance"))
                .andExpect(jsonPath("$.detail").value("Only 4 vacation days left in 2027, but the request needs 5"));
    }

    @Test
    @WithUserDetails(DIOGO)
    void approvedDaysCountAgainstTheBalance() throws Exception {
        entitle(DIOGO, 2027, "5");
        save(DIOGO, "2027-03-01", "2027-03-04", "4", AbsenceStatus.APPROVED);

        create("VACATION", "2027-02-08", "FULL", "2027-02-09", "FULL", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Only 1 vacation days left in 2027, but the request needs 2"));
    }

    @Test
    @WithUserDetails(DIOGO)
    void pendingDaysDontCountAgainstTheBalance() throws Exception {
        entitle(DIOGO, 2027, "5");
        save(DIOGO, "2027-03-01", "2027-03-05", "5", AbsenceStatus.PENDING);

        create("VACATION", "2027-02-08", "FULL", "2027-02-10", "FULL", null).andExpect(status().isCreated());
    }

    @Test
    @WithUserDetails(DIOGO)
    void aYearWithoutAnEntitlementHasNoDaysLeft() throws Exception {
        create("VACATION", "2027-02-08", "FULL", "2027-02-08", "FULL", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Only 0 vacation days left in 2027, but the request needs 1"));
    }

    @Test
    @WithUserDetails(DIOGO)
    void aRequestAcrossNewYearNeedsDaysInBothYears() throws Exception {
        entitle(DIOGO, 2026, "1");
        entitle(DIOGO, 2027, "22");

        // Wed 30 Dec 2026 to Mon 4 Jan 2027: 2 days in 2026, 1 in 2027 (1 Jan is a holiday)
        create("VACATION", "2026-12-30", "FULL", "2027-01-04", "FULL", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Only 1 vacation days left in 2026, but the request needs 2"));
    }

    @Test
    @WithUserDetails(DIOGO)
    void anEndBeforeTheStartIsAFieldError() throws Exception {
        create("VACATION", "2027-02-10", "FULL", "2027-02-09", "FULL", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("endDate"));
    }

    @Test
    @WithUserDetails(DIOGO)
    void dayPartsThatLeaveAGapAreAFieldError() throws Exception {
        create("VACATION", "2027-02-08", "MORNING", "2027-02-09", "FULL", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("startPart"));
    }

    @Test
    @WithUserDetails(DIOGO)
    void aWeekendOnlyRequestHasNoWorkingDays() throws Exception {
        create("VACATION", "2027-02-06", "FULL", "2027-02-07", "FULL", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("endDate"))
                .andExpect(jsonPath("$.errors[0].message").value("the absence has no working days"));
    }

    @Test
    @WithUserDetails(DIOGO)
    void missingFieldsAreFieldErrors() throws Exception {
        mockMvc.perform(post("/api/me/absence-requests").with(XsrfToken.from(mockMvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\": \"2027-02-08\", \"endDate\": \"2027-02-08\", \"startPart\": \"FULL\", \"endPart\": \"FULL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("type"));
    }

    @Test
    @WithUserDetails(DIOGO)
    void aReasonOver500CharactersIsAFieldError() throws Exception {
        create("TRAINING", "2027-02-08", "FULL", "2027-02-08", "FULL", "x".repeat(501))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("reason"));
    }

    @Test
    @WithUserDetails(DIOGO)
    void anUnknownTypeIsABadRequest() throws Exception {
        create("HOLIDAY", "2027-02-08", "FULL", "2027-02-08", "FULL", null)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @WithUserDetails(DIOGO)
    void withoutTheCsrfTokenItsForbidden() throws Exception {
        mockMvc.perform(post("/api/me/absence-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("TRAINING", "2027-02-08", "FULL", "2027-02-08", "FULL", null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestingNeedsALogin() throws Exception {
        create("TRAINING", "2027-02-08", "FULL", "2027-02-08", "FULL", null).andExpect(status().isUnauthorized());
    }

    private ResultActions create(String type, String start, String startPart, String end, String endPart, String reason)
            throws Exception {
        return mockMvc.perform(post("/api/me/absence-requests").with(XsrfToken.from(mockMvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(type, start, startPart, end, endPart, reason)));
    }

    private static String json(String type, String start, String startPart, String end, String endPart, String reason) {
        String reasonField = reason == null ? "" : ", \"reason\": \"" + reason + "\"";
        return """
                {"type": "%s", "startDate": "%s", "startPart": "%s", "endDate": "%s", "endPart": "%s"%s}"""
                .formatted(type, start, startPart, end, endPart, reasonField);
    }

    private void entitle(String email, int year, String days) {
        entitlements.saveAndFlush(new AbsenceEntitlement(users.findByEmail(email).orElseThrow(),
                types.findByCode(AbsenceTypeCode.VACATION).orElseThrow(), year, new BigDecimal(days), BigDecimal.ZERO));
    }

    private void save(String email, String start, String end, String days, AbsenceStatus status) {
        User user = users.findByEmail(email).orElseThrow();
        requests.saveAndFlush(new AbsenceRequest(user, types.findByCode(AbsenceTypeCode.VACATION).orElseThrow(),
                LocalDate.parse(start), DayPart.FULL, LocalDate.parse(end), DayPart.FULL, new BigDecimal(days), status,
                user.getTeamLead()));
    }
}
