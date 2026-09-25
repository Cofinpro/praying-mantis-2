package pt.cofinpro.prayingmantis.absences;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AbsenceEntitlementRepository extends JpaRepository<AbsenceEntitlement, Long> {

    /** A user's entitlements for one year, with their type loaded, in type name order (T-2.1). */
    @Query("""
            select e from AbsenceEntitlement e join fetch e.type t
            where e.user.id = :userId and e.year = :year
            order by t.name""")
    List<AbsenceEntitlement> findWithTypeByUserIdAndYear(@Param("userId") Long userId, @Param("year") int year);
}
