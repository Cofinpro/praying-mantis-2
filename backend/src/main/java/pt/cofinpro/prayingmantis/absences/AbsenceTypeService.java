package pt.cofinpro.prayingmantis.absences;

import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AbsenceTypeService {

    private final AbsenceTypeRepository types;

    public AbsenceTypeService(AbsenceTypeRepository types) {
        this.types = types;
    }

    /** Every type, ordered by name (T-2.1). */
    @Transactional(readOnly = true)
    public List<AbsenceType> all() {
        return types.findAll(Sort.by("name"));
    }
}
