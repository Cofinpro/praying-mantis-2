package pt.cofinpro.prayingmantis.projects;

import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.cofinpro.prayingmantis.auth.Permissions;
import pt.cofinpro.prayingmantis.common.ConflictException;
import pt.cofinpro.prayingmantis.common.NotFoundException;
import pt.cofinpro.prayingmantis.users.Client;

/**
 * Managing projects (BE-9.3, decision #35). Projects are never deleted, since time entries point at them;
 * deactivating one stops new hours on it (decision #32). Every method checks that the caller is an admin.
 */
@Service
public class AdminProjectService {

    static final String CODE_TAKEN = "project-code-taken";

    public record ProjectInput(String code, String name, Client client, boolean billable, boolean active) {
    }

    private final ProjectRepository projects;
    private final Permissions permissions;

    public AdminProjectService(ProjectRepository projects, Permissions permissions) {
        this.projects = projects;
        this.permissions = permissions;
    }

    /** Active and inactive, by code. */
    @Transactional(readOnly = true)
    public List<Project> all() {
        permissions.requireAdmin();
        return projects.findAllByOrderByCode();
    }

    @Transactional
    public Project create(ProjectInput input) {
        permissions.requireAdmin();
        String code = normalize(input.code());
        if (projects.existsByCode(code)) {
            throw codeTaken(code);
        }
        return save(new Project(code, input.name().strip(), input.client(), input.billable(), input.active()));
    }

    @Transactional
    public Project update(Long projectId, ProjectInput input) {
        permissions.requireAdmin();
        Project project = projects.findById(projectId).orElseThrow(() -> new NotFoundException("Project not found"));
        String code = normalize(input.code());
        if (projects.existsByCodeAndIdNot(code, projectId)) {
            throw codeTaken(code);
        }
        project.update(code, input.name().strip(), input.client(), input.billable(), input.active());
        return save(project);
    }

    /** Codes are case-insensitive for people, so one spelling is stored: upper case (T-9.1). */
    private static String normalize(String code) {
        return code.strip().toUpperCase(Locale.ROOT);
    }

    /** A concurrent create with the same code gets past the check; uq_projects_code catches it. */
    private Project save(Project project) {
        try {
            return projects.saveAndFlush(project);
        } catch (DataIntegrityViolationException e) {
            if (String.valueOf(e.getMessage()).contains("uq_projects_code")) {
                throw codeTaken(project.getCode());
            }
            throw e;
        }
    }

    private static ConflictException codeTaken(String code) {
        return new ConflictException(CODE_TAKEN, "Another project already has the code " + code);
    }
}
