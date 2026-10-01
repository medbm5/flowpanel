package com.flowpanel.demo;

/** Synthetic emails for the pre-advanced demo missions. */
final class DemoData {

    private DemoData() {
    }

    static final long LOGINORD = 1;
    static final long METALPRO = 2;
    static final long CLAIRE = 1;
    static final long MARC = 2;

    static final String EMAIL_0142 = """
            Objet : 2 caristes CACES 3 pour Lesquin — inventaire de fin septembre

            Bonjour Claire,

            Nous avons besoin de 2 caristes CACES R489 cat. 3 sur l'entrepôt Lille Lesquin, du lundi 21 septembre 2026 au vendredi 9 octobre 2026.
            Horaires : du lundi au vendredi, 6h00-13h00, soit 35 h par semaine. Pas d'heures supplémentaires prévues.
            Taux horaire : 13,20 € brut.
            Motif : accroissement temporaire d'activité (inventaire annuel).

            Merci,
            Paul Vasseur
            Responsable de site — 06 98 76 54 32 — paul.vasseur@loginord.example""";

    static final String EMAIL_0145 = """
            Objet : Opérateurs de production — usine de Valenciennes

            Bonjour Marc,

            L'atelier d'usinage de Valenciennes a besoin de 2 opérateurs de production du lundi 26 octobre 2026 au vendredi 20 novembre 2026.
            Horaires : du lundi au vendredi, 8h00-15h00, soit 35 h par semaine. Pas d'heures supplémentaires.
            Taux horaire : 14,10 €.
            Motif : accroissement temporaire d'activité (commande export).

            Cordialement,
            Isabelle Caron
            Responsable de production — isabelle.caron@metalpro.example""";
}
