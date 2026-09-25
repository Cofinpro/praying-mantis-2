package pt.cofinpro.prayingmantis.absences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
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
import org.springframework.transaction.annotation.Transactional;
import pt.cofinpro.prayingmantis.TestcontainersConfiguration;
import pt.cofinpro.prayingmantis.config.TimeConfig;
import pt.cofinpro.prayingmantis.holidays.PublicHoliday;
import pt.cofinpro.prayingmantis.holidays.PublicHolidayRepository;
import pt.cofinpro.prayingmantis.users.User;
import pt.cofinpro.prayingmantis.users.UserRepository;

/**
 * GET /api/absence-types and /api/me/absence-balance end to end (BE-2.2). {@code @WithUserDetails} loads
 * a seed user through our UserDetailsService, so the principal is the real AuthenticatedUser. MockMvc
 * runs in the test's thread, so it sees the rows each test inserts, and they roll back afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AbsenceBalanceIntegrationTest {

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
    PublicHolidayRepository holidays;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    private User diogo;

    @BeforeEach
    void setUp() {
        diogo = users.findByEmail(DIOGO).orElseThrow();
        // Diogo's tests set up their own years, independent of the year the seed was created in
        jdbc.update("delete from absence_entitlements where user_id = ?", diogo.getId());
    }

    @Test
    @WithUserDetails("carla.mendes@cofinpro.pt")
    void carlasSeededYear() throws Exception {
        int year = LocalDate.now(TimeConfig.ZONE).getYear();

        mockMvc.perform(get("/api/me/absence-balance").param("year", String.valueOf(year)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("VACATION"))
                .andExpect(jsonPath("$[0].year").value(year))
                .andExpect(jsonPath("$[0].entitledDays").value(22.0))
                .andExpect(jsonPath("$[0].carriedOverDays").value(2.5))
                // 5 + 0.5 approved; the rejected 2 days and the sick day don't count here
                .andExpect(jsonPath("$[0].usedDays").value(5.5))
                .andExpect(jsonPath("$[0].pendingDays").value(3.0))
                .andExpect(jsonPath("$[0].remainingDays").value(19.0));
    }

    @Test
    @WithUserDetails("carla.mendes@cofinpro.pt")
    void withoutAYearItsTheCurrentYearInLisbon() throws Exception {
        mockMvc.perform(get("/api/me/absence-balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].year").value(LocalDate.now(TimeConfig.ZONE).getYear()));
    }

    /** The seed's working days are hand-computed; this checks them against the real calculation. */
    @Test
    void theSeededWorkingDaysMatchTheCalculation() {
        User carla = users.findByEmail("carla.mendes@cofinpro.pt").orElseThrow();
        var seeded = requests.findWithTypeOverlapping(carla.getId(), LocalDate.of(2000, 1, 1), LocalDate.of(2100, 12, 31),
                Set.of(AbsenceStatus.values()));
        Set<LocalDate> holidayDates = holidays.findAll().stream().map(PublicHoliday::getDate).collect(Collectors.toSet());

        assertThat(seeded).hasSize(5).allSatisfy(r -> assertThat(
                        WorkingDays.count(r.getStartDate(), r.getStartPart(), r.getEndDate(), r.getEndPart(), holidayDates))
                .as("request from %s", r.getStartDate())
                .isEqualByComparingTo(r.getWorkingDays()));
    }

    @Test
    @WithUserDetails(DIOGO)
    void aRequestAcrossNewYearCountsInBothYears() throws Exception {
        entitle(2026, AbsenceTypeCode.VACATION, "22", "0");
        entitle(2027, AbsenceTypeCode.VACATION, "22", "0");
        // Mon 28 Dec 2026 to Fri 8 Jan 2027: 4 days in 2026, 5 in 2027 (1 Jan is a holiday)
        request("2026-12-28", DayPart.FULL, "2027-01-08", DayPart.FULL, "9", AbsenceStatus.APPROVED);

        balance(2026).andExpect(jsonPath("$[0].usedDays").value(4.0)).andExpect(jsonPath("$[0].remainingDays").value(18.0));
        balance(2027).andExpect(jsonPath("$[0].usedDays").value(5.0)).andExpect(jsonPath("$[0].remainingDays").value(17.0));
    }

    @Test
    @WithUserDetails(DIOGO)
    void halfDaysCountInTheYearTheyFallIn() throws Exception {
        entitle(2026, AbsenceTypeCode.VACATION, "22", "0");
        entitle(2027, AbsenceTypeCode.VACATION, "22", "0");
        // Afternoon of Wed 30 Dec 2026 to the morning of Mon 4 Jan 2027
        request("2026-12-30", DayPart.AFTERNOON, "2027-01-04", DayPart.MORNING, "2", AbsenceStatus.PENDING);

        balance(2026).andExpect(jsonPath("$[0].pendingDays").value(1.5)).andExpect(jsonPath("$[0].usedDays").value(0));
        balance(2027).andExpect(jsonPath("$[0].pendingDays").value(0.5));
    }

    @Test
    @WithUserDetails(DIOGO)
    void rejectedAndCancelledRequestsDontCount() throws Exception {
        entitle(2027, AbsenceTypeCode.VACATION, "22", "1.5");
        request("2027-02-08", DayPart.FULL, "2027-02-12", DayPart.FULL, "5", AbsenceStatus.REJECTED);
        request("2027-02-08", DayPart.FULL, "2027-02-09", DayPart.FULL, "2", AbsenceStatus.CANCELLED);

        balance(2027)
                .andExpect(jsonPath("$[0].usedDays").value(0))
                .andExpect(jsonPath("$[0].pendingDays").value(0))
                .andExpect(jsonPath("$[0].remainingDays").value(23.5));
    }

    @Test
    @WithUserDetails(DIOGO)
    void onlyTypesThatDeductHaveRemainingDays() throws Exception {
        entitle(2027, AbsenceTypeCode.VACATION, "22", "0");
        entitle(2027, AbsenceTypeCode.TRAINING, "5", "0");
        request("2027-03-01", DayPart.FULL, "2027-03-02", DayPart.FULL, "2", AbsenceStatus.APPROVED, AbsenceTypeCode.TRAINING);

        // In type name order: Training, then Vacation
        balance(2027)
                .andExpect(jsonPath("$[*].type", contains("TRAINING", "VACATION")))
                .andExpect(jsonPath("$[0].usedDays").value(2.0))
                .andExpect(jsonPath("$[0].remainingDays").doesNotExist())
                .andExpect(jsonPath("$[1].usedDays").value(0))
                .andExpect(jsonPath("$[1].remainingDays").value(22.0));
    }

    @Test
    @WithUserDetails(DIOGO)
    void remainingDaysCanGoNegative() throws Exception {
        entitle(2027, AbsenceTypeCode.VACATION, "1", "0");
        request("2027-02-08", DayPart.FULL, "2027-02-09", DayPart.FULL, "2", AbsenceStatus.APPROVED);

        balance(2027).andExpect(jsonPath("$[0].remainingDays").value(-1.0));
    }

    @Test
    @WithUserDetails(DIOGO)
    void aYearWithoutEntitlementsIsAnEmptyList() throws Exception {
        balance(2030).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithUserDetails(DIOGO)
    void aYearOutOfRangeIsAValidationError() throws Exception {
        mockMvc.perform(get("/api/me/absence-balance").param("year", "1999"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void theBalanceNeedsALogin() throws Exception {
        mockMvc.perform(get("/api/me/absence-balance"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @WithUserDetails(DIOGO)
    void absenceTypesAreOrderedByName() throws Exception {
        mockMvc.perform(get("/api/absence-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", contains("PARENTAL", "SICK", "TRAINING", "UNPAID", "VACATION")))
                .andExpect(jsonPath("$[1].name").value("Sick leave"))
                .andExpect(jsonPath("$[1].isPaid").value(true))
                .andExpect(jsonPath("$[1].deductsFromBalance").value(false))
                .andExpect(jsonPath("$[1].requiresApproval").value(false));
    }

    @Test
    void absenceTypesNeedALogin() throws Exception {
        mockMvc.perform(get("/api/absence-types")).andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions balance(int year) throws Exception {
        return mockMvc.perform(get("/api/me/absence-balance").param("year", String.valueOf(year)))
                .andExpect(status().isOk());
    }

    private void entitle(int year, AbsenceTypeCode code, String entitled, String carriedOver) {
        entitlements.saveAndFlush(new AbsenceEntitlement(
                diogo, types.findByCode(code).orElseThrow(), year, new BigDecimal(entitled), new BigDecimal(carriedOver)));
    }

    private void request(String start, DayPart startPart, String end, DayPart endPart, String days, AbsenceStatus status) {
        request(start, startPart, end, endPart, days, status, AbsenceTypeCode.VACATION);
    }

    private void request(String start, DayPart startPart, String end, DayPart endPart, String days, AbsenceStatus status,
            AbsenceTypeCode code) {
        User ana = users.findByEmail("ana.silva@cofinpro.pt").orElseThrow();
        requests.saveAndFlush(new AbsenceRequest(diogo, types.findByCode(code).orElseThrow(),
                LocalDate.parse(start), startPart, LocalDate.parse(end), endPart, new BigDecimal(days), status, ana));
    }
}
