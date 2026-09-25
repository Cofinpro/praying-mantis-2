package pt.cofinpro.prayingmantis.exports;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import pt.cofinpro.prayingmantis.common.InvalidFieldException;

/**
 * Every {@link ExportTemplate} bean in the context (BE-8.1). Spring injects them all as a list, so a new
 * template registers itself just by being a component. Two templates with the same code stop the app at start.
 */
@Component
public class ExportTemplateRegistry {

    private final List<ExportTemplate> byName;
    private final Map<String, ExportTemplate> byCode;

    public ExportTemplateRegistry(List<ExportTemplate> templates) {
        this.byName = templates.stream().sorted(Comparator.comparing(ExportTemplate::name, String.CASE_INSENSITIVE_ORDER)).toList();
        this.byCode = templates.stream().collect(Collectors.toMap(ExportTemplate::code, Function.identity()));
    }

    /** All templates, ordered by name (T-8.1). */
    public List<ExportTemplate> all() {
        return byName;
    }

    /** 400 on {@code template} for an unknown code (T-8.1). */
    public ExportTemplate get(String code) {
        ExportTemplate template = byCode.get(code);
        if (template == null) {
            throw new InvalidFieldException("template", "no such template: " + code);
        }
        return template;
    }
}
