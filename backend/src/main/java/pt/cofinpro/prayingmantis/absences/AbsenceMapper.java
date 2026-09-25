package pt.cofinpro.prayingmantis.absences;

import pt.cofinpro.prayingmantis.api.model.AbsenceBalance;

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

    static pt.cofinpro.prayingmantis.api.model.AbsenceTypeCode toApi(AbsenceTypeCode code) {
        return pt.cofinpro.prayingmantis.api.model.AbsenceTypeCode.valueOf(code.name());
    }
}
