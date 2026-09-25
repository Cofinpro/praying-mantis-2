package pt.cofinpro.prayingmantis.exports;

import org.springframework.stereotype.Component;
import pt.cofinpro.prayingmantis.users.Client;

/** The template for every client until their own sheet exists (decision #33). BE-8.2 writes its workbook. */
@Component
class GenericExportTemplate implements ExportTemplate {

    static final String CODE = "GENERIC";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String name() {
        return "Generic monthly timesheet";
    }

    @Override
    public Client client() {
        return null;
    }
}
