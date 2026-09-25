package pt.cofinpro.prayingmantis.exports;

import java.io.IOException;
import java.io.OutputStream;
import pt.cofinpro.prayingmantis.users.Client;

/**
 * One way to write a month as an Excel file (decisions #19 and #33): a Spring bean per template, found by
 * {@link ExportTemplateRegistry} (strategy pattern). Adding a template is one new class, with no API change.
 */
public interface ExportTemplate {

    /** Stable id, what the API takes as {@code template}, e.g. GENERIC. */
    String code();

    /** Shown in the export dialog. */
    String name();

    /** The client the template is for; null for the generic one. */
    Client client();

    /** Writes the month as an .xlsx workbook to {@code out}. Doesn't close {@code out}. */
    void write(MonthExport month, OutputStream out) throws IOException;
}
