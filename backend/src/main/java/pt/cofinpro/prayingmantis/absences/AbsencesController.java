package pt.cofinpro.prayingmantis.absences;

import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.RestController;
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
    private final AbsenceRequestService requestService;

    public AbsencesController(
            AbsenceTypeService typeService, AbsenceBalanceService balanceService, AbsenceRequestService requestService) {
        this.typeService = typeService;
        this.balanceService = balanceService;
        this.requestService = requestService;
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

    @Override
    public List<AbsenceRequest> getMyAbsenceRequests(LocalDate from, LocalDate to) {
        return requestService.overlapping(AuthenticatedUsers.current().getId(), from, to).stream()
                .map(AbsenceMapper::toApi)
                .toList();
    }
}
