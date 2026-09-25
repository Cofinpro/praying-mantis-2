package pt.cofinpro.prayingmantis.users;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Pass an email normalized with {@link User#normalizeEmail(String)}. */
    Optional<User> findByEmail(String email);

    /** True when someone has this user as team lead, which is what "is team lead" means (decision #9). */
    boolean existsByTeamLeadId(Long userId);

    /** Is this user an admin? One query, no entity loaded. */
    boolean existsByIdAndAdminTrue(Long userId);

    /** Is {@code teamLeadId} the direct team lead of {@code userId}? One query, no entity loaded. */
    boolean existsByIdAndTeamLeadId(Long userId, Long teamLeadId);

    /** The user's team lead, fully loaded, so it's usable outside the caller's persistence context. */
    @Query("select lead from User u join u.teamLead lead where u.id = :userId")
    Optional<User> findTeamLeadOf(@Param("userId") Long userId);

    /** The fallback approver (decision #16): the first admin by id, never the requester themselves. */
    Optional<User> findFirstByAdminTrueAndIdNotOrderByIdAsc(Long excludedUserId);
}
