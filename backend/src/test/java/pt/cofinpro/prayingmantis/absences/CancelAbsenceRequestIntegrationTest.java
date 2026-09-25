package pt.cofinpro.prayingmantis.absences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.DayOfWeek;
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
import pt.cofinpro.prayingmantis.XsrfToken;
import pt.cofinpro.prayingmantis.config.TimeConfig;
import pt.cofinpro.prayingmantis.holidays.PublicHolidayRepository;
import pt.cofinpro.prayingmantis.users.User;
import pt.cofinpro.prayingmantis.users.UserRepository;

/**
 * POST /api/me/absence-requests/{id}/cancel end to end (BE-3.3). "Started" depends on today, so the dates
 * are relative to today in Lisbon, the zone the service's Clock uses.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class CancelAbsenceRequestIntegrationTest {

    private static final String DIOGO = "diogo.pereira@cofinpro.pt";
    private static final LocalDate TODAY = LocalDate.now(TimeConfig.ZONE);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AbsenceTypeRepository types;

    @Autowired
    AbsenceRequestRepository requests;

    @Autowired
    UserRepository users;

    @Autowired
    PublicHolidayRepository holidays;

    @Test
    @WithUserDetails(DIOGO)
    void aPendingRequestCanBeCancelled() throws Exception {
        Long id = save(DIOGO, TODAY.minusDays(10), AbsenceStatus.PENDING);

        cancel(id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(requests.findById(id)).get().extracting(AbsenceRequest::getStatus).isEqualTo(AbsenceStatus.CANCELLED);
    }

    @Test
    @WithUserDetails(DIOGO)
    void anApprovedRequestCanBeCancelledBeforeItStarts() throws Exception {
        Long id = save(DIOGO, TODAY.plusDays(1), AbsenceStatus.APPROVED);

        cancel(id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @WithUserDetails(DIOGO)
    void anApprovedRequestThatStartsTodayCantBeCancelled() throws Exception {
        Long id = save(DIOGO, TODAY, AbsenceStatus.APPROVED);

        cancel(id)
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("/problems/absence-not-cancellable"))
                .andExpect(jsonPath("$.detail").value("An approved absence can only be cancelled before it starts"));
    }

    @Test
    @WithUserDetails(DIOGO)
    void rejectedAndCancelledRequestsCantBeCancelled() throws Exception {
        Long rejected = save(DIOGO, TODAY.plusDays(20), AbsenceStatus.REJECTED);
        Long cancelled = save(DIOGO, TODAY.plusDays(30), AbsenceStatus.CANCELLED);

        cancel(rejected)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Only pending or approved requests can be cancelled; this one is rejected"));
        cancel(cancelled).andExpect(status().isConflict()).andExpect(jsonPath("$.type").value("/problems/absence-not-cancellable"));
    }

    @Test
    @WithUserDetails(DIOGO)
    void cancellingFreesTheDaysForANewRequest() throws Exception {
        LocalDate day = workingDayFrom(TODAY.plusWeeks(2));
        Long id = save(DIOGO, day, AbsenceStatus.PENDING);
        cancel(id).andExpect(status().isOk());

        // TRAINING needs no entitlement, so only the overlap rule can say no
        mockMvc.perform(post("/api/me/absence-requests").with(XsrfToken.from(mockMvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "TRAINING", "startDate": "%s", "startPart": "FULL", "endDate": "%s", "endPart": "FULL"}"""
                                .formatted(day, day)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithUserDetails(DIOGO)
    void someoneElsesRequestIsNotFound() throws Exception {
        Long evas = save("eva.santos@cofinpro.pt", TODAY.plusDays(5), AbsenceStatus.PENDING);

        cancel(evas)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Absence request not found"));
        assertThat(requests.findById(evas)).get().extracting(AbsenceRequest::getStatus).isEqualTo(AbsenceStatus.PENDING);
    }

    @Test
    @WithUserDetails(DIOGO)
    void anUnknownIdIsNotFound() throws Exception {
        cancel(Long.MAX_VALUE).andExpect(status().isNotFound());
    }

    @Test
    @WithUserDetails(DIOGO)
    void withoutTheCsrfTokenItsForbidden() throws Exception {
        Long id = save(DIOGO, TODAY.plusDays(5), AbsenceStatus.PENDING);

        mockMvc.perform(post("/api/me/absence-requests/{id}/cancel", id)).andExpect(status().isForbidden());
    }

    @Test
    void cancellingNeedsALogin() throws Exception {
        cancel(1L).andExpect(status().isUnauthorized());
    }

    private ResultActions cancel(Long id) throws Exception {
        return mockMvc.perform(post("/api/me/absence-requests/{id}/cancel", id).with(XsrfToken.from(mockMvc)));
    }

    /** The first day from {@code from} on that is neither a weekend nor a public holiday (5 Oct 2026 is a Monday holiday). */
    private LocalDate workingDayFrom(LocalDate from) {
        LocalDate day = from;
        while (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY
                || !holidays.findByDateBetweenOrderByDate(day, day).isEmpty()) {
            day = day.plusDays(1);
        }
        return day;
    }

    /** A one-day vacation, stored directly: the balance and approver rules aren't what these tests are about. */
    private Long save(String email, LocalDate day, AbsenceStatus status) {
        User user = users.findByEmail(email).orElseThrow();
        return requests.saveAndFlush(new AbsenceRequest(user, types.findByCode(AbsenceTypeCode.VACATION).orElseThrow(),
                day, DayPart.FULL, day, DayPart.FULL, BigDecimal.ONE, status, user.getTeamLead())).getId();
    }
}
