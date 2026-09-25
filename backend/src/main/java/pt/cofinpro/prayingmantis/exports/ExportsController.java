package pt.cofinpro.prayingmantis.exports;

import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pt.cofinpro.prayingmantis.api.ExportsApi;
import pt.cofinpro.prayingmantis.api.model.TimesheetMonth;

/** Export templates and the monthly export (T-8.1). The templates are the same for everyone. */
@RestController
public class ExportsController implements ExportsApi {

    private final ExportTemplateRegistry registry;

    public ExportsController(ExportTemplateRegistry registry) {
        this.registry = registry;
    }

    @Override
    public List<pt.cofinpro.prayingmantis.api.model.ExportTemplate> getExportTemplates() {
        return registry.all().stream()
                .map(t -> new pt.cofinpro.prayingmantis.api.model.ExportTemplate(t.code(), t.name())
                        .client(t.client() == null ? null : pt.cofinpro.prayingmantis.api.model.Client.valueOf(t.client().name())))
                .toList();
    }

    /** BE-8.2 (SCRUM-69). The generated interface has no default methods, so it needs a body until then. */
    @Override
    public TimesheetMonth getMyTimesheetMonth(String month) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Coming in BE-8.2");
    }

    /** BE-8.2 (SCRUM-69). */
    @Override
    public Resource exportMyTimesheetMonth(String month, String template) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Coming in BE-8.2");
    }
}
