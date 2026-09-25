package pt.cofinpro.prayingmantis.absences;

import java.util.List;
import org.springframework.web.bind.annotation.RestController;
import pt.cofinpro.prayingmantis.api.AdminEntitlementsApi;
import pt.cofinpro.prayingmantis.api.model.AdminEntitlement;
import pt.cofinpro.prayingmantis.api.model.EntitlementInput;
import pt.cofinpro.prayingmantis.api.model.UserRef;

/** Admin, entitlements (T-9.1). The admin check is in AdminEntitlementService, against the DB (decision #35). */
@RestController
public class AdminEntitlementsController implements AdminEntitlementsApi {

    private final AdminEntitlementService adminEntitlements;

    public AdminEntitlementsController(AdminEntitlementService adminEntitlements) {
        this.adminEntitlements = adminEntitlements;
    }

    @Override
    public List<AdminEntitlement> getAdminEntitlements(Integer year) {
        return adminEntitlements.forYear(year).stream().map(AdminEntitlementsController::toApi).toList();
    }

    @Override
    public AdminEntitlement saveAdminEntitlement(EntitlementInput body) {
        return toApi(adminEntitlements.save(body.getUserId(), AbsenceTypeCode.valueOf(body.getType().name()), body.getYear(),
                body.getEntitledDays(), body.getCarriedOverDays()));
    }

    @Override
    public void deleteAdminEntitlement(Long id) {
        adminEntitlements.delete(id);
    }

    private static AdminEntitlement toApi(AbsenceEntitlement e) {
        return new AdminEntitlement(e.getId(), new UserRef(e.getUser().getId(), e.getUser().getName()),
                AbsenceMapper.toApi(e.getType().getCode()), e.getYear(), e.getEntitledDays(), e.getCarriedOverDays());
    }
}
