package pt.cofinpro.prayingmantis.absences;

import org.springframework.web.bind.annotation.RestController;
import pt.cofinpro.prayingmantis.api.AbsenceRequestsApi;
import pt.cofinpro.prayingmantis.api.model.AbsenceRequest;
import pt.cofinpro.prayingmantis.api.model.NewAbsenceRequest;
import pt.cofinpro.prayingmantis.auth.AuthenticatedUsers;

/** Create and cancel the caller's own requests (T-3.1). The rules are in AbsenceRequestService. */
@RestController
public class AbsenceRequestsController implements AbsenceRequestsApi {

    private final AbsenceRequestService requestService;

    public AbsenceRequestsController(AbsenceRequestService requestService) {
        this.requestService = requestService;
    }

    @Override
    public AbsenceRequest createMyAbsenceRequest(NewAbsenceRequest body) {
        return AbsenceMapper.toApi(requestService.create(
                AuthenticatedUsers.current().getId(),
                AbsenceTypeCode.valueOf(body.getType().name()),
                body.getStartDate(),
                DayPart.valueOf(body.getStartPart().name()),
                body.getEndDate(),
                DayPart.valueOf(body.getEndPart().name()),
                body.getReason()));
    }

    @Override
    public AbsenceRequest cancelMyAbsenceRequest(Long id) {
        return AbsenceMapper.toApi(requestService.cancel(AuthenticatedUsers.current().getId(), id));
    }
}
