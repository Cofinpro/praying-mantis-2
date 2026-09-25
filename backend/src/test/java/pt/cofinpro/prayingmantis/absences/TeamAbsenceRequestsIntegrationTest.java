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
import org.springframework.transaction.annotation.Transactional;
import pt.cofinpro.prayingmantis.TestcontainersConfiguration;
import pt.cofinpro.prayingmantis.users.User;
import pt.cofinpro.prayingmantis.users.UserRepository;

/**
 * GET /api/team/absence-requests end to end (BE-5.1). The dev seed gives Ana, Carla's team lead, one pending
 * request (Carla's, in the week of 18 Nov) and two approved vacations; the sick day has no approver.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class TeamAbsenceRequestsIntegrationTest {

    private static final int YEAR = LocalDate.now().getYear();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AbsenceTypeRepository types;

    @Autowired
    AbsenceRequestRepository requests;

    @Autowired
    UserRepository users;

    @Test
    @WithUserDetails("ana.silva@cofinpro.pt")
    void aTeamLeadSeesTheirPendingRequestsWithTheRequestersBalance() throws Exception {
        mockMvc.perform(get("/api/team/absence-requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].requester.name").value("Carla Mendes"))
                .andExpect(jsonPath("$[0].request.status").value("PENDING"))
                .andExpect(jsonPath("$[0].request.type").value("VACATION"))
                .andExpect(jsonPath("$[0].request.workingDays").value(3.0))
                .andExpect(jsonPath("$[0].request.approver.name").value("Ana Silva"))
                // 22 + 2.5 carried over − 5.5 approved; the pending 3 aren't subtracted
                .andExpect(jsonPath("$[0].remainingDays").value(19.0));
    }

    @Test
    @WithUserDetails("ana.silva@cofinpro.pt")
    void pendingRequestsComeSoonestFirst() throws Exception {
        User diogo = users.findByEmail("diogo.pereira@cofinpro.pt").orElseThrow();
        save(diogo, AbsenceTypeCode.VACATION, YEAR + 1 + "-03-01", AbsenceStatus.PENDING);
        save(diogo, AbsenceTypeCode.TRAINING, YEAR + 1 + "-01-11", AbsenceStatus.PENDING);

        mockMvc.perform(get("/api/team/absence-requests").param("status", "PENDING"))
                .andExpect(jsonPath("$[*].request.startDate", contains(
                        LocalDate.of(YEAR, 11, 18).with(java.time.DayOfWeek.MONDAY).toString(),
                        YEAR + 1 + "-01-11",
                        YEAR + 1 + "-03-01")));
    }

    @Test
    @WithUserDetails("ana.silva@cofinpro.pt")
    void typesThatDontDeductHaveNoRemainingDays() throws Exception {
        User diogo = users.findByEmail("diogo.pereira@cofinpro.pt").orElseThrow();
        save(diogo, AbsenceTypeCode.TRAINING, YEAR + 1 + "-01-11", AbsenceStatus.PENDING);

        // Carla's vacation (November) first, then Diogo's training (January next year)
        mockMvc.perform(get("/api/team/absence-requests"))
                .andExpect(jsonPath("$[1].request.type").value("TRAINING"))
                .andExpect(jsonPath("$[1].remainingDays").doesNotExist())
                .andExpect(jsonPath("$[0].remainingDays").value(19.0));
    }

    @Test
    @WithUserDetails("ana.silva@cofinpro.pt")
    void decidedRequestsComeNewestFirst() throws Exception {
        // Carla's approved vacations: the week of 15 Feb and a Tuesday afternoon in March
        mockMvc.perform(get("/api/team/absence-requests").param("status", "APPROVED"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].request.startPart").value("AFTERNOON"))
                .andExpect(jsonPath("$[1].request.workingDays").value(5.0));
    }

    @Test
    @WithUserDetails("alex.admin@cofinpro.pt")
    void theAdminSeesTheRequestsOfPeopleWithoutATeamLead() throws Exception {
        User gabriela = users.findByEmail("gabriela.lopes@cofinpro.pt").orElseThrow();
        User alex = users.findByEmail("alex.admin@cofinpro.pt").orElseThrow();
        requests.saveAndFlush(new AbsenceRequest(gabriela, types.findByCode(AbsenceTypeCode.VACATION).orElseThrow(),
                LocalDate.parse(YEAR + 1 + "-02-08"), DayPart.FULL, LocalDate.parse(YEAR + 1 + "-02-08"), DayPart.FULL,
                BigDecimal.ONE, AbsenceStatus.PENDING, alex));

        mockMvc.perform(get("/api/team/absence-requests"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].requester.name").value("Gabriela Lopes"))
                // No entitlement for next year: nothing left
                .andExpect(jsonPath("$[0].remainingDays").value(0));
    }

    @Test
    @WithUserDetails("diogo.pereira@cofinpro.pt")
    void someoneWhoApprovesNobodyGetsAnEmptyList() throws Exception {
        mockMvc.perform(get("/api/team/absence-requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithUserDetails("bruno.costa@cofinpro.pt")
    void aLeadDoesntSeeTheirOwnLeadsRequests() throws Exception {
        // Carla's pending request goes to Ana, not to Bruno, even though Bruno is a team lead too
        mockMvc.perform(get("/api/team/absence-requests")).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithUserDetails("ana.silva@cofinpro.pt")
    void anUnknownStatusIsABadRequest() throws Exception {
        mockMvc.perform(get("/api/team/absence-requests").param("status", "WITHDRAWN"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void theListNeedsALogin() throws Exception {
        mockMvc.perform(get("/api/team/absence-requests")).andExpect(status().isUnauthorized());
    }

    private void save(User user, AbsenceTypeCode type, String day, AbsenceStatus status) {
        requests.saveAndFlush(new AbsenceRequest(user, types.findByCode(type).orElseThrow(),
                LocalDate.parse(day), DayPart.FULL, LocalDate.parse(day), DayPart.FULL, BigDecimal.ONE, status,
                user.getTeamLead()));
    }
}
