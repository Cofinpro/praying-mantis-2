package pt.cofinpro.prayingmantis.absences;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AbsenceTypeRepository extends JpaRepository<AbsenceType, Long> {

    Optional<AbsenceType> findByCode(AbsenceTypeCode code);
}
