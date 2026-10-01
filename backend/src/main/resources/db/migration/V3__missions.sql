-- Worker pool, owned by suppliers (synthetic people).
CREATE TABLE worker (
    id               BIGSERIAL PRIMARY KEY,
    supplier_id      BIGINT        NOT NULL REFERENCES supplier (id),
    first_name       TEXT          NOT NULL,
    last_name        TEXT          NOT NULL,
    email            TEXT          NOT NULL,
    phone            TEXT          NOT NULL,
    city             TEXT          NOT NULL,
    latitude         NUMERIC(8, 5) NOT NULL,
    longitude        NUMERIC(8, 5) NOT NULL,
    skills           TEXT[]        NOT NULL DEFAULT '{}',
    certifications   TEXT[]        NOT NULL DEFAULT '{}',
    experience_years INT           NOT NULL DEFAULT 0,
    available_from   DATE          NOT NULL,
    available_to     DATE,
    profile          TEXT          NOT NULL
);
CREATE INDEX worker_supplier_idx ON worker (supplier_id);

CREATE TABLE request_template (
    code        TEXT PRIMARY KEY,
    title       TEXT NOT NULL,
    site        TEXT NOT NULL,
    description TEXT NOT NULL,
    email_text  TEXT NOT NULL
);

CREATE SEQUENCE mission_number_seq START WITH 150;

CREATE TABLE mission (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT      NOT NULL REFERENCES tenant (id),
    number        INT         NOT NULL UNIQUE,
    ref           TEXT        NOT NULL UNIQUE,
    title         TEXT        NOT NULL,
    site          TEXT,
    phase         TEXT        NOT NULL CHECK (phase IN ('INTAKE', 'SOURCING', 'CONTRACTS', 'TIMESHEETS', 'INVOICE', 'CLOSED')),
    template_code TEXT REFERENCES request_template (code),
    source_email  TEXT        NOT NULL,
    summary       JSONB,
    created_by    BIGINT REFERENCES app_user (id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at     TIMESTAMPTZ
);
CREATE INDEX mission_tenant_idx ON mission (tenant_id, created_at DESC);

CREATE TABLE phase_completion (
    id           BIGSERIAL PRIMARY KEY,
    mission_id   BIGINT      NOT NULL REFERENCES mission (id) ON DELETE CASCADE,
    phase        TEXT        NOT NULL,
    finalized_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    finalized_by BIGINT REFERENCES app_user (id),
    UNIQUE (mission_id, phase)
);

CREATE TABLE artifact (
    id         BIGSERIAL PRIMARY KEY,
    mission_id BIGINT      NOT NULL REFERENCES mission (id) ON DELETE CASCADE,
    phase      TEXT        NOT NULL,
    type       TEXT        NOT NULL,
    ref        TEXT        NOT NULL,
    status     TEXT        NOT NULL,
    payload    JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (mission_id, type, ref)
);

-- 15 synthetic workers across the three suppliers.
INSERT INTO worker (id, supplier_id, first_name, last_name, email, phone, city, latitude, longitude, skills, certifications,
                    experience_years, available_from, available_to, profile) VALUES
 (1, 1, 'Karim', 'Haddad', 'karim.haddad@mail.example', '06 12 34 56 01', 'Lesquin', 50.58900, 3.11500,
  '{cariste,chargement,gestion de stock}', '{CACES R489 cat. 3,CACES R489 cat. 1,SST}', 7, '2026-09-01', '2026-12-31',
  'Cariste confirmé, CACES R489 catégories 1 et 3, chargement et déchargement de camions, inventaires en entrepôt logistique.'),
 (2, 1, 'Julien', 'Marchand', 'julien.marchand@mail.example', '06 12 34 56 02', 'Villeneuve-d''Ascq', 50.62300, 3.14500,
  '{cariste,préparation de commandes}', '{CACES R489 cat. 3,CACES R489 cat. 1}', 4, '2026-09-01', '2026-12-31',
  'Cariste en entrepôt e-commerce, CACES R489 cat. 1 et 3, préparation de commandes et réception de marchandises.'),
 (3, 1, 'Sofia', 'Ramos', 'sofia.ramos@mail.example', '06 12 34 56 03', 'Roubaix', 50.69000, 3.18100,
  '{préparation de commandes,emballage,scanner}', '{CACES R489 cat. 1}', 3, '2026-09-01', '2026-12-31',
  'Préparatrice de commandes, picking au scanner, emballage et contrôle qualité, CACES R489 cat. 1 (transpalette).'),
 (4, 1, 'Mehdi', 'Bensaïd', 'mehdi.bensaid@mail.example', '06 12 34 56 04', 'Tourcoing', 50.72400, 3.16100,
  '{préparation de commandes,cariste}', '{CACES R489 cat. 1}', 2, '2026-10-01', '2026-12-31',
  'Préparateur de commandes en entrepôt frais, picking vocal, transpalette électrique.'),
 (5, 1, 'Claire', 'Fontaine', 'claire.fontaine@mail.example', '06 12 34 56 05', 'Paris', 48.85700, 2.35200,
  '{assistanat,accueil,Pack Office}', '{}', 6, '2026-09-15', '2026-12-31',
  'Assistante administrative polyvalente, accueil, gestion d''agendas, Excel et Word avancés, facturation.'),
 (6, 2, 'Lucas', 'Petit', 'lucas.petit@mail.example', '06 22 34 56 06', 'Lille', 50.62900, 3.05700,
  '{cariste,gestion de stock}', '{CACES R489 cat. 3,SST}', 9, '2026-09-01', '2026-12-31',
  'Cariste expérimenté, CACES R489 cat. 3, gestion de stock et inventaires tournants, sauveteur secouriste du travail.'),
 (7, 2, 'Emma', 'Lambert', 'emma.lambert@mail.example', '06 22 34 56 07', 'Lens', 50.43200, 2.83300,
  '{cariste,chargement}', '{CACES R489 cat. 3}', 5, '2026-09-01', '2026-10-09',
  'Cariste, CACES R489 cat. 3, chargement de quais, disponible jusqu''au 9 octobre.'),
 (8, 2, 'Nicolas', 'Roussel', 'nicolas.roussel@mail.example', '06 22 34 56 08', 'Roubaix', 50.69400, 3.17400,
  '{préparation de commandes,emballage}', '{}', 1, '2026-09-01', '2026-12-31',
  'Préparateur de commandes débutant, emballage et étiquetage, très motivé.'),
 (9, 2, 'Inès', 'Moreau', 'ines.moreau@mail.example', '06 22 34 56 09', 'Wattrelos', 50.70100, 3.21600,
  '{préparation de commandes,scanner,gestion de stock}', '{CACES R489 cat. 1}', 5, '2026-09-01', '2026-12-31',
  'Préparatrice de commandes expérimentée, picking au scanner, réapprovisionnement, CACES R489 cat. 1.'),
 (10, 2, 'Hugo', 'Garnier', 'hugo.garnier@mail.example', '06 22 34 56 10', 'Saint-Denis', 48.93600, 2.35700,
  '{assistanat,Pack Office,standard téléphonique}', '{}', 3, '2026-09-01', '2026-12-31',
  'Assistant administratif, standard téléphonique, saisie de commandes, maîtrise du Pack Office.'),
 (11, 3, 'Yanis', 'Leroy', 'yanis.leroy@mail.example', '06 32 34 56 11', 'Seclin', 50.54800, 3.03000,
  '{cariste,chargement,gestion de stock}', '{CACES R489 cat. 3,CACES R489 cat. 5}', 6, '2026-09-01', '2026-12-31',
  'Cariste multi-catégories (R489 cat. 3 et 5), chargement de semi-remorques, gestion des emplacements.'),
 (12, 3, 'Camille', 'Dupuis', 'camille.dupuis@mail.example', '06 32 34 56 12', 'Douai', 50.37000, 3.08000,
  '{cariste}', '{CACES R489 cat. 1}', 2, '2026-09-01', '2026-12-31',
  'Cariste junior, CACES R489 cat. 1 uniquement, souhaite évoluer vers la catégorie 3.'),
 (13, 3, 'Thomas', 'Henry', 'thomas.henry@mail.example', '06 32 34 56 13', 'Croix', 50.67800, 3.15000,
  '{préparation de commandes,emballage,cariste}', '{CACES R489 cat. 1,SST}', 4, '2026-09-01', '2026-12-31',
  'Préparateur de commandes et cariste occasionnel, transpalette et gerbeur, SST.'),
 (14, 3, 'Léa', 'Girard', 'lea.girard@mail.example', '06 32 34 56 14', 'Boulogne-Billancourt', 48.83500, 2.24100,
  '{assistanat,accueil,Pack Office,comptabilité}', '{}', 8, '2026-09-01', '2026-12-31',
  'Assistante de direction, accueil, comptabilité fournisseurs, Pack Office, anglais courant.'),
 (15, 3, 'Antoine', 'Mercier', 'antoine.mercier@mail.example', '06 32 34 56 15', 'Arras', 50.29100, 2.77700,
  '{préparation de commandes,cariste}', '{CACES R489 cat. 1}', 3, '2026-11-02', '2026-12-31',
  'Préparateur de commandes, disponible à partir du 2 novembre.');
SELECT setval('worker_id_seq', 100);

INSERT INTO request_template (code, title, site, description, email_text) VALUES
 ('forklift-lille', 'Caristes CACES 3 — Lille Lesquin', 'Entrepôt Lille Lesquin',
  '2 forklift operators (CACES R489 cat. 3) for a two-week order peak in Lille Lesquin.',
  E'Objet : Besoin de 2 caristes pour Lesquin\n\nBonjour Claire,\n\nPour absorber le pic de commandes de début octobre, nous avons besoin de 2 caristes CACES R489 cat. 3 sur l''entrepôt Lille Lesquin.\nMission du lundi 5 octobre 2026 jusqu''au vendredi 16 octobre 2026 normalement (à confirmer selon les volumes).\nHoraires : du lundi au vendredi, 6h00-13h00, soit 35 h par semaine. Pas d''heures supplémentaires prévues.\nTaux horaire : 13,20 € brut.\nMotif : accroissement temporaire d''activité.\n\nMerci,\nPaul Vasseur\nResponsable de site — 06 98 76 54 32 — paul.vasseur@loginord.example'),
 ('pickers-roubaix', 'Préparateurs de commandes — Roubaix', 'Plateforme Roubaix',
  '3 order pickers for the Roubaix e-commerce platform, afternoon shift.',
  E'Objet : Renfort préparation de commandes Roubaix\n\nBonjour,\n\nNous cherchons 3 préparateurs de commandes pour la plateforme Roubaix, du lundi 12 octobre 2026 au vendredi 30 octobre 2026.\nPoste en après-midi, du lundi au vendredi 13h00-20h00 (35 h hebdo), heures supplémentaires non prévues.\nCACES R489 cat. 1 apprécié mais pas obligatoire.\nRémunération autour de 12 € de l''heure, comme la dernière fois.\nMotif : accroissement temporaire d''activité lié aux promotions d''automne.\n\nCordialement,\nSarah Lemaire\nChef d''équipe logistique — sarah.lemaire@loginord.example'),
 ('office-paris', 'Assistant(e) administratif(ve) — Paris', 'Siège Paris 9e',
  '1 office assistant in Paris to replace an employee on maternity leave.',
  E'Objet : Remplacement assistante — siège Paris\n\nBonjour,\n\nNous devons remplacer Julie Perrin, assistante administrative, pendant son congé maternité.\nPoste au siège Paris 9e, idéalement à partir du lundi 19 octobre 2026 (dès que possible), jusqu''au vendredi 18 décembre 2026.\nHoraires : du lundi au vendredi 9h00-17h00, 35 h par semaine, sans heures supplémentaires.\nTaux horaire : 15,50 €.\nMotif : remplacement d''un salarié absent.\nMaîtrise du Pack Office indispensable.\n\nBien à vous,\nNathalie Roche\nDRH — 01 44 55 66 77 — nathalie.roche@loginord.example');
