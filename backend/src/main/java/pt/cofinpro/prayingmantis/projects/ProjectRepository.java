package pt.cofinpro.prayingmantis.projects;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByActiveOrderByCode(boolean active);

    List<Project> findAllByOrderByCode();

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);
}
