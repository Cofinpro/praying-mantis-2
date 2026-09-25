package pt.cofinpro.prayingmantis.exports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import pt.cofinpro.prayingmantis.TestcontainersConfiguration;
import pt.cofinpro.prayingmantis.common.InvalidFieldException;
import pt.cofinpro.prayingmantis.users.Client;

/** GET /api/export-templates and the template registry (BE-8.1). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ExportTemplatesIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ExportTemplateRegistry registry;

    @Test
    @WithUserDetails("eva.santos@cofinpro.pt")
    void theGenericTemplateIsListed() throws Exception {
        mockMvc.perform(get("/api/export-templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].code").value("GENERIC"))
                .andExpect(jsonPath("$[0].name").value("Generic monthly timesheet"))
                .andExpect(jsonPath("$[0].client").doesNotExist());
    }

    @Test
    void templatesNeedALogin() throws Exception {
        mockMvc.perform(get("/api/export-templates")).andExpect(status().isUnauthorized());
    }

    @Test
    void anUnknownCodeIsAFieldErrorOnTemplate() {
        assertThat(registry.get("GENERIC").code()).isEqualTo("GENERIC");
        assertThatThrownBy(() -> registry.get("DKB"))
                .isInstanceOfSatisfying(InvalidFieldException.class, e -> assertThat(e.getField()).isEqualTo("template"));
    }

    @Test
    void aNewTemplateIsJustAnotherBeanAndTheListIsByName() {
        ExportTemplate dkb = new ExportTemplate() {
            public String code() {
                return "DKB";
            }

            public String name() {
                return "DKB monthly report";
            }

            public Client client() {
                return Client.DKB;
            }
        };

        ExportTemplateRegistry withDkb = new ExportTemplateRegistry(List.of(registry.get("GENERIC"), dkb));

        assertThat(withDkb.all()).extracting(ExportTemplate::code).containsExactly("DKB", "GENERIC");
        assertThat(withDkb.get("DKB").client()).isEqualTo(Client.DKB);
    }

    @Test
    void twoTemplatesWithOneCodeAreAStartupError() {
        ExportTemplate generic = registry.get("GENERIC");

        assertThatThrownBy(() -> new ExportTemplateRegistry(List.of(generic, generic))).isInstanceOf(IllegalStateException.class);
    }
}
