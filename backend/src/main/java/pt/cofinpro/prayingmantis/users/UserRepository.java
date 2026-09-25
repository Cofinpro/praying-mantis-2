package pt.cofinpro.prayingmantis.users;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Pass an email normalized with {@link User#normalizeEmail(String)}. */
    Optional<User> findByEmail(String email);

    /** True when someone has this user as team lead, which is what "is team lead" means (decision #9). */
    boolean existsByTeamLeadId(Long userId);
}
