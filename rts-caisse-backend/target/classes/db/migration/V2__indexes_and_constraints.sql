-- ============================================================
--  V2 : index de recherche plein-texte + contraintes métier
-- ============================================================

-- Index trigram sur le motif pour accélérer les recherches ILIKE '%xxx%'
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_operation_motif_trgm
    ON operations_caisse USING gin (motif gin_trgm_ops);

-- Contrainte : le montant d'une opération doit être strictement positif
ALTER TABLE operations_caisse
    DROP CONSTRAINT IF EXISTS chk_operation_montant_positif;
ALTER TABLE operations_caisse
    ADD CONSTRAINT chk_operation_montant_positif CHECK (montant > 0);

-- Contrainte : le solde courant d'une caisse ne peut pas être négatif
ALTER TABLE caisses
    DROP CONSTRAINT IF EXISTS chk_caisse_solde_positif;
ALTER TABLE caisses
    ADD CONSTRAINT chk_caisse_solde_positif CHECK (solde_courant >= 0);
