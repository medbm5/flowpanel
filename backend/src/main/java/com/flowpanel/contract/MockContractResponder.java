package com.flowpanel.contract;

import com.flowpanel.ai.AiModelClient.ChatCall;
import com.flowpanel.ai.mock.MockResponder;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Mock profile answer for {@code contract.draft}: a French contract body filled from the (masked) facts. */
@Component
public class MockContractResponder implements MockResponder {

    @Override
    public List<String> features() {
        return List.of(ContractService.FEATURE);
    }

    @Override
    public String respond(ChatCall call, ToolRunner tools) {
        Map<String, Object> f = call.facts();
        String reason = switch (String.valueOf(f.get("legalReason"))) {
            case "REPLACEMENT" -> "remplacement de " + f.get("replacedEmployee") + ", salarié(e) absent(e)";
            case "SEASONAL" -> "emploi à caractère saisonnier";
            default -> "accroissement temporaire d'activité";
        };
        return """
                CONTRAT DE MISE À DISPOSITION %s

                1. Parties. %s (entreprise de travail temporaire) met à disposition de %s (entreprise utilisatrice) %s.
                2. Poste. %s, sur le site %s.
                3. Durée. Du %s au %s inclus.
                4. Horaires. %s, soit %s heures par semaine.
                5. Rémunération. Taux horaire brut de %s EUR, identique à celui d'un salarié permanent de même qualification.
                6. Motif de recours. %s.
                7. Habilitations. %s.
                8. Sécurité. Le salarié reçoit l'accueil sécurité du site avant sa prise de poste.
                """.formatted(f.get("ref"), f.get("supplier"), f.get("client"), f.get("worker"), f.get("position"),
                f.get("site"), f.get("startDate"), f.get("endDate"), f.get("schedule"), f.get("weeklyHours"),
                f.get("hourlyRate"), reason, certificates(f.get("certificates"))).strip();
    }

    private static String certificates(Object value) {
        if (value instanceof List<?> list && !list.isEmpty()) {
            return "Justificatifs joints : " + String.join(", ", list.stream().map(String::valueOf).toList());
        }
        return "Aucune habilitation particulière requise";
    }
}
