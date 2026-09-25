package pt.cofinpro.prayingmantis.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Transactional;
import pt.cofinpro.prayingmantis.TestcontainersConfiguration;
import pt.cofinpro.prayingmantis.XsrfToken;
import pt.cofinpro.prayingmantis.absences.AbsenceEntitlement;
import pt.cofinpro.prayingmantis.absences.AbsenceEntitlementRepository;
import pt.cofinpro.prayingmantis.absences.AbsenceRequest;
import pt.cofinpro.prayingmantis.absences.AbsenceRequestRepository;
import pt.cofinpro.prayingmantis.absences.AbsenceStatus;
import pt.cofinpro.prayingmantis.absences.AbsenceTypeCode;
import pt.cofinpro.prayingmantis.absences.AbsenceTypeRepository;
import pt.cofinpro.prayingmantis.absences.DayPart;
import pt.cofinpro.prayingmantis.users.User;
import pt.cofinpro.prayingmantis.users.UserRepository;

/**
 * BE-4.1: notifications are rows written in the same transaction as the change they're about. The absence
 * flows on main (request and cancel) now store them; the other types follow with epics 5 to 7.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class NotificationsStoredIntegrationTest {

    private static final int NEXT_YEAR = LocalDate.now().getYear() + 1;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    NotificationService notificationService;

    @Autowired
    NotificationRepository notifications;

    @Autowired
    AbsenceRequestRepository requests;

    @Autowired
    AbsenceEntitlementRepository entitlements;

    @Autowired
    AbsenceTypeRepository types;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @Transactional
    @WithUserDetails("diogo.pereira@cofinpro.pt")
    void aNewRequestNotifiesItsApprover() throws Exception {
        User diogo = users.findByEmail("diogo.pereira@cofinpro.pt").orElseThrow();
        entitlements.saveAndFlush(new AbsenceEntitlement(diogo, types.findByCode(AbsenceTypeCode.VACATION).orElseThrow(),
                NEXT_YEAR, new BigDecimal("22"), BigDecimal.ZERO));
        LocalDate monday = LocalDate.of(NEXT_YEAR, 2, 1).with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY));

        mockMvc.perform(post("/api/me/absence-requests").with(XsrfToken.from(mockMvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "VACATION", "startDate": "%s", "startPart": "FULL", "endDate": "%s", "endPart": "FULL"}"""
                                .formatted(monday, monday.plusDays(4))))
                .andExpect(status().isCreated());

        List<Map<String, Object>> rows = rowsFor("ana.silva@cofinpro.pt");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0))
                .containsEntry("type", "ABSENCE_REQUESTED")
                .containsEntry("link", "/approvals")
                .containsEntry("read_at", null);
        assertThat((String) rows.get(0).get("message")).startsWith("Diogo Pereira requested 5 days of vacation (");
    }

    @Test
    @Transactional
    @WithUserDetails("diogo.pereira@cofinpro.pt")
    void sickLeaveNotifiesNobody() throws Exception {
        mockMvc.perform(post("/api/me/absence-requests").with(XsrfToken.from(mockMvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "SICK", "startDate": "2026-01-05", "startPart": "FULL", "endDate": "2026-01-05", "endPart": "FULL"}"""))
                .andExpect(status().isCreated());

        assertThat(notifications.count()).isZero();
    }

    @Test
    @Transactional
    @WithUserDetails("diogo.pereira@cofinpro.pt")
    void cancellingAnApprovedRequestNotifiesItsApprover() throws Exception {
        User diogo = users.findByEmail("diogo.pereira@cofinpro.pt").orElseThrow();
        LocalDate day = LocalDate.of(NEXT_YEAR, 3, 1).with(TemporalAdjusters.firstInMonth(DayOfWeek.TUESDAY));
        Long id = requests.saveAndFlush(new AbsenceRequest(diogo, types.findByCode(AbsenceTypeCode.VACATION).orElseThrow(),
                day, DayPart.FULL, day, DayPart.FULL, BigDecimal.ONE, AbsenceStatus.APPROVED, diogo.getTeamLead())).getId();

        mockMvc.perform(post("/api/me/absence-requests/{id}/cancel", id).with(XsrfToken.from(mockMvc))).andExpect(status().isOk());

        assertThat(rowsFor("ana.silva@cofinpro.pt")).singleElement()
                .satisfies(row -> {
                    assertThat(row).containsEntry("type", "ABSENCE_CANCELLED").containsEntry("link", "/approvals");
                    assertThat((String) row.get("message")).startsWith("Diogo Pereira cancelled their vacation (");
                });
    }

    @Test
    @Transactional
    @WithUserDetails("diogo.pereira@cofinpro.pt")
    void cancellingAPendingRequestNotifiesNobody() throws Exception {
        User diogo = users.findByEmail("diogo.pereira@cofinpro.pt").orElseThrow();
        LocalDate day = LocalDate.of(NEXT_YEAR, 3, 1).with(TemporalAdjusters.firstInMonth(DayOfWeek.TUESDAY));
        Long id = requests.saveAndFlush(new AbsenceRequest(diogo, types.findByCode(AbsenceTypeCode.VACATION).orElseThrow(),
                day, DayPart.FULL, day, DayPart.FULL, BigDecimal.ONE, AbsenceStatus.PENDING, diogo.getTeamLead())).getId();

        mockMvc.perform(post("/api/me/absence-requests/{id}/cancel", id).with(XsrfToken.from(mockMvc))).andExpect(status().isOk());

        assertThat(notifications.count()).isZero();
    }

    /** No transaction here on purpose: MANDATORY refuses to run on its own. */
    @Test
    void notifyNeedsTheCallersTransaction() {
        User ana = users.findByEmail("ana.silva@cofinpro.pt").orElseThrow();

        assertThatThrownBy(() -> notificationService.notify(ana, NotificationType.ABSENCE_REQUESTED, "x", "/approvals"))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(notifications.count()).isZero();
    }

    /**
     * No transaction on purpose: in Postgres a failed statement aborts the whole transaction, so the second
     * insert would fail with "current transaction is aborted" instead of the check. Each insert commits alone.
     */
    @Test
    void aLinkMustBeAnInAppPath() {
        User ana = users.findByEmail("ana.silva@cofinpro.pt").orElseThrow();

        for (String bad : List.of("https://evil.example", "//evil.example", "/\\evil.example", "approvals")) {
            assertThatThrownBy(() -> jdbc.update("insert into notifications (user_id, type, message, link) values (?, 'ABSENCE_REQUESTED', 'x', ?)",
                            ana.getId(), bad))
                    .as(bad)
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ck_notifications_link");
        }
    }

    private List<Map<String, Object>> rowsFor(String email) {
        return jdbc.queryForList("""
                select n.type, n.message, n.link, n.read_at from notifications n join users u on u.id = n.user_id
                where u.email = ? order by n.id""", email);
    }
}
