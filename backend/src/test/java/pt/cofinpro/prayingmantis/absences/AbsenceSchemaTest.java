package pt.cofinpro.prayingmantis.absences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import pt.cofinpro.prayingmantis.TestcontainersConfiguration;
import pt.cofinpro.prayingmantis.holidays.PublicHolidayRepository;
import pt.cofinpro.prayingmantis.users.User;
import pt.cofinpro.prayingmantis.users.UserRepository;

/**
 * The BE-2.1 schema against real Postgres (decision #10): the reference data, the dev seed and the
 * constraints, above all the no-overlap exclusion constraint (decision #14). Each test rolls back.
 * Requests are made for Diogo, who has none in the seed, so they can't collide with Carla's.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AbsenceSchemaTest {

    private static final LocalDate MON = LocalDate.of(2027, 2, 8);

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

    @Test
    void theFiveAbsenceTypesAreReferenceData() {
        AbsenceType vacation = types.findByCode(AbsenceTypeCode.VACATION).orElseThrow();
        AbsenceType sick = types.findByCode(AbsenceTypeCode.SICK).orElseThrow();

        assertThat(types.findAll()).extracting(AbsenceType::getCode)
                .containsExactlyInAnyOrder(AbsenceTypeCode.values());
        assertThat(vacation.isDeductsFromBalance()).isTrue();
        assertThat(vacation.isRequiresApproval()).isTrue();
        assertThat(sick.isDeductsFromBalance()).isFalse();
        assertThat(sick.isRequiresApproval()).isFalse();
        assertThat(types.findByCode(AbsenceTypeCode.UNPAID).orElseThrow().isPaid()).isFalse();
    }

    @Test
    void holidaysCover2026And2027() {
        assertThat(holidays.count()).isEqualTo(26);
        assertThat(jdbc.queryForObject(
                        "select name from public_holidays where date = '2027-03-26'", String.class))
                .isEqualTo("Good Friday");
    }

    @Test
    void devSeedGivesEveryUserAVacationEntitlementThisYear() {
        int year = LocalDate.now().getYear();

        assertThat(entitlements.findAll())
                .hasSize((int) users.count())
                .allSatisfy(e -> {
                    assertThat(e.getYear()).isEqualTo(year);
                    assertThat(e.getType().getCode()).isEqualTo(AbsenceTypeCode.VACATION);
                    assertThat(e.getEntitledDays()).isEqualByComparingTo("22");
                });
        assertThat(jdbc.queryForObject("""
                select e.carried_over_days from absence_entitlements e join users u on u.id = e.user_id
                where u.email = 'carla.mendes@cofinpro.pt'""", BigDecimal.class))
                .isEqualByComparingTo("2.5");
    }

    @Test
    void devSeedGivesCarlaOneRequestPerStatus() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select r.status, sum(r.working_days) as days from absence_requests r
                join users u on u.id = r.user_id join absence_types t on t.id = r.absence_type_id
                where u.email = 'carla.mendes@cofinpro.pt' and t.code = 'VACATION'
                group by r.status""");

        assertThat(rows).extracting(row -> row.get("status") + "=" + ((BigDecimal) row.get("days")).stripTrailingZeros().toPlainString())
                .containsExactlyInAnyOrder("APPROVED=5.5", "REJECTED=2", "PENDING=3");
    }

    @Test
    void savesARequestWithEnumsAsNames() {
        AbsenceRequest saved = requests.saveAndFlush(request(MON, DayPart.AFTERNOON, MON.plusDays(2), DayPart.MORNING,
                "2", AbsenceStatus.PENDING));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(jdbc.queryForObject("select status || '/' || start_part || '/' || end_part from absence_requests where id = ?",
                        String.class, saved.getId()))
                .isEqualTo("PENDING/AFTERNOON/MORNING");
    }

    @Test
    void pendingAndApprovedRequestsOfOneUserCantOverlap() {
        requests.saveAndFlush(request(MON, DayPart.FULL, MON.plusDays(4), DayPart.FULL, "5", AbsenceStatus.APPROVED));

        // Both ends are inclusive, so sharing only the last day is an overlap too
        assertThatThrownBy(() -> requests.saveAndFlush(
                        request(MON.plusDays(4), DayPart.FULL, MON.plusDays(7), DayPart.FULL, "2", AbsenceStatus.PENDING)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ex_absence_requests_no_overlap");
    }

    @Test
    void rejectedAndCancelledRequestsDontBlockTheirDays() {
        requests.saveAndFlush(request(MON, DayPart.FULL, MON.plusDays(4), DayPart.FULL, "5", AbsenceStatus.REJECTED));
        requests.saveAndFlush(request(MON, DayPart.FULL, MON.plusDays(4), DayPart.FULL, "5", AbsenceStatus.CANCELLED));

        requests.saveAndFlush(request(MON, DayPart.FULL, MON.plusDays(4), DayPart.FULL, "5", AbsenceStatus.PENDING));
    }

    @Test
    void cancellingARequestFreesItsDays() {
        AbsenceRequest first = requests.saveAndFlush(
                request(MON, DayPart.FULL, MON.plusDays(4), DayPart.FULL, "5", AbsenceStatus.APPROVED));
        first.setStatus(AbsenceStatus.CANCELLED);
        requests.flush();

        requests.saveAndFlush(request(MON.plusDays(1), DayPart.FULL, MON.plusDays(1), DayPart.FULL, "1", AbsenceStatus.PENDING));
    }

    @Test
    void differentUsersCanBeAwayOnTheSameDays() {
        requests.saveAndFlush(request(MON, DayPart.FULL, MON.plusDays(4), DayPart.FULL, "5", AbsenceStatus.APPROVED));

        User eva = users.findByEmail("eva.santos@cofinpro.pt").orElseThrow();
        requests.saveAndFlush(new AbsenceRequest(eva, vacation(), MON, DayPart.FULL, MON.plusDays(4), DayPart.FULL,
                new BigDecimal("5"), AbsenceStatus.APPROVED, null));
    }

    @Test
    void aMultiDayRequestCantStartWithAMorning() {
        assertThatThrownBy(() -> requests.saveAndFlush(
                        request(MON, DayPart.MORNING, MON.plusDays(1), DayPart.FULL, "1.5", AbsenceStatus.PENDING)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_absence_requests_day_parts");
    }

    @Test
    void aSingleDayHasTheSamePartAtBothEnds() {
        assertThatThrownBy(() -> requests.saveAndFlush(
                        request(MON, DayPart.MORNING, MON, DayPart.AFTERNOON, "0.5", AbsenceStatus.PENDING)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_absence_requests_day_parts");
    }

    @Test
    void workingDaysComeInHalfDays() {
        assertThatThrownBy(() -> requests.saveAndFlush(
                        request(MON, DayPart.FULL, MON.plusDays(1), DayPart.FULL, "1.3", AbsenceStatus.PENDING)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_absence_requests_working_days");
    }

    @Test
    void oneEntitlementPerUserTypeAndYear() {
        User carla = users.findByEmail("carla.mendes@cofinpro.pt").orElseThrow();

        assertThatThrownBy(() -> entitlements.saveAndFlush(new AbsenceEntitlement(
                        carla, vacation(), LocalDate.now().getYear(), new BigDecimal("25"), BigDecimal.ZERO)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_absence_entitlements_user_type_year");
    }

    @Test
    void entitlementsComeInHalfDays() {
        User carla = users.findByEmail("carla.mendes@cofinpro.pt").orElseThrow();

        assertThatThrownBy(() -> entitlements.saveAndFlush(new AbsenceEntitlement(
                        carla, vacation(), 2030, new BigDecimal("22.3"), BigDecimal.ZERO)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_absence_entitlements_entitled");
    }

    private AbsenceRequest request(LocalDate start, DayPart startPart, LocalDate end, DayPart endPart, String days,
            AbsenceStatus status) {
        User diogo = users.findByEmail("diogo.pereira@cofinpro.pt").orElseThrow();
        User ana = users.findByEmail("ana.silva@cofinpro.pt").orElseThrow();
        return new AbsenceRequest(diogo, vacation(), start, startPart, end, endPart, new BigDecimal(days), status, ana);
    }

    private AbsenceType vacation() {
        return types.findByCode(AbsenceTypeCode.VACATION).orElseThrow();
    }
}
