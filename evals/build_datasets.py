"""Writes the golden datasets (synthetic). Run once: python evals/build_datasets.py. The JSONL files are committed."""

import json
from pathlib import Path

HERE = Path(__file__).parent / "datasets"


def intake_cases():
    cases = [
        ("Nous avons besoin de 3 manutentionnaires sur l'entrepôt Douai, du lundi 2 novembre 2026 au vendredi 13 novembre 2026.\n"
         "Horaires : du lundi au vendredi, 7h00-14h00, soit 35 h par semaine. Pas d'heures supplémentaires prévues.\n"
         "Taux horaire : 12,30 € brut.\nMotif : accroissement temporaire d'activité.",
         dict(position="Manutentionnaire", quantity="3", site="Entrepôt Douai", startDate="2026-11-02", endDate="2026-11-13",
              weeklyHours="35", hourlyRate="12.30", legalReason="ACTIVITY_INCREASE", overtimeAllowed="false", requiredCertifications="")),
        ("Bonjour,\nIl nous faut 1 magasinier pour le dépôt Seclin à partir du lundi 9 novembre 2026 et jusqu'au vendredi 27 novembre 2026.\n"
         "Du lundi au vendredi 8h00-15h00 (35 h hebdo), sans heures supplémentaires.\nRémunération : 12,80 € de l'heure.\n"
         "Motif : surcroît d'activité.",
         dict(position="Magasinier", quantity="1", site="Dépôt Seclin", startDate="2026-11-09", endDate="2026-11-27",
              weeklyHours="35", hourlyRate="12.80", legalReason="ACTIVITY_INCREASE", overtimeAllowed="false", requiredCertifications="")),
        ("Objet : soudeurs\n\nNous recherchons 2 soudeurs MIG pour l'atelier de Valenciennes, du 16/11/2026 au 18/12/2026.\n"
         "Horaires : du lundi au vendredi, 6h00-14h00, soit 39 h par semaine. Heures supplémentaires possibles.\n"
         "Taux horaire : 15,20 €.\nMotif : accroissement temporaire d'activité (commande export).",
         dict(position="Soudeur MIG", quantity="2", site="Atelier de Valenciennes", startDate="2026-11-16", endDate="2026-12-18",
              weeklyHours="39", hourlyRate="15.20", legalReason="ACTIVITY_INCREASE", overtimeAllowed="true", requiredCertifications="")),
        ("Bonjour,\nNous avons besoin de deux caristes CACES R489 cat. 5 obligatoire sur la plateforme Lens, du lundi 23 novembre 2026 au vendredi 4 décembre 2026.\n"
         "Horaires : du lundi au vendredi, 5h00-12h00, soit 35 h par semaine. Pas d'heures supplémentaires.\n"
         "Taux horaire : 13,60 € brut.\nMotif : accroissement temporaire d'activité.",
         dict(position="Cariste", quantity="2", site="Plateforme Lens", startDate="2026-11-23", endDate="2026-12-04",
              weeklyHours="35", hourlyRate="13.60", legalReason="ACTIVITY_INCREASE", overtimeAllowed="false",
              requiredCertifications="CACES R489 cat. 5")),
        ("Objet : remplacement comptable\n\nNous devons remplacer Marc Dupont, assistant comptable, pendant son arrêt maladie.\n"
         "Poste au siège Lille, du lundi 2 novembre 2026 au vendredi 18 décembre 2026.\n"
         "Horaires : du lundi au vendredi 9h00-17h00, 35 h par semaine, sans heures supplémentaires.\nTaux horaire : 16,40 €.\n"
         "Motif : remplacement d'un salarié absent.",
         dict(position="Assistant comptable", quantity="1", site="Siège Lille", startDate="2026-11-02", endDate="2026-12-18",
              weeklyHours="35", hourlyRate="16.40", legalReason="REPLACEMENT", overtimeAllowed="false", requiredCertifications="",
              replacedEmployee="Marc Dupont")),
        ("Bonjour,\nNous cherchons 4 préparateurs de commandes pour la plateforme Roubaix, du lundi 30 novembre 2026 au jeudi 24 décembre 2026.\n"
         "Horaires : du lundi au samedi 6h00-12h00 (36 h hebdo). Heures supplémentaires possibles.\nTaux horaire : 12,10 €.\n"
         "Motif : emploi saisonnier (pic de Noël).",
         dict(position="Préparateur de commandes", quantity="4", site="Plateforme Roubaix", startDate="2026-11-30", endDate="2026-12-24",
              weeklyHours="36", hourlyRate="12.10", legalReason="SEASONAL", overtimeAllowed="true", requiredCertifications="")),
        ("Objet : agents de quai\n\nNous avons besoin de 5 agents de quai sur l'entrepôt Lille Lesquin, du lundi 7 décembre 2026 au vendredi 18 décembre 2026.\n"
         "Horaires : du lundi au vendredi, 4h00-11h00, soit 35 h par semaine. Pas d'heures supplémentaires.\nTaux horaire : 12,90 € brut.\n"
         "SST souhaité.\nMotif : accroissement temporaire d'activité.",
         dict(position="Agent de quai", quantity="5", site="Entrepôt Lille Lesquin", startDate="2026-12-07", endDate="2026-12-18",
              weeklyHours="35", hourlyRate="12.90", legalReason="ACTIVITY_INCREASE", overtimeAllowed="false", requiredCertifications="")),
        ("Bonjour,\nIl nous faut 2 techniciens de maintenance habilitation électrique B1V exigée pour l'usine de Douai, du lundi 16 novembre 2026 au vendredi 11 décembre 2026.\n"
         "Horaires : du lundi au vendredi, 7h30-15h30, soit 37,5 h par semaine. Heures supplémentaires possibles.\nTaux horaire : 18,50 €.\n"
         "Motif : accroissement temporaire d'activité.",
         dict(position="Technicien de maintenance", quantity="2", site="Usine de Douai", startDate="2026-11-16", endDate="2026-12-11",
              weeklyHours="37.5", hourlyRate="18.50", legalReason="ACTIVITY_INCREASE", overtimeAllowed="true",
              requiredCertifications="Habilitation électrique B1V")),
        ("Objet : hôtesse d'accueil\n\nNous recherchons 1 hôte d'accueil pour le siège Paris 9e, du lundi 4 janvier 2027 au vendredi 26 février 2027.\n"
         "Horaires : du lundi au vendredi 8h30-16h30, 35 h par semaine, sans heures supplémentaires.\nTaux horaire : 14,20 €.\n"
         "Motif : remplacement de Sophie Lenoir, en congé parental.",
         dict(position="Hôte d'accueil", quantity="1", site="Siège Paris 9e", startDate="2027-01-04", endDate="2027-02-26",
              weeklyHours="35", hourlyRate="14.20", legalReason="REPLACEMENT", overtimeAllowed="false", requiredCertifications="",
              replacedEmployee="Sophie Lenoir")),
        ("Bonjour,\nNous avons besoin de 3 opérateurs de production du lundi 2 novembre 2026 au vendredi 27 novembre 2026 sur le site Arras.\n"
         "Horaires : du lundi au vendredi, 13h00-20h00, soit 35 h par semaine. Pas d'heures supplémentaires.\nTaux horaire : 13,10 €.\n"
         "Motif : accroissement temporaire d'activité.",
         dict(position="Opérateur de production", quantity="3", site="Site Arras", startDate="2026-11-02", endDate="2026-11-27",
              weeklyHours="35", hourlyRate="13.10", legalReason="ACTIVITY_INCREASE", overtimeAllowed="false", requiredCertifications="")),
        ("Objet : renfort inventaire\n\nNous cherchons 6 inventoristes pour le magasin Tourcoing, du jeudi 31 décembre 2026 au samedi 2 janvier 2027.\n"
         "Horaires : du jeudi au samedi 6h00-14h00, 24 h par semaine. Pas d'heures supplémentaires.\nTaux horaire : 12,00 €.\n"
         "Motif : accroissement temporaire d'activité (inventaire annuel).",
         dict(position="Inventoriste", quantity="6", site="Magasin Tourcoing", startDate="2026-12-31", endDate="2027-01-02",
              weeklyHours="24", hourlyRate="12", legalReason="ACTIVITY_INCREASE", overtimeAllowed="false", requiredCertifications="")),
        ("Bonjour,\nNous avons besoin de 2 conducteurs de ligne sur l'usine Valenciennes, du lundi 9 novembre 2026 au vendredi 4 décembre 2026.\n"
         "Horaires : du lundi au vendredi, 5h00-12h00, soit 35 h par semaine. Pas d'heures supplémentaires prévues.\nTaux horaire : 14,60 € brut.\n"
         "Motif : accroissement temporaire d'activité.",
         dict(position="Conducteur de ligne", quantity="2", site="Usine Valenciennes", startDate="2026-11-09", endDate="2026-12-04",
              weeklyHours="35", hourlyRate="14.60", legalReason="ACTIVITY_INCREASE", overtimeAllowed="false", requiredCertifications="")),
        ("Objet : chauffeur-livreur\n\nIl nous faut 1 chauffeur-livreur pour l'agence Villeneuve-d'Ascq, du lundi 14 décembre 2026 au jeudi 31 décembre 2026.\n"
         "Horaires : du lundi au vendredi, 7h00-14h00, soit 35 h par semaine. Heures supplémentaires possibles.\nTaux horaire : 13,40 €.\n"
         "Permis C obligatoire.\nMotif : emploi saisonnier.",
         dict(position="Chauffeur-livreur", quantity="1", site="Agence Villeneuve-d'Ascq", startDate="2026-12-14", endDate="2026-12-31",
              weeklyHours="35", hourlyRate="13.40", legalReason="SEASONAL", overtimeAllowed="true", requiredCertifications="")),
        ("Bonjour,\nNous recherchons 2 caristes CACES R489 cat. 1 pour le dépôt Croix, du 07/12/2026 au 22/12/2026.\n"
         "Horaires : du lundi au vendredi 14h00-21h00 (35 h hebdo), pas d'heures supplémentaires.\nTaux horaire : 12,70 €.\n"
         "Motif : accroissement temporaire d'activité.",
         dict(position="Cariste", quantity="2", site="Dépôt Croix", startDate="2026-12-07", endDate="2026-12-22",
              weeklyHours="35", hourlyRate="12.70", legalReason="ACTIVITY_INCREASE", overtimeAllowed="false",
              requiredCertifications="CACES R489 cat. 1")),
        ("Objet : renfort logistique\n\nNous avons besoin de 8 préparateurs de commandes sur la plateforme Wattrelos, du lundi 16 novembre 2026 au vendredi 11 décembre 2026.\n"
         "Horaires : du lundi au vendredi, 6h00-13h00, soit 35 h par semaine. Pas d'heures supplémentaires.\nTaux horaire : 12,20 € brut.\n"
         "Motif : accroissement temporaire d'activité.",
         dict(position="Préparateur de commandes", quantity="8", site="Plateforme Wattrelos", startDate="2026-11-16", endDate="2026-12-11",
              weeklyHours="35", hourlyRate="12.20", legalReason="ACTIVITY_INCREASE", overtimeAllowed="false", requiredCertifications="")),
        ("Bonjour,\nNous cherchons 1 agent d'entretien pour les bureaux Saint-Denis, du lundi 2 novembre 2026 au vendredi 20 novembre 2026.\n"
         "Horaires : du lundi au vendredi, 6h00-10h00, 20 h par semaine. Pas d'heures supplémentaires.\nTaux horaire : 12,00 €.\n"
         "Motif : remplacement de Karine Bastien, en congé maladie.",
         dict(position="Agent d'entretien", quantity="1", site="Bureaux Saint-Denis", startDate="2026-11-02", endDate="2026-11-20",
              weeklyHours="20", hourlyRate="12", legalReason="REPLACEMENT", overtimeAllowed="false", requiredCertifications="",
              replacedEmployee="Karine Bastien")),
        ("Objet : usineurs\n\nNous avons besoin de 2 usineurs CN pour l'atelier d'usinage de Valenciennes, du lundi 30 novembre 2026 au vendredi 15 janvier 2027.\n"
         "Horaires : du lundi au vendredi, 8h00-16h00, soit 39 h par semaine. Heures supplémentaires possibles.\nTaux horaire : 16,90 €.\n"
         "Motif : accroissement temporaire d'activité.",
         dict(position="Usineur CN", quantity="2", site="Atelier d'usinage de Valenciennes", startDate="2026-11-30", endDate="2027-01-15",
              weeklyHours="39", hourlyRate="16.90", legalReason="ACTIVITY_INCREASE", overtimeAllowed="true", requiredCertifications="")),
        ("Bonjour,\nIl nous faut 3 emballeurs sur l'entrepôt Arras, du lundi 7 décembre 2026 au mercredi 23 décembre 2026.\n"
         "Horaires : du lundi au vendredi, 13h00-20h00, soit 35 h par semaine. Pas d'heures supplémentaires.\nTaux horaire : 12,15 € brut.\n"
         "Motif : emploi saisonnier.",
         dict(position="Emballeur", quantity="3", site="Entrepôt Arras", startDate="2026-12-07", endDate="2026-12-23",
              weeklyHours="35", hourlyRate="12.15", legalReason="SEASONAL", overtimeAllowed="false", requiredCertifications="")),
        ("Objet : contrôleurs qualité\n\nNous recherchons 2 contrôleurs qualité pour l'usine de Lens, du lundi 4 janvier 2027 au vendredi 29 janvier 2027.\n"
         "Horaires : du lundi au vendredi, 6h00-13h00, soit 35 h par semaine. Pas d'heures supplémentaires.\nTaux horaire : 14,80 €.\n"
         "Motif : accroissement temporaire d'activité.",
         dict(position="Contrôleur qualité", quantity="2", site="Usine de Lens", startDate="2027-01-04", endDate="2027-01-29",
              weeklyHours="35", hourlyRate="14.80", legalReason="ACTIVITY_INCREASE", overtimeAllowed="false", requiredCertifications="")),
        ("Bonjour,\nNous avons besoin de 1 standardiste pour le siège Boulogne-Billancourt, du lundi 16 novembre 2026 au vendredi 18 décembre 2026.\n"
         "Horaires : du lundi au vendredi 9h00-17h00, 35 h par semaine, sans heures supplémentaires.\nTaux horaire : 14,00 €.\n"
         "Motif : remplacement de Paul Garnier, en congé maladie.",
         dict(position="Standardiste", quantity="1", site="Siège Boulogne-Billancourt", startDate="2026-11-16", endDate="2026-12-18",
              weeklyHours="35", hourlyRate="14", legalReason="REPLACEMENT", overtimeAllowed="false", requiredCertifications="",
              replacedEmployee="Paul Garnier")),
        ("Objet : caristes nuit\n\nNous cherchons 2 caristes CACES R489 cat. 3 pour l'entrepôt Seclin, du lundi 11 janvier 2027 au vendredi 5 février 2027.\n"
         "Horaires : du lundi au vendredi, 21h00-4h00, soit 35 h par semaine. Pas d'heures supplémentaires.\nTaux horaire : 13,90 € brut.\n"
         "Motif : accroissement temporaire d'activité.",
         dict(position="Cariste", quantity="2", site="Entrepôt Seclin", startDate="2027-01-11", endDate="2027-02-05",
              weeklyHours="35", hourlyRate="13.90", legalReason="ACTIVITY_INCREASE", overtimeAllowed="false",
              requiredCertifications="CACES R489 cat. 3")),
    ]
    return [{"id": f"intake-{i + 1:02d}", "email": e, "expected": x} for i, (e, x) in enumerate(cases)]


def money(v):
    s = f"{v:,.2f}".replace(",", " ").replace(".", ",")
    return s


def invoice_cases():
    specs = [
        ("INV-INTERSUD-0201", "InterSud Intérim", [("Karim Haddad", 105, 13.20), ("Julien Marchand", 70, 13.20)]),
        ("INV-PROXI-0202", "Proxi Staffing", [("Lucas Petit", 108, 13.20)]),
        ("INV-ATLAS-0203", "Atlas RH", [("Yanis Leroy", 35, 13.20), ("Camille Dupuis", 35, 12.10)]),
        ("INV-INTERSUD-0204", "InterSud Intérim", [("Sofia Ramos", 140, 12.10), ("Mehdi Bensaïd", 136.5, 12.10)]),
        ("INV-PROXI-0205", "Proxi Staffing", [("Inès Moreau", 37.5, 12.15)]),
        ("INV-ATLAS-0206", "Atlas RH", [("Léa Girard", 315, 15.50)]),
        ("INV-PROXI-0207", "Proxi Staffing", [("Hugo Garnier", 70, 15.50), ("Nicolas Roussel", 35, 12.10), ("Emma Lambert", 41, 13.20)]),
        ("INV-INTERSUD-0208", "InterSud Intérim", [("Claire Fontaine", 280, 15.50)]),
        ("INV-ATLAS-0209", "Atlas RH", [("Thomas Henry", 72.25, 12.40)]),
        ("INV-PROXI-0210", "Proxi Staffing", [("Lucas Petit", 35, 13.90), ("Inès Moreau", 35, 13.90)]),
        ("INV-ATLAS-0211", "Atlas RH", [("Antoine Mercier", 24, 12.00)]),
        ("INV-INTERSUD-0212", "InterSud Intérim", [("Karim Haddad", 39, 15.20), ("Julien Marchand", 39, 15.20)]),
        ("INV-ATLAS-0213", "Atlas RH", [("Yanis Leroy", 210, 13.60)]),
        ("INV-PROXI-0214", "Proxi Staffing", [("Emma Lambert", 17.5, 12.90)]),
        ("INV-INTERSUD-0215", "InterSud Intérim", [("Sofia Ramos", 105, 12.20), ("Mehdi Bensaïd", 98, 12.20), ("Karim Haddad", 35, 12.20)]),
        ("INV-ATLAS-0216", "Atlas RH", [("Léa Girard", 66.5, 14.80)]),
    ]
    cases = []
    for ref, supplier, lines in specs:
        rows = []
        exp_lines = []
        total = 0.0
        for name, h, r in lines:
            amount = round(h * r + 1e-9, 2)
            total += amount
            rows.append(f"{name} | {money(h)} h | {money(r)} €/h | {money(amount)} €")
            exp_lines.append({"workerName": name, "hours": h, "hourlyRate": r, "amount": amount})
        total = round(total, 2)
        vat = round(total * 0.2, 2)
        text = "\n".join([f"FACTURE N° {ref}", f"Fournisseur : {supplier}", "Client : LogiNord", "Mission : ORD-2026-0199",
                          "Intérimaire | Heures | Taux horaire | Montant HT", *rows,
                          f"Total HT : {money(total)} €", f"TVA 20 % : {money(vat)} €", f"Total TTC : {money(total + vat)} €"])
        cases.append({"id": ref.lower(), "text": text, "expected": {"invoiceNumber": ref, "lines": exp_lines, "totalExclTax": total}})
    return cases


def rag_cases():
    L = "claire"
    M = "marc"
    rows = [
        (L, "What is the night work bonus?", "Night work policy — LogiNord", ["25 %"]),
        (M, "What is the night work bonus?", "Night work policy — MétalPro", ["40 %"]),
        (L, "Which hours count as night work?", "Night work policy — LogiNord", ["21:00", "06:00"]),
        (M, "Which hours count as night work at the plant?", "Night work policy — MétalPro", ["22:00", "05:00"]),
        (L, "What rest period is required between two night shifts?", "Night work policy — LogiNord", ["11 hours"]),
        (L, "How long is the safety induction?", "Site safety rules — LogiNord warehouses", ["45-minute"]),
        (M, "How long is the safety induction?", "Plant safety rules — MétalPro", ["90-minute"]),
        (L, "What is the maximum forklift speed inside the warehouse?", "Site safety rules — LogiNord warehouses", ["10 km/h"]),
        (L, "Which certificate is needed to drive a counterbalanced forklift?", "Site safety rules — LogiNord warehouses", ["CACES R489"]),
        (L, "Who provides the safety shoes?", "Site safety rules — LogiNord warehouses", ["supplier provides the safety shoes"]),
        (L, "How fast must suppliers send their first candidate proposals?", "Supplier panel agreement — LogiNord", ["24 hours"]),
        (M, "How fast must suppliers send their first candidate proposals?", "Supplier panel agreement — MétalPro", ["48 hours"]),
        (L, "What is the cap on the supplier markup coefficient?", "Supplier panel agreement — LogiNord", ["1.95"]),
        (M, "What is the cap on the supplier markup coefficient?", "Supplier panel agreement — MétalPro", ["2.10"]),
        (L, "Within how many days are invoices paid?", "Supplier panel agreement — LogiNord", ["30 days"]),
        (L, "When can a supplier be removed from the panel?", "Supplier panel agreement — LogiNord", ["20 %"]),
        (L, "When must suppliers submit weekly timesheets?", "Timesheet and overtime policy — LogiNord", ["Monday before 12:00"]),
        (L, "At what rate are the first overtime hours paid?", "Timesheet and overtime policy — LogiNord", ["125 %"]),
        (L, "What is the maximum number of hours in a week?", "Timesheet and overtime policy — LogiNord", ["48 hours"]),
        (M, "Which protective equipment is mandatory in the machining workshop?", "Plant safety rules — MétalPro", ["safety glasses"]),
        (L, "What is on the canteen menu on Friday?", None, []),
        (M, "What is the parking policy for visitors on Sundays?", None, []),
    ]
    return [{"id": f"rag-{i + 1:02d}", "persona": p, "question": q, "expected_document": d, "key_facts": f,
             "expect_not_found": d is None} for i, (p, q, d, f) in enumerate(rows)]


def tool_cases():
    rows = [
        ("claire", "How many missions do I have?", "listMissions", "2 mission"),
        ("claire", "List my missions in progress", "listMissions", "{ref:142}"),
        ("claire", "Which missions need review?", "listMissions", "mission"),
        ("marc", "How many missions do I have?", "listMissions", "1 mission"),
        ("claire", "What is the status of {ref:142}?", "getMissionStatus", "TIMESHEETS"),
        ("claire", "Where is {ref:147} at?", "getMissionStatus", "SOURCING"),
        ("marc", "What is the status of {ref:142}?", "getMissionStatus", "can't find"),
        ("claire", "What is our spend by supplier?", "getSpendBySupplier", "13.2"),
        ("thomas", "What are InterSud Intérim's hourly rates?", "getSpendBySupplier", "Proxi Staffing"),
        ("nadia", "Show me the spend and rates of every supplier", "getSpendBySupplier", "InterSud Intérim"),
        ("claire", "How many open timesheet anomalies are there?", "listOpenAnomalies", "0 open"),
        ("nadia", "Do I have open anomalies on my timesheets?", "listOpenAnomalies", "0 open"),
        ("claire", "Which contracts end before 2026-10-31?", "listContractsEndingBefore", "2 contract"),
        ("claire", "Which contracts end before 2026-09-30?", "listContractsEndingBefore", "0 contracts"),
        ("thomas", "Which of my contracts end before 2026-10-31?", "listContractsEndingBefore", "1 contract"),
        ("nadia", "List the orders sent to me", "listMissions", "2 mission"),
    ]
    return [{"id": f"tools-{i + 1:02d}", "persona": p, "question": q, "expected_tool": t, "expected_value": v}
            for i, (p, q, t, v) in enumerate(rows)]


def write(name, rows):
    with open(HERE / name, "w", encoding="utf-8", newline="\n") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")


if __name__ == "__main__":
    HERE.mkdir(exist_ok=True)
    write("intake.jsonl", intake_cases())
    write("invoice.jsonl", invoice_cases())
    write("copilot_rag.jsonl", rag_cases())
    write("copilot_tools.jsonl", tool_cases())
    print("datasets written")
