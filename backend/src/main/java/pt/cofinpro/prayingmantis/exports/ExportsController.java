package pt.cofinpro.prayingmantis.exports;

import jakarta.servlet.http.HttpServletResponse;
import java.time.YearMonth;
import java.util.List;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RestController;
import pt.cofinpro.prayingmantis.api.ExportsApi;
import pt.cofinpro.prayingmantis.api.model.TimesheetMonth;
import pt.cofinpro.prayingmantis.api.model.TimesheetMonthWeek;
import pt.cofinpro.prayingmantis.auth.AuthenticatedUsers;

/** Export templates and the monthly export (T-8.1). Only ever the caller's own month (decision #11). */
@RestController
public class ExportsController implements ExportsApi {

    private final ExportTemplateRegistry registry;
    private final ExportService exportService;
    private final HttpServletResponse response;

    /**
     * {@code response} is a request-scoped proxy that Spring injects for servlet types: the generated interface
     * returns only the body, so this is how the download gets its Content-Disposition header.
     */
    public ExportsController(ExportTemplateRegistry registry, ExportService exportService, HttpServletResponse response) {
        this.registry = registry;
        this.exportService = exportService;
        this.response = response;
    }

    @Override
    public List<pt.cofinpro.prayingmantis.api.model.ExportTemplate> getExportTemplates() {
        return registry.all().stream()
                .map(t -> new pt.cofinpro.prayingmantis.api.model.ExportTemplate(t.code(), t.name())
                        .client(t.client() == null ? null : pt.cofinpro.prayingmantis.api.model.Client.valueOf(t.client().name())))
                .toList();
    }

    /** {@code month} already matched the contract's YYYY-MM pattern, so parsing can't fail. */
    @Override
    public TimesheetMonth getMyTimesheetMonth(String month) {
        ExportService.MonthSummary summary = exportService.summary(AuthenticatedUsers.current().getId(), YearMonth.parse(month));
        return new TimesheetMonth(
                summary.month().toString(),
                summary.totalHours(),
                summary.weeks().stream()
                        .map(w -> new TimesheetMonthWeek(
                                w.weekStart(),
                                pt.cofinpro.prayingmantis.api.model.TimesheetStatus.valueOf(w.status().name()),
                                w.hoursInMonth()))
                        .toList());
    }

    @Override
    public Resource exportMyTimesheetMonth(String month, String template) {
        ExportService.ExportFile file = exportService.export(AuthenticatedUsers.current().getId(), YearMonth.parse(month), template);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(file.filename()).build().toString());
        return new ByteArrayResource(file.content());
    }
}
