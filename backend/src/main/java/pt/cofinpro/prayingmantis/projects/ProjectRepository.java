package pt.cofinpro.prayingmantis.projects;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByActiveOrderByCode(boolean active);
}
