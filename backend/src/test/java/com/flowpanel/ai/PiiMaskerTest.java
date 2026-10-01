package com.flowpanel.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PiiMaskerTest {

    private static final List<String> NAMES = List.of("Julie Perrin", "Paul Vasseur", "Claire Dubois", "Karim Haddad");

    @Test
    void masksEmailsPhonesAndKnownNames() {
        PiiMasker.Session s = PiiMasker.session(NAMES);
        String masked = s.mask("Remplacer Julie Perrin. Contact : Paul Vasseur — 06 98 76 54 32 — paul.vasseur@loginord.example");
        assertThat(masked)
                .doesNotContain("Julie", "Perrin", "Paul", "Vasseur", "06 98", "@loginord")
                .contains("[PERSON_1]", "[PERSON_2]", "[PHONE_1]", "[EMAIL_1]");
    }

    @Test
    void roundTripRestoresOriginals() {
        PiiMasker.Session s = PiiMasker.session(NAMES);
        String original = "Karim Haddad (karim.haddad@mail.example, +33 6 12 34 56 01) remplace Julie Perrin.";
        String masked = s.mask(original);
        assertThat(s.unmask(masked)).isEqualTo(original);
    }

    @Test
    void sameValueGetsSameTokenAcrossTexts() {
        PiiMasker.Session s = PiiMasker.session(NAMES);
        String a = s.mask("Julie Perrin est absente.");
        String b = s.mask("Le remplacement de Julie Perrin commence lundi.");
        assertThat(a).contains("[PERSON_1]");
        assertThat(b).contains("[PERSON_1]");
        assertThat(s.unmask("{\"replacedEmployee\":\"[PERSON_1]\"}")).isEqualTo("{\"replacedEmployee\":\"Julie Perrin\"}");
    }

    @Test
    void masksNameVariants() {
        PiiMasker.Session s = PiiMasker.session(NAMES);
        String masked = s.mask("Bonjour Claire, Mme Perrin et PERRIN Julie et Vasseur Paul.");
        assertThat(masked).doesNotContain("Claire", "Perrin", "PERRIN", "Vasseur");
        assertThat(s.unmask(masked)).contains("Claire Dubois", "Julie Perrin", "Paul Vasseur");
    }

    @Test
    void doesNotMaskPartialWordsOrUnknownText() {
        PiiMasker.Session s = PiiMasker.session(NAMES);
        String text = "Caristes CACES R489 cat. 3, entrepôt de Lesquin, 35 h par semaine, 13,20 € de l'heure.";
        assertThat(s.mask(text)).isEqualTo(text);
        assertThat(s.unmask("[PERSON_9] inconnu")).isEqualTo("[PERSON_9] inconnu");
    }

    @Test
    void masksNestedFacts() {
        PiiMasker.Session s = PiiMasker.session(NAMES);
        Map<String, Object> facts = s.maskFacts(Map.of("worker", "Karim Haddad", "rows", List.of(Map.of("name", "Julie Perrin")), "hours", 41));
        assertThat(facts.toString()).doesNotContain("Karim", "Julie").contains("41");
    }
}
