-- =====================================================================
-- V2__production_run_numbering.sql
-- Production run numbers (RUN/2026-27/0001) use the same per-financial-year
-- counter table as invoices and credit notes (DocumentNumberService).
-- =====================================================================

-- V1 made doc_type VARCHAR(12), too short for 'PRODUCTION_RUN' (14 characters).
ALTER TABLE invoice_sequence ALTER COLUMN doc_type TYPE VARCHAR(20);

-- V1 named this check automatically. Widen it to allow the new document type.
ALTER TABLE invoice_sequence DROP CONSTRAINT invoice_sequence_doc_type_check;
ALTER TABLE invoice_sequence
    ADD CONSTRAINT invoice_sequence_doc_type_check
    CHECK (doc_type IN ('INVOICE', 'CREDIT_NOTE', 'PRODUCTION_RUN'));

-- First financial year of the benchmark. Later years are created on demand.
INSERT INTO invoice_sequence (doc_type, financial_year, prefix, next_number)
VALUES ('PRODUCTION_RUN', '2026-27', 'RUN', 1);
