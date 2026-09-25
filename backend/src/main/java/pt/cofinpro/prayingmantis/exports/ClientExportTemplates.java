package pt.cofinpro.prayingmantis.exports;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pt.cofinpro.prayingmantis.users.Client;

/**
 * One export template per client (BE-8.3, decisions #19 and #33). The .xlsx files in
 * src/main/resources/export-templates/ are MOCKS, built by MockExportTemplateGenerator (test sources) from the
 * layouts below, until the clients send their real sheets. To swap in a real sheet: put the client's file in
 * place of the mock, adjust that client's layout to its cells, and delete the client from the generator.
 */
@Configuration
public class ClientExportTemplates {

    public static final DailyListLayout DKB = new DailyListLayout(
            "Leistungsnachweis", "Leistungsnachweis DKB (MUSTER)", "C3", "C4", 8, "A", "B", "C", "D", "E", "D39");

    public static final ProjectGridLayout DEKA = new ProjectGridLayout(
            "Stundennachweis", "Stundennachweis Deka (MUSTER)", "B3", "B4", 7, "A", "B", 6, "I");

    public static final DailyListLayout VV = new DailyListLayout(
            "Tätigkeitsnachweis", "Tätigkeitsnachweis VV (MUSTER)", "B4", "B5", 9, "A", "B", "D", "C", "E", "C40");

    public static final ProjectGridLayout DBIS = new ProjectGridLayout(
            "Stunden", "Stundenübersicht DBIS (MUSTER)", "C3", "C4", 6, "A", "B", 4, "G");

    public static final DailyListLayout UNION = new DailyListLayout(
            "Nachweis", "Leistungsnachweis Union Investment (MUSTER)", "C4", "C5", 8, "A", "B", "C", "D", "E", "D39");

    @Bean
    ExportTemplate dkbExportTemplate() {
        return new DailyListExportTemplate("DKB", "DKB Leistungsnachweis (mock)", Client.DKB, "/export-templates/dkb.xlsx", DKB);
    }

    @Bean
    ExportTemplate dekaExportTemplate() {
        return new ProjectGridExportTemplate("DEKA", "Deka Stundennachweis (mock)", Client.DEKA, "/export-templates/deka.xlsx", DEKA);
    }

    @Bean
    ExportTemplate vvExportTemplate() {
        return new DailyListExportTemplate("VV", "VV Tätigkeitsnachweis (mock)", Client.VV, "/export-templates/vv.xlsx", VV);
    }

    @Bean
    ExportTemplate dbisExportTemplate() {
        return new ProjectGridExportTemplate("DBIS", "DBIS Stundenübersicht (mock)", Client.DBIS, "/export-templates/dbis.xlsx", DBIS);
    }

    @Bean
    ExportTemplate unionExportTemplate() {
        return new DailyListExportTemplate("UNION", "Union Investment Leistungsnachweis (mock)", Client.UNION,
                "/export-templates/union.xlsx", UNION);
    }
}
