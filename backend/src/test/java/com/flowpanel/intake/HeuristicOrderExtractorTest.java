package com.flowpanel.intake;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class HeuristicOrderExtractorTest {

    /** Reads the template emails straight from the Flyway migration so the test follows the seed data. */
    static String template(String code) throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V3__missions.sql"), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("\\('" + code + "'.*?E'(.*?)'\\)[,;]", Pattern.DOTALL).matcher(sql);
        assertThat(m.find()).isTrue();
        return m.group(1).replace("\\n", "\n").replace("''", "'");
    }

    static Map<String, OrderExtraction.ExtractedField> extract(String email) {
        return HeuristicOrderExtractor.extract(email).asMap();
    }

    static String v(Map<String, OrderExtraction.ExtractedField> f, String name) {
        return f.get(name).value();
    }

    @Test
    void forkliftTemplateHasAHedgedEndDate() throws Exception {
        var f = extract(template("forklift-lille"));
        assertThat(v(f, "position")).isEqualTo("Cariste");
        assertThat(v(f, "quantity")).isEqualTo("2");
        assertThat(v(f, "site")).isEqualTo("Entrepôt Lille Lesquin");
        assertThat(v(f, "startDate")).isEqualTo("2026-10-05");
        assertThat(v(f, "endDate")).isEqualTo("2026-10-16");
        assertThat(v(f, "schedule")).isEqualTo("Lun–ven 06:00–13:00");
        assertThat(v(f, "weeklyHours")).isEqualTo("35");
        assertThat(v(f, "hourlyRate")).isEqualTo("13.20");
        assertThat(v(f, "legalReason")).isEqualTo("ACTIVITY_INCREASE");
        assertThat(v(f, "requiredCertifications")).isEqualTo("CACES R489 cat. 3");
        assertThat(v(f, "overtimeAllowed")).isEqualTo("false");
        assertThat(lowConfidence(f)).containsExactly("endDate");
    }

    @Test
    void pickersTemplateHasAHedgedRateAndOptionalCertification() throws Exception {
        var f = extract(template("pickers-roubaix"));
        assertThat(v(f, "position")).isEqualTo("Préparateur de commandes");
        assertThat(v(f, "quantity")).isEqualTo("3");
        assertThat(v(f, "site")).isEqualTo("Plateforme Roubaix");
        assertThat(v(f, "startDate")).isEqualTo("2026-10-12");
        assertThat(v(f, "endDate")).isEqualTo("2026-10-30");
        assertThat(v(f, "hourlyRate")).isEqualTo("12");
        assertThat(v(f, "requiredCertifications")).isEmpty();
        assertThat(lowConfidence(f)).containsExactly("hourlyRate");
    }

    @Test
    void officeTemplateIsAReplacementWithAHedgedStartDate() throws Exception {
        var f = extract(template("office-paris"));
        assertThat(v(f, "position")).isEqualTo("Assistante administrative");
        assertThat(v(f, "quantity")).isEqualTo("1");
        assertThat(v(f, "site")).isEqualTo("Siège Paris 9e");
        assertThat(v(f, "startDate")).isEqualTo("2026-10-19");
        assertThat(v(f, "endDate")).isEqualTo("2026-12-18");
        assertThat(v(f, "hourlyRate")).isEqualTo("15.50");
        assertThat(v(f, "legalReason")).isEqualTo("REPLACEMENT");
        assertThat(v(f, "replacedEmployee")).isEqualTo("Julie Perrin");
        assertThat(lowConfidence(f)).containsExactly("startDate");
    }

    @Test
    void maskedReplacedEmployeeTokenIsKept() {
        var f = extract("Nous devons remplacer [PERSON_1], assistante administrative, pendant son congé maladie.\n"
                + "Motif : remplacement d'un salarié absent.");
        assertThat(v(f, "replacedEmployee")).isEqualTo("[PERSON_1]");
    }

    @Test
    void missingValuesGetLowConfidenceInsteadOfInventedOnes() {
        var f = extract("Bonjour, pouvez-vous nous aider ?");
        assertThat(v(f, "startDate")).isEmpty();
        assertThat(f.get("startDate").confidence()).isLessThan(0.5);
        assertThat(v(f, "hourlyRate")).isEmpty();
    }

    static java.util.List<String> lowConfidence(Map<String, OrderExtraction.ExtractedField> f) {
        return f.entrySet().stream().filter(e -> e.getValue().confidence() < 0.75).map(Map.Entry::getKey).toList();
    }
}
