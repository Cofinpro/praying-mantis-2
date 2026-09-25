package pt.cofinpro.prayingmantis.absences;

import pt.cofinpro.prayingmantis.api.model.AbsenceBalance;
import pt.cofinpro.prayingmantis.api.model.UserRef;
import pt.cofinpro.prayingmantis.users.User;

/** Domain to contract types. The enums share their names with the API enums, as in CurrentUserMapper. */
final class AbsenceMapper {

    private AbsenceMapper() {
    }

    static pt.cofinpro.prayingmantis.api.model.AbsenceType toApi(AbsenceType type) {
        return new pt.cofinpro.prayingmantis.api.model.AbsenceType(
                toApi(type.getCode()),
                type.getName(),
                type.isPaid(),
                type.isDeductsFromBalance(),
                type.isRequiresApproval());
    }

    static AbsenceBalance toApi(TypeBalance balance) {
        return new AbsenceBalance(
                        toApi(balance.type()),
                        balance.year(),
                        balance.entitledDays(),
                        balance.carriedOverDays(),
                        balance.usedDays(),
                        balance.pendingDays())
                .remainingDays(balance.remainingDays());
    }

    /** The type and the approver must be loaded (findForCalendar fetches both). */
    static pt.cofinpro.prayingmantis.api.model.AbsenceRequest toApi(AbsenceRequest request) {
        return new pt.cofinpro.prayingmantis.api.model.AbsenceRequest(
                        request.getId(),
                        toApi(request.getType().getCode()),
                        request.getStartDate(),
                        request.getEndDate(),
                        pt.cofinpro.prayingmantis.api.model.DayPart.valueOf(request.getStartPart().name()),
                        pt.cofinpro.prayingmantis.api.model.DayPart.valueOf(request.getEndPart().name()),
                        request.getWorkingDays(),
                        pt.cofinpro.prayingmantis.api.model.AbsenceStatus.valueOf(request.getStatus().name()),
                        request.getCreatedAt())
                .reason(request.getReason())
                .approver(toRef(request.getApprover()))
                .decidedAt(request.getDecidedAt())
                .decisionComment(request.getDecisionComment());
    }

    static pt.cofinpro.prayingmantis.api.model.AbsenceTypeCode toApi(AbsenceTypeCode code) {
        return pt.cofinpro.prayingmantis.api.model.AbsenceTypeCode.valueOf(code.name());
    }

    private static UserRef toRef(User user) {
        return user == null ? null : new UserRef(user.getId(), user.getName());
    }
}
