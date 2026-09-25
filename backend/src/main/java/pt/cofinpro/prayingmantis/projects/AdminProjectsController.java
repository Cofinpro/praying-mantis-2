package pt.cofinpro.prayingmantis.projects;

import java.util.List;
import org.springframework.web.bind.annotation.RestController;
import pt.cofinpro.prayingmantis.api.AdminProjectsApi;
import pt.cofinpro.prayingmantis.api.model.ProjectInput;
import pt.cofinpro.prayingmantis.users.Client;

/** Admin, projects (T-9.1). The admin check is in AdminProjectService, against the DB (decision #35). */
@RestController
public class AdminProjectsController implements AdminProjectsApi {

    private final AdminProjectService adminProjects;

    public AdminProjectsController(AdminProjectService adminProjects) {
        this.adminProjects = adminProjects;
    }

    @Override
    public List<pt.cofinpro.prayingmantis.api.model.Project> getAdminProjects() {
        return adminProjects.all().stream().map(ProjectsController::toApi).toList();
    }

    @Override
    public pt.cofinpro.prayingmantis.api.model.Project createAdminProject(ProjectInput body) {
        return ProjectsController.toApi(adminProjects.create(toInput(body)));
    }

    @Override
    public pt.cofinpro.prayingmantis.api.model.Project updateAdminProject(Long id, ProjectInput body) {
        return ProjectsController.toApi(adminProjects.update(id, toInput(body)));
    }

    private static AdminProjectService.ProjectInput toInput(ProjectInput body) {
        return new AdminProjectService.ProjectInput(body.getCode(), body.getName(),
                body.getClient() == null ? null : Client.valueOf(body.getClient().name()), body.getIsBillable(), body.getIsActive());
    }
}
