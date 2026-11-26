-- =====================================================================
-- V3__sales_numbering.sql
-- Sales (build step 5):
--   1. Sales order (SO/) and dispatch (DC/) numbers use the same
--      per-financial-year counter table as invoices (DocumentNumberService).
--   2. The invoice keeps its own copy of the seller, buyer and item names
--      (R10.5), so a reprint never changes when master data is edited.
-- =====================================================================

ALTER TABLE invoice_sequence DROP CONSTRAINT invoice_sequence_doc_type_check;
ALTER TABLE invoice_sequence
    ADD CONSTRAINT invoice_sequence_doc_type_check
    CHECK (doc_type IN ('INVOICE', 'CREDIT_NOTE', 'PRODUCTION_RUN', 'SALES_ORDER', 'DISPATCH'));

INSERT INTO invoice_sequence (doc_type, financial_year, prefix, next_number)
VALUES ('SALES_ORDER', '2026-27', 'SO', 1),
       ('DISPATCH',    '2026-27', 'DC', 1);

-- NOT NULL without a default is safe: nothing could create an invoice before this migration.
-- The two addresses stay optional because company.address and customer.address are optional.
-- The buyer's state is already copied in place_of_supply.
ALTER TABLE invoice
    ADD COLUMN seller_name       VARCHAR(150) NOT NULL,
    ADD COLUMN seller_gstin      VARCHAR(15)  NOT NULL,
    ADD COLUMN seller_state_code VARCHAR(2)   NOT NULL
               CONSTRAINT invoice_seller_state_code_check CHECK (seller_state_code ~ '^[0-9]{2}$'),
    ADD COLUMN seller_address    TEXT,
    ADD COLUMN buyer_name        VARCHAR(150) NOT NULL,
    ADD COLUMN buyer_gstin       VARCHAR(15),             -- NULL for unregistered (B2C)
    ADD COLUMN buyer_address     TEXT;

ALTER TABLE invoice_line ADD COLUMN item_name VARCHAR(150) NOT NULL;
