package pt.cofinpro.prayingmantis.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pt.cofinpro.prayingmantis.TestcontainersConfiguration;
import pt.cofinpro.prayingmantis.XsrfToken;
import pt.cofinpro.prayingmantis.users.User;
import pt.cofinpro.prayingmantis.users.UserRepository;

/** The four notification endpoints end to end (BE-4.2). Ana gets 25 notifications, the oldest 5 read. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class NotificationEndpointsIntegrationTest {

    private static final String ANA = "ana.silva@cofinpro.pt";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    NotificationService notificationService;

    @Autowired
    NotificationRepository notifications;

    @Autowired
    UserRepository users;

    private final List<Long> anas = new ArrayList<>();
    private Long brunos;

    @BeforeEach
    void setUp() {
        User ana = users.findByEmail(ANA).orElseThrow();
        for (int i = 1; i <= 25; i++) {
            Notification n = notificationService.notify(ana, NotificationType.ABSENCE_REQUESTED, "Request " + i, "/approvals");
            if (i <= 5) {
                n.setReadAt(Instant.parse("2026-09-01T10:00:00Z"));
            }
            anas.add(n.getId());
        }
        brunos = notificationService.notify(users.findByEmail("bruno.costa@cofinpro.pt").orElseThrow(),
                NotificationType.TIMESHEET_SUBMITTED, "Eva submitted", "/approvals?tab=timesheets").getId();
        notifications.flush();
    }

    @Test
    @WithUserDetails(ANA)
    void theFirstPageIsTheNewest20() throws Exception {
        mockMvc.perform(get("/api/me/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(20)))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.items[0].id").value(anas.get(24)))
                .andExpect(jsonPath("$.items[0].message").value("Request 25"))
                .andExpect(jsonPath("$.items[0].type").value("ABSENCE_REQUESTED"))
                .andExpect(jsonPath("$.items[0].link").value("/approvals"))
                .andExpect(jsonPath("$.items[0].readAt").doesNotExist())
                .andExpect(jsonPath("$.items[0].createdAt").isString());
    }

    @Test
    @WithUserDetails(ANA)
    void theCursorGivesTheNextPage() throws Exception {
        mockMvc.perform(get("/api/me/notifications").param("before", String.valueOf(anas.get(5))))
                .andExpect(jsonPath("$.items[*].message", contains("Request 5", "Request 4", "Request 3", "Request 2", "Request 1")))
                .andExpect(jsonPath("$.items[0].readAt").value("2026-09-01T10:00:00Z"))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    @WithUserDetails(ANA)
    void limitAndUnreadFilter() throws Exception {
        mockMvc.perform(get("/api/me/notifications").param("limit", "3").param("unread", "true").param("before", String.valueOf(anas.get(7))))
                // Requests 7, 6 are unread; 5 and older are read
                .andExpect(jsonPath("$.items[*].message", contains("Request 7", "Request 6")))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    @WithUserDetails(ANA)
    void aLimitOutsideOneTo100IsAValidationError() throws Exception {
        mockMvc.perform(get("/api/me/notifications").param("limit", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/me/notifications").param("limit", "101")).andExpect(status().isBadRequest());
    }

    @Test
    @WithUserDetails(ANA)
    void theUnreadCount() throws Exception {
        mockMvc.perform(get("/api/me/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(20));
    }

    @Test
    @WithUserDetails(ANA)
    void markingOneReadIsIdempotent() throws Exception {
        Long latest = anas.get(24);

        mockMvc.perform(post("/api/me/notifications/{id}/read", latest).with(XsrfToken.from(mockMvc))).andExpect(status().isNoContent());
        Instant first = notifications.findById(latest).orElseThrow().getReadAt();
        mockMvc.perform(post("/api/me/notifications/{id}/read", latest).with(XsrfToken.from(mockMvc))).andExpect(status().isNoContent());

        assertThat(first).isNotNull();
        assertThat(notifications.findById(latest).orElseThrow().getReadAt()).isEqualTo(first);
        mockMvc.perform(get("/api/me/notifications/unread-count")).andExpect(jsonPath("$.count").value(19));
    }

    @Test
    @WithUserDetails(ANA)
    void someoneElsesNotificationIsNotFound() throws Exception {
        mockMvc.perform(post("/api/me/notifications/{id}/read", brunos).with(XsrfToken.from(mockMvc)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Notification not found"));
        assertThat(notifications.findById(brunos).orElseThrow().getReadAt()).isNull();
    }

    @Test
    @WithUserDetails(ANA)
    void markAllReadOnlyTouchesMyUnreadOnes() throws Exception {
        mockMvc.perform(post("/api/me/notifications/read-all").with(XsrfToken.from(mockMvc))).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/me/notifications/unread-count")).andExpect(jsonPath("$.count").value(0));
        // The already read ones keep their first readAt; Bruno's stays unread
        assertThat(notifications.findById(anas.get(0)).orElseThrow().getReadAt()).isEqualTo(Instant.parse("2026-09-01T10:00:00Z"));
        assertThat(notifications.findById(brunos).orElseThrow().getReadAt()).isNull();

        // Nothing left to mark is still a 204
        mockMvc.perform(post("/api/me/notifications/read-all").with(XsrfToken.from(mockMvc))).andExpect(status().isNoContent());
    }

    @Test
    @WithUserDetails("diogo.pereira@cofinpro.pt")
    void someoneWithoutNotificationsGetsAnEmptyPage() throws Exception {
        mockMvc.perform(get("/api/me/notifications"))
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.hasMore").value(false));
        mockMvc.perform(get("/api/me/notifications/unread-count")).andExpect(jsonPath("$.count").value(0));
    }

    @Test
    @WithUserDetails(ANA)
    void markingNeedsTheCsrfToken() throws Exception {
        mockMvc.perform(post("/api/me/notifications/read-all")).andExpect(status().isForbidden());
    }

    @Test
    void notificationsNeedALogin() throws Exception {
        mockMvc.perform(get("/api/me/notifications")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me/notifications/unread-count")).andExpect(status().isUnauthorized());
    }
}
