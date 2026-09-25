package pt.cofinpro.prayingmantis.projects;

import java.util.List;
import org.springframework.web.bind.annotation.RestController;
import pt.cofinpro.prayingmantis.api.ProjectsApi;

/** Every logged-in user may see the projects: they book hours on them (decision #11). */
@RestController
public class ProjectsController implements ProjectsApi {

    private final ProjectService projectService;

    public ProjectsController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @Override
    public List<pt.cofinpro.prayingmantis.api.model.Project> getProjects(Boolean active) {
        return projectService.list(active).stream().map(ProjectsController::toApi).toList();
    }

    static pt.cofinpro.prayingmantis.api.model.Project toApi(Project project) {
        return new pt.cofinpro.prayingmantis.api.model.Project(
                        project.getId(), project.getCode(), project.getName(), project.isBillable(), project.isActive())
                .client(project.getClient() == null
                        ? null
                        : pt.cofinpro.prayingmantis.api.model.Client.valueOf(project.getClient().name()));
    }
}
