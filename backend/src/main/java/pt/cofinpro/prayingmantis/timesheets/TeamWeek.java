package pt.cofinpro.prayingmantis.timesheets;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pt.cofinpro.prayingmantis.projects.Project;

/** A week as its approver sees it (T-7.1): the whole week, and its hours per project for the collapsed row. */
public record TeamWeek(Week week) {

    public record ProjectHours(Project project, BigDecimal hours) {
    }

    /** By project code: the entries already come ordered by project code, then day. */
    public List<ProjectHours> projectHours() {
        Map<Long, ProjectHours> byProject = new LinkedHashMap<>();
        for (TimeEntry e : week.entries()) {
            byProject.merge(e.getProject().getId(), new ProjectHours(e.getProject(), e.getHours()),
                    (a, b) -> new ProjectHours(a.project(), a.hours().add(b.hours())));
        }
        return List.copyOf(byProject.values());
    }
}
