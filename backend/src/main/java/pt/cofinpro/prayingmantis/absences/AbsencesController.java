package pt.cofinpro.prayingmantis.absences;

import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pt.cofinpro.prayingmantis.api.AbsencesApi;
import pt.cofinpro.prayingmantis.api.model.AbsenceBalance;
import pt.cofinpro.prayingmantis.api.model.AbsenceRequest;
import pt.cofinpro.prayingmantis.api.model.AbsenceType;
import pt.cofinpro.prayingmantis.auth.AuthenticatedUsers;

/** Only reads the caller's own data, so there's nothing to check beyond being logged in (decision #11). */
@RestController
public class AbsencesController implements AbsencesApi {

    private final AbsenceTypeService typeService;
    private final AbsenceBalanceService balanceService;

    public AbsencesController(AbsenceTypeService typeService, AbsenceBalanceService balanceService) {
        this.typeService = typeService;
        this.balanceService = balanceService;
    }

    @Override
    public List<AbsenceType> getAbsenceTypes() {
        return typeService.all().stream().map(AbsenceMapper::toApi).toList();
    }

    @Override
    public List<AbsenceBalance> getMyAbsenceBalance(Integer year) {
        return balanceService.balance(AuthenticatedUsers.current().getId(), year).stream()
                .map(AbsenceMapper::toApi)
                .toList();
    }

    /** BE-2.3 (SCRUM-32). The generated interface has no default methods, so it needs a body until then. */
    @Override
    public List<AbsenceRequest> getMyAbsenceRequests(LocalDate from, LocalDate to) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Coming in BE-2.3");
    }
}
