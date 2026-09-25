package pt.cofinpro.prayingmantis.projects;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private final ProjectRepository projects;

    public ProjectService(ProjectRepository projects) {
        this.projects = projects;
    }

    /** Active projects by default, the ones that take new hours; ordered by code (T-6.1). */
    @Transactional(readOnly = true)
    public List<Project> list(Boolean active) {
        return projects.findByActiveOrderByCode(active == null || active);
    }
}
