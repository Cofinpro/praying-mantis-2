package pt.cofinpro.prayingmantis.users;

import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    /** Everyone with their team lead loaded, by name (BE-9.1). */
    @Query("select u from User u left join fetch u.teamLead order by u.name, u.id")
    List<User> findAllWithTeamLead();

    /** The ids of everyone who leads someone: "is team lead" for a whole list in one query (decision #9). */
    @Query("select distinct u.teamLead.id from User u where u.teamLead is not null")
    Set<Long> findTeamLeadIds();

    long countByAdminTrue();

    /** Pass an email normalized with {@link User#normalizeEmail(String)}. */
    boolean existsByEmailAndIdNot(String email, Long id);

    boolean existsByEmail(String email);

    /** The fallback approver (decision #16): the first admin by id, never the requester themselves. */
    Optional<User> findFirstByAdminTrueAndIdNotOrderByIdAsc(Long excludedUserId);
}
