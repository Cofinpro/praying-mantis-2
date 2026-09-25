package pt.cofinpro.prayingmantis.absences;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import pt.cofinpro.prayingmantis.TestcontainersConfiguration;
import pt.cofinpro.prayingmantis.users.User;
import pt.cofinpro.prayingmantis.users.UserRepository;

/** GET /api/team/absences end to end (BE-5.3). Bruno leads Eva, Filipe and Hugo; the week is 12–16 Oct 2026. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class TeamCalendarIntegrationTest {

    private static final String BRUNO = "bruno.costa@cofinpro.pt";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AbsenceRequestRepository requests;

    @Autowired
    AbsenceTypeRepository types;

    @Autowired
    UserRepository users;

    @Test
    @WithUserDetails(BRUNO)
    void theLeadFirstThenTheTeamByName() throws Exception {
        range("2026-10-12", "2026-10-16")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].user.name", contains("Bruno Costa", "Eva Santos", "Filipe Rocha", "Hugo Marques")));
    }

    @Test
    @WithUserDetails(BRUNO)
    void pendingAndApprovedAbsencesWithoutReasons() throws Exception {
        save("eva.santos@cofinpro.pt", AbsenceTypeCode.VACATION, "2026-10-12", "2026-10-14", AbsenceStatus.APPROVED);
        save("filipe.rocha@cofinpro.pt", AbsenceTypeCode.TRAINING, "2026-10-14", "2026-10-14", AbsenceStatus.PENDING);
        save("hugo.marques@cofinpro.pt", AbsenceTypeCode.VACATION, "2026-10-13", "2026-10-13", AbsenceStatus.REJECTED);
        save("hugo.marques@cofinpro.pt", AbsenceTypeCode.VACATION, "2026-10-15", "2026-10-15", AbsenceStatus.CANCELLED);

        range("2026-10-12", "2026-10-16")
                .andExpect(jsonPath("$[0].absences", hasSize(0)))
                .andExpect(jsonPath("$[1].absences", hasSize(1)))
                .andExpect(jsonPath("$[1].absences[0].type").value("VACATION"))
                .andExpect(jsonPath("$[1].absences[0].status").value("APPROVED"))
                .andExpect(jsonPath("$[1].absences[0].startDate").value("2026-10-12"))
                .andExpect(jsonPath("$[1].absences[0].endDate").value("2026-10-14"))
                .andExpect(jsonPath("$[1].absences[0].reason").doesNotExist())
                .andExpect(jsonPath("$[1].absences[0].decisionComment").doesNotExist())
                .andExpect(jsonPath("$[2].absences[0].status").value("PENDING"))
                // Hugo's rejected and cancelled days block nobody
                .andExpect(jsonPath("$[3].absences", hasSize(0)));
    }

    @Test
    @WithUserDetails(BRUNO)
    void anAbsenceThatOnlyTouchesTheRangeIsIncluded() throws Exception {
        save("eva.santos@cofinpro.pt", AbsenceTypeCode.VACATION, "2026-10-08", "2026-10-12", AbsenceStatus.APPROVED);

        range("2026-10-12", "2026-10-16").andExpect(jsonPath("$[1].absences[0].startDate").value("2026-10-08"));
        range("2026-10-13", "2026-10-16").andExpect(jsonPath("$[1].absences", hasSize(0)));
    }

    @Test
    @WithUserDetails("ana.silva@cofinpro.pt")
    void onlyDirectReportsNotTheirTeams() throws Exception {
        // Ana leads Bruno, Carla and Diogo; Bruno's own team isn't hers
        range("2026-10-12", "2026-10-16")
                .andExpect(jsonPath("$[*].user.name", contains("Ana Silva", "Bruno Costa", "Carla Mendes", "Diogo Pereira")));
    }

    @Test
    @WithUserDetails("eva.santos@cofinpro.pt")
    void someoneWhoLeadsNobodySeesOnlyThemselves() throws Exception {
        range("2026-10-12", "2026-10-16").andExpect(jsonPath("$[*].user.name", contains("Eva Santos")));
    }

    @Test
    @WithUserDetails(BRUNO)
    void aBackwardsOrTooLongRangeIsAFieldError() throws Exception {
        range("2026-10-16", "2026-10-12").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("to"));
        range("2028-01-01", "2029-01-01").andExpect(status().isBadRequest());
    }

    @Test
    void theCalendarNeedsALogin() throws Exception {
        range("2026-10-12", "2026-10-16").andExpect(status().isUnauthorized());
    }

    private ResultActions range(String from, String to) throws Exception {
        return mockMvc.perform(get("/api/team/absences").param("from", from).param("to", to));
    }

    private void save(String email, AbsenceTypeCode type, String start, String end, AbsenceStatus status) {
        User user = users.findByEmail(email).orElseThrow();
        AbsenceRequest r = new AbsenceRequest(user, types.findByCode(type).orElseThrow(), LocalDate.parse(start), DayPart.FULL,
                LocalDate.parse(end), DayPart.FULL, BigDecimal.ONE, status, user.getTeamLead());
        r.setReason("Private reason");
        r.setDecisionComment("Private comment");
        requests.saveAndFlush(r);
    }
}
