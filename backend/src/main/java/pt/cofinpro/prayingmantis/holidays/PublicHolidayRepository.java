package pt.cofinpro.prayingmantis.holidays;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicHolidayRepository extends JpaRepository<PublicHoliday, Long> {

    /** Both ends inclusive. */
    List<PublicHoliday> findByDateBetweenOrderByDate(LocalDate from, LocalDate to);
}
