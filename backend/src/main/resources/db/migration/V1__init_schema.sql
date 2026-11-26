-- =====================================================================
-- V1__init_schema.sql
-- Order-to-Audit system: initial schema
--
-- Conventions
--   * Money        : NUMERIC(14,2)  (rupees, exact to the paisa)
--   * Quantities   : NUMERIC(14,4)  always in the item's BASE unit
--   * Unit costs   : NUMERIC(14,4)  (cost per base unit, before rounding)
--   * Stock is never stored as a number. Balances are derived from
--     stock_movement (see the stock_balance view at the end).
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. Identity
-- ---------------------------------------------------------------------

CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL
                  CHECK (role IN ('SALESMAN', 'ADMIN', 'PRODUCTION', 'AUDITOR', 'OWNER')),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Single row: the factory itself (GSTIN, home state for CGST/SGST vs IGST)
CREATE TABLE company (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(150) NOT NULL,
    gstin      VARCHAR(15)  NOT NULL,
    state_code VARCHAR(2)   NOT NULL             -- '33' = Tamil Nadu
               CHECK (state_code ~ '^[0-9]{2}$'),
    address    TEXT,
    -- R8.9: a variance beyond +/- this percentage is flagged
    variance_tolerance_pct NUMERIC(5,2) NOT NULL DEFAULT 3.00 CHECK (variance_tolerance_pct >= 0)
);


-- ---------------------------------------------------------------------
-- 2. Masters: units, items, parties
-- ---------------------------------------------------------------------

-- factor_to_base converts this unit to its dimension's base unit
-- (kg for MASS, L for VOLUME, pc for COUNT). NULL means the factor
-- depends on the item (e.g. a carton) and lives in item_uom_conversion.
CREATE TABLE uom (
    id             BIGSERIAL PRIMARY KEY,
    code           VARCHAR(10) NOT NULL UNIQUE,  -- kg, g, L, ml, pc, pkt, ctn
    name           VARCHAR(50) NOT NULL,
    dimension      VARCHAR(10) NOT NULL CHECK (dimension IN ('MASS', 'VOLUME', 'COUNT')),
    factor_to_base NUMERIC(18,6) CHECK (factor_to_base > 0)
);

CREATE TABLE item (
    id             BIGSERIAL PRIMARY KEY,
    code           VARCHAR(30)  NOT NULL UNIQUE,
    name           VARCHAR(150) NOT NULL,
    kind           VARCHAR(10)  NOT NULL CHECK (kind IN ('RAW', 'PACKAGING', 'FG')),
    base_uom_id    BIGINT       NOT NULL REFERENCES uom(id),
    hsn_code       VARCHAR(10),
    gst_rate       NUMERIC(5,2) CHECK (gst_rate >= 0),
    selling_price  NUMERIC(14,2) CHECK (selling_price >= 0),   -- per base unit, before tax (FG)
    pack_weight_kg NUMERIC(14,4) CHECK (pack_weight_kg > 0),   -- FG only: 0.200 for Murukku 200 g
    reorder_level  NUMERIC(14,4) CHECK (reorder_level >= 0),
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fg_needs_price CHECK (kind <> 'FG' OR (selling_price IS NOT NULL AND hsn_code IS NOT NULL))
);

-- Item-specific units, e.g. 1 carton of Murukku 200 g = 20 packets
CREATE TABLE item_uom_conversion (
    id          BIGSERIAL PRIMARY KEY,
    item_id     BIGINT        NOT NULL REFERENCES item(id),
    uom_id      BIGINT        NOT NULL REFERENCES uom(id),
    qty_in_base NUMERIC(14,4) NOT NULL CHECK (qty_in_base > 0),
    UNIQUE (item_id, uom_id)
);

CREATE TABLE customer (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(30)  NOT NULL UNIQUE,
    name        VARCHAR(150) NOT NULL,
    type        VARCHAR(15)  NOT NULL CHECK (type IN ('RETAILER', 'DISTRIBUTOR')),
    gstin       VARCHAR(15),                      -- NULL for unregistered (B2C)
    state_code  VARCHAR(2)   NOT NULL             -- decides CGST+SGST vs IGST
                CHECK (state_code ~ '^[0-9]{2}$'),
    address     TEXT,
    phone       VARCHAR(15),
    salesman_id BIGINT       REFERENCES app_user(id),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- R5.3: a GSTIN is 15 characters and its first two digits are the state code
    CONSTRAINT gstin_matches_state CHECK (
        gstin IS NULL OR (length(gstin) = 15 AND left(gstin, 2) = state_code)
    )
);

CREATE TABLE supplier (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(150) NOT NULL,
    gstin      VARCHAR(15),
    state_code VARCHAR(2) CHECK (state_code ~ '^[0-9]{2}$'),
    phone      VARCHAR(15),
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);


-- ---------------------------------------------------------------------
-- 3. Recipe (Bill of Materials)
-- ---------------------------------------------------------------------

CREATE TABLE bom (
    id         BIGSERIAL PRIMARY KEY,
    product_id BIGINT        NOT NULL REFERENCES item(id),   -- an FG item
    version    INT           NOT NULL DEFAULT 1,
    output_qty NUMERIC(14,4) NOT NULL CHECK (output_qty > 0), -- packets per batch (200)
    active     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (product_id, version)
);

-- Only one active BOM per product
CREATE UNIQUE INDEX ux_bom_one_active ON bom (product_id) WHERE active;

-- qty is per batch, in the component item's base unit
CREATE TABLE bom_line (
    id        BIGSERIAL PRIMARY KEY,
    bom_id    BIGINT        NOT NULL REFERENCES bom(id) ON DELETE CASCADE,
    item_id   BIGINT        NOT NULL REFERENCES item(id),
    qty       NUMERIC(14,4) NOT NULL CHECK (qty > 0),
    line_kind VARCHAR(12)   NOT NULL CHECK (line_kind IN ('INGREDIENT', 'PACKAGING')),
    UNIQUE (bom_id, item_id)
);


-- ---------------------------------------------------------------------
-- 4. Purchases
-- ---------------------------------------------------------------------

CREATE TABLE purchase (
    id            BIGSERIAL PRIMARY KEY,
    supplier_id   BIGINT      NOT NULL REFERENCES supplier(id),
    purchase_date DATE        NOT NULL,
    bill_no       VARCHAR(50) NOT NULL,
    -- R7.6: a posted purchase is never edited. A wrong one is reversed, which keeps the row.
    status        VARCHAR(10) NOT NULL DEFAULT 'POSTED' CHECK (status IN ('POSTED', 'REVERSED')),
    reversed_at   TIMESTAMPTZ,
    reversed_by   BIGINT      REFERENCES app_user(id),
    created_by    BIGINT      NOT NULL REFERENCES app_user(id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (supplier_id, bill_no),
    -- Reversal details travel together: REVERSED <=> reversed_at, and reversed_at <=> reversed_by
    CONSTRAINT purchase_reversed_state CHECK ((status = 'REVERSED') = (reversed_at IS NOT NULL)),
    CONSTRAINT purchase_reversed_by_with_at CHECK ((reversed_at IS NULL) = (reversed_by IS NULL))
);

-- qty in base unit; rate per base unit (e.g. Rs 45 per kg)
CREATE TABLE purchase_line (
    id          BIGSERIAL PRIMARY KEY,
    purchase_id BIGINT        NOT NULL REFERENCES purchase(id) ON DELETE CASCADE,
    item_id     BIGINT        NOT NULL REFERENCES item(id),
    qty         NUMERIC(14,4) NOT NULL CHECK (qty > 0),
    rate        NUMERIC(14,4) NOT NULL CHECK (rate >= 0),
    amount      NUMERIC(14,2) NOT NULL CHECK (amount >= 0),
    -- One line per item: two flour lines would post two RECEIPT movements with the
    -- same (source, item, type) and hit ux_stock_movement_no_double_post (R6.8).
    CONSTRAINT ux_purchase_line_item UNIQUE (purchase_id, item_id)
);


-- ---------------------------------------------------------------------
-- 5. Production
-- ---------------------------------------------------------------------

CREATE TABLE production_run (
    id             BIGSERIAL PRIMARY KEY,
    run_no         VARCHAR(30)   NOT NULL UNIQUE,
    bom_id         BIGINT        NOT NULL REFERENCES bom(id),
    run_date       DATE          NOT NULL,
    batches        NUMERIC(10,4) NOT NULL CHECK (batches > 0),
    planned_output NUMERIC(14,4) NOT NULL CHECK (planned_output > 0),  -- bom.output_qty x batches
    actual_output  NUMERIC(14,4) CHECK (actual_output >= 0),           -- good packets, set before posting
    -- R8 status flow: DRAFT -> POSTED -> REVERSED, or DRAFT -> CANCELLED
    status         VARCHAR(10)   NOT NULL DEFAULT 'DRAFT'
                   CHECK (status IN ('DRAFT', 'POSTED', 'CANCELLED', 'REVERSED')),
    posted_at      TIMESTAMPTZ,
    posted_by      BIGINT        REFERENCES app_user(id),
    reversed_at    TIMESTAMPTZ,
    reversed_by    BIGINT        REFERENCES app_user(id),
    created_by     BIGINT        NOT NULL REFERENCES app_user(id),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- A run that has been posted (and maybe reversed since) always has its good output
    CONSTRAINT posted_has_output CHECK (status NOT IN ('POSTED', 'REVERSED') OR actual_output IS NOT NULL),
    -- Reversal details travel together: REVERSED <=> reversed_at, and reversed_at <=> reversed_by
    CONSTRAINT production_run_reversed_state CHECK ((status = 'REVERSED') = (reversed_at IS NOT NULL)),
    CONSTRAINT production_run_reversed_by_with_at CHECK ((reversed_at IS NULL) = (reversed_by IS NULL))
);

-- One row per component. actual_qty is everything issued, INCLUDING scrap
-- (194 pouches = 188 used + 6 damaged, scrap_qty = 6).
CREATE TABLE production_consumption (
    id          BIGSERIAL PRIMARY KEY,
    run_id      BIGINT        NOT NULL REFERENCES production_run(id) ON DELETE CASCADE,
    item_id     BIGINT        NOT NULL REFERENCES item(id),
    planned_qty NUMERIC(14,4) NOT NULL CHECK (planned_qty >= 0),
    actual_qty  NUMERIC(14,4) CHECK (actual_qty >= 0),
    scrap_qty   NUMERIC(14,4) NOT NULL DEFAULT 0 CHECK (scrap_qty >= 0),
    unit_cost   NUMERIC(14,4),                    -- weighted average cost at issue time
    UNIQUE (run_id, item_id),
    CONSTRAINT scrap_within_actual CHECK (actual_qty IS NULL OR scrap_qty <= actual_qty)
);

-- Either charged to one run, or a monthly overhead allocated later
CREATE TABLE overhead_entry (
    id           BIGSERIAL PRIMARY KEY,
    run_id       BIGINT        REFERENCES production_run(id),
    period_month DATE,                            -- first day of the month
    type         VARCHAR(15)   NOT NULL CHECK (type IN ('LABOUR', 'POWER_GAS', 'OTHER')),
    amount       NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    note         VARCHAR(200),
    created_by   BIGINT        NOT NULL REFERENCES app_user(id),
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT run_or_month CHECK ((run_id IS NULL) <> (period_month IS NULL))
);


-- ---------------------------------------------------------------------
-- 6. Sales orders
-- ---------------------------------------------------------------------

CREATE TABLE sales_order (
    id          BIGSERIAL PRIMARY KEY,
    order_no    VARCHAR(30) NOT NULL UNIQUE,
    client_uuid UUID        NOT NULL UNIQUE,      -- generated on the phone; makes offline resubmits safe
    customer_id BIGINT      NOT NULL REFERENCES customer(id),
    salesman_id BIGINT      NOT NULL REFERENCES app_user(id),
    order_date  DATE        NOT NULL,
    status      VARCHAR(12) NOT NULL DEFAULT 'SUBMITTED'
                CHECK (status IN ('SUBMITTED', 'APPROVED', 'REJECTED', 'INVOICED', 'DISPATCHED', 'CANCELLED')),
    approved_by BIGINT      REFERENCES app_user(id),
    approved_at TIMESTAMPTZ,
    remarks     VARCHAR(500),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- approved_qty can be lower than qty (partial approval on shortage)
CREATE TABLE sales_order_line (
    id           BIGSERIAL PRIMARY KEY,
    order_id     BIGINT        NOT NULL REFERENCES sales_order(id) ON DELETE CASCADE,
    item_id      BIGINT        NOT NULL REFERENCES item(id),
    qty          NUMERIC(14,4) NOT NULL CHECK (qty > 0),
    rate         NUMERIC(14,2) NOT NULL CHECK (rate >= 0),
    approved_qty NUMERIC(14,4) CHECK (approved_qty >= 0 AND approved_qty <= qty),
    UNIQUE (order_id, item_id)
);


-- ---------------------------------------------------------------------
-- 7. GST invoices
-- ---------------------------------------------------------------------

-- One row per document type per financial year (R10.2). Lock it with
-- SELECT ... FOR UPDATE when issuing a number so two admins can never get
-- the same number, and a rollback never leaves a gap.
CREATE TABLE invoice_sequence (
    doc_type       VARCHAR(12) NOT NULL CHECK (doc_type IN ('INVOICE', 'CREDIT_NOTE')),
    financial_year VARCHAR(7)  NOT NULL CHECK (financial_year ~ '^[0-9]{4}-[0-9]{2}$'),  -- '2026-27'
    prefix         VARCHAR(10) NOT NULL,          -- 'INV' or 'CN'
    next_number    INT         NOT NULL DEFAULT 1 CHECK (next_number > 0),
    PRIMARY KEY (doc_type, financial_year)
);

CREATE TABLE invoice (
    id              BIGSERIAL PRIMARY KEY,
    invoice_no      VARCHAR(30)   NOT NULL UNIQUE,   -- INV/2026-27/0001
    -- No FK to invoice_sequence: its key is now (doc_type, financial_year) and
    -- an invoice is always doc_type INVOICE. The service locks that row to issue the number.
    financial_year  VARCHAR(7)    NOT NULL CHECK (financial_year ~ '^[0-9]{4}-[0-9]{2}$'),
    invoice_date    DATE          NOT NULL,
    order_id        BIGINT        UNIQUE REFERENCES sales_order(id),
    customer_id     BIGINT        NOT NULL REFERENCES customer(id),
    place_of_supply VARCHAR(2)    NOT NULL           -- customer's state code at invoicing time
                    CHECK (place_of_supply ~ '^[0-9]{2}$'),
    supply_type     VARCHAR(5)    NOT NULL CHECK (supply_type IN ('INTRA', 'INTER')),
    taxable_value   NUMERIC(14,2) NOT NULL CHECK (taxable_value >= 0),
    cgst            NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (cgst >= 0),
    sgst            NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (sgst >= 0),
    igst            NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (igst >= 0),
    total           NUMERIC(14,2) NOT NULL CHECK (total >= 0),
    status          VARCHAR(10)   NOT NULL DEFAULT 'POSTED' CHECK (status IN ('POSTED', 'CANCELLED')),
    created_by      BIGINT        NOT NULL REFERENCES app_user(id),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT total_adds_up  CHECK (total = taxable_value + cgst + sgst + igst),
    CONSTRAINT tax_matches_supply CHECK (
        (supply_type = 'INTRA' AND igst = 0)
     OR (supply_type = 'INTER' AND cgst = 0 AND sgst = 0)
    )
);

-- HSN, rate and GST % are copied in at invoicing time, so later price
-- list changes never alter a posted invoice.
CREATE TABLE invoice_line (
    id            BIGSERIAL PRIMARY KEY,
    invoice_id    BIGINT        NOT NULL REFERENCES invoice(id) ON DELETE CASCADE,
    item_id       BIGINT        NOT NULL REFERENCES item(id),
    hsn_code      VARCHAR(10)   NOT NULL,
    qty           NUMERIC(14,4) NOT NULL CHECK (qty > 0),
    rate          NUMERIC(14,2) NOT NULL CHECK (rate >= 0),
    taxable_value NUMERIC(14,2) NOT NULL CHECK (taxable_value >= 0),
    gst_rate      NUMERIC(5,2)  NOT NULL CHECK (gst_rate >= 0),
    cgst          NUMERIC(14,2) NOT NULL DEFAULT 0,
    sgst          NUMERIC(14,2) NOT NULL DEFAULT 0,
    igst          NUMERIC(14,2) NOT NULL DEFAULT 0,
    CONSTRAINT ux_invoice_line_item UNIQUE (invoice_id, item_id)
);

-- Posted invoices are never deleted: a credit note cancels them (R10.6).
-- It repeats the invoice's tax split so GST reports can deduct it directly.
CREATE TABLE credit_note (
    id            BIGSERIAL PRIMARY KEY,
    note_no       VARCHAR(30)   NOT NULL UNIQUE,    -- CN/2026-27/0001
    invoice_id    BIGINT        NOT NULL UNIQUE REFERENCES invoice(id),
    note_date     DATE          NOT NULL,
    reason        VARCHAR(500)  NOT NULL,
    supply_type   VARCHAR(5)    NOT NULL CHECK (supply_type IN ('INTRA', 'INTER')),
    taxable_value NUMERIC(14,2) NOT NULL CHECK (taxable_value >= 0),
    cgst          NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (cgst >= 0),
    sgst          NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (sgst >= 0),
    igst          NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (igst >= 0),
    total         NUMERIC(14,2) NOT NULL CHECK (total > 0),
    created_by    BIGINT        NOT NULL REFERENCES app_user(id),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT cn_total_adds_up CHECK (total = taxable_value + cgst + sgst + igst),
    CONSTRAINT cn_tax_matches_supply CHECK (
        (supply_type = 'INTRA' AND igst = 0)
     OR (supply_type = 'INTER' AND cgst = 0 AND sgst = 0)
    )
);


-- ---------------------------------------------------------------------
-- 8. Dispatch (the only sales step that moves stock)
-- ---------------------------------------------------------------------

CREATE TABLE dispatch (
    id            BIGSERIAL PRIMARY KEY,
    dispatch_no   VARCHAR(30) NOT NULL UNIQUE,
    invoice_id    BIGINT      NOT NULL REFERENCES invoice(id),
    dispatch_date DATE        NOT NULL,
    vehicle_no    VARCHAR(20),
    created_by    BIGINT      NOT NULL REFERENCES app_user(id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE dispatch_line (
    id          BIGSERIAL PRIMARY KEY,
    dispatch_id BIGINT        NOT NULL REFERENCES dispatch(id) ON DELETE CASCADE,
    item_id     BIGINT        NOT NULL REFERENCES item(id),
    qty         NUMERIC(14,4) NOT NULL CHECK (qty > 0),
    -- Same reason as purchase_line: one SALE movement per (dispatch, item)
    CONSTRAINT ux_dispatch_line_item UNIQUE (dispatch_id, item_id)
);


-- ---------------------------------------------------------------------
-- 9. Payments and collections
-- ---------------------------------------------------------------------

CREATE TABLE payment (
    id          BIGSERIAL PRIMARY KEY,
    customer_id BIGINT        NOT NULL REFERENCES customer(id),
    received_on DATE          NOT NULL,
    amount      NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    mode        VARCHAR(10)   NOT NULL CHECK (mode IN ('CASH', 'UPI', 'BANK', 'CHEQUE')),
    reference   VARCHAR(50),
    -- R12.5: a wrong payment is reversed, not deleted. Its allocations stay but stop counting.
    status      VARCHAR(10)   NOT NULL DEFAULT 'POSTED' CHECK (status IN ('POSTED', 'REVERSED')),
    reversed_at TIMESTAMPTZ,
    reversed_by BIGINT        REFERENCES app_user(id),
    created_by  BIGINT        NOT NULL REFERENCES app_user(id),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- Reversal details travel together: REVERSED <=> reversed_at, and reversed_at <=> reversed_by
    CONSTRAINT payment_reversed_state CHECK ((status = 'REVERSED') = (reversed_at IS NOT NULL)),
    CONSTRAINT payment_reversed_by_with_at CHECK ((reversed_at IS NULL) = (reversed_by IS NULL))
);

-- One payment can settle several invoices; one invoice can be paid in parts
CREATE TABLE payment_allocation (
    id         BIGSERIAL PRIMARY KEY,
    payment_id BIGINT        NOT NULL REFERENCES payment(id) ON DELETE CASCADE,
    invoice_id BIGINT        NOT NULL REFERENCES invoice(id),
    amount     NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    UNIQUE (payment_id, invoice_id)
);


-- ---------------------------------------------------------------------
-- 10. Stock ledger (the heart of the system)
-- ---------------------------------------------------------------------

-- A manual stock-count correction (R6.9). Posting one writes an ADJUSTMENT
-- row in stock_movement with source_type 'ADJUSTMENT' and source_id = this id.
-- qty is signed: positive = found extra, negative = lost / damaged.
CREATE TABLE stock_adjustment (
    id              BIGSERIAL PRIMARY KEY,
    item_id         BIGINT        NOT NULL REFERENCES item(id),
    adjustment_date DATE          NOT NULL,
    qty             NUMERIC(14,4) NOT NULL CHECK (qty <> 0),
    reason          VARCHAR(500)  NOT NULL CHECK (length(btrim(reason)) > 0),
    created_by      BIGINT        NOT NULL REFERENCES app_user(id),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Every stock change is one row. qty is signed (+ in, - out) in the
-- item's base unit. source_type + source_id point back to the document
-- that caused it (not a foreign key, because the source table varies).
CREATE TABLE stock_movement (
    id            BIGSERIAL PRIMARY KEY,
    item_id       BIGINT        NOT NULL REFERENCES item(id),
    movement_date TIMESTAMPTZ   NOT NULL,
    qty           NUMERIC(14,4) NOT NULL CHECK (qty <> 0),
    unit_cost     NUMERIC(14,4) NOT NULL CHECK (unit_cost >= 0),
    value         NUMERIC(14,2) NOT NULL,          -- qty x unit_cost, signed like qty
    movement_type VARCHAR(20)   NOT NULL CHECK (movement_type IN (
                      'OPENING',            -- opening balance
                      'RECEIPT',            -- purchase in
                      'ISSUE',              -- raw/packaging issued to production
                      'PRODUCTION_OUTPUT',  -- finished goods in
                      'SALE',               -- finished goods dispatched
                      'RETURN',             -- goods back via credit note
                      'ADJUSTMENT',         -- stock count correction
                      'REVERSAL')),         -- undoes a posted document; keeps that document's source_type/source_id
    source_type   VARCHAR(20)   NOT NULL CHECK (source_type IN (
                      'OPENING', 'PURCHASE', 'PRODUCTION_RUN', 'DISPATCH', 'CREDIT_NOTE', 'ADJUSTMENT')),
    source_id     BIGINT,                          -- NULL only for OPENING
    batch_no      VARCHAR(30),                     -- optional, for batch/expiry stretch goal
    remarks       VARCHAR(200),
    created_by    BIGINT        NOT NULL REFERENCES app_user(id),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT sign_matches_type CHECK (
        (movement_type IN ('OPENING', 'RECEIPT', 'PRODUCTION_OUTPUT', 'RETURN') AND qty > 0)
     OR (movement_type IN ('ISSUE', 'SALE') AND qty < 0)
     OR  movement_type IN ('ADJUSTMENT', 'REVERSAL')   -- either sign: a reversal is the opposite of the original
    ),
    CONSTRAINT source_required CHECK (source_type = 'OPENING' OR source_id IS NOT NULL),
    -- value follows qty's direction. "value = 0" is allowed because a tiny qty
    -- times a tiny cost can round to 0.00 at 2 decimals.
    CONSTRAINT value_sign_matches_qty CHECK (value = 0 OR sign(value) = sign(qty)),
    -- R6.8: one document cannot move the same item the same way twice.
    -- NULLS NOT DISTINCT (PostgreSQL 15+) also stops a second OPENING row,
    -- because OPENING rows have source_id NULL.
    CONSTRAINT ux_stock_movement_no_double_post
        UNIQUE NULLS NOT DISTINCT (source_type, source_id, item_id, movement_type)
);

CREATE INDEX ix_stock_movement_item_date ON stock_movement (item_id, movement_date);
CREATE INDEX ix_stock_movement_source    ON stock_movement (source_type, source_id);


-- ---------------------------------------------------------------------
-- 11. Audit trail
-- ---------------------------------------------------------------------

CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL,              -- 'INVOICE', 'PRODUCTION_RUN', ...
    entity_id   BIGINT,                            -- NULL for events with no entity (failed login, unknown user)
    action     VARCHAR(20) NOT NULL CHECK (action IN (
                    'CREATE', 'UPDATE', 'APPROVE', 'REJECT', 'CANCEL',
                    'POST', 'REVERSE', 'DISPATCH', 'PAYMENT', 'LOGIN', 'LOGIN_FAILED')),
    before_data JSONB,
    after_data  JSONB,
    -- R14.4: a failed login may have no matching user, so user_id can be NULL.
    -- username is copied in so the entry still says who tried.
    user_id     BIGINT      REFERENCES app_user(id),
    username    VARCHAR(50),
    at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_audit_log_entity ON audit_log (entity_type, entity_id);


-- ---------------------------------------------------------------------
-- 12. Helpful indexes on foreign keys used in reports
-- ---------------------------------------------------------------------

CREATE INDEX ix_sales_order_customer    ON sales_order (customer_id);
CREATE INDEX ix_sales_order_salesman    ON sales_order (salesman_id);
CREATE INDEX ix_invoice_customer_date   ON invoice (customer_id, invoice_date);
CREATE INDEX ix_payment_customer        ON payment (customer_id);
CREATE INDEX ix_payment_alloc_invoice   ON payment_allocation (invoice_id);
CREATE INDEX ix_purchase_line_item      ON purchase_line (item_id);
CREATE INDEX ix_production_run_date     ON production_run (run_date);


-- ---------------------------------------------------------------------
-- 13. Derived views (never stored, always rebuilt from the ledger)
-- ---------------------------------------------------------------------

-- Current on-hand quantity and value per item
CREATE VIEW stock_balance AS
SELECT i.id                          AS item_id,
       i.code,
       i.name,
       i.kind,
       COALESCE(SUM(m.qty), 0)       AS qty_on_hand,
       COALESCE(SUM(m.value), 0)     AS stock_value
FROM item i
LEFT JOIN stock_movement m ON m.item_id = i.id
GROUP BY i.id, i.code, i.name, i.kind;

-- Outstanding per posted invoice: total minus what has been allocated.
-- Only allocations from POSTED payments count (R12.5): a reversed payment is ignored.
CREATE VIEW invoice_outstanding AS
SELECT inv.id                                  AS invoice_id,
       inv.invoice_no,
       inv.customer_id,
       inv.invoice_date,
       inv.total,
       COALESCE(SUM(pa.amount), 0)             AS paid,
       inv.total - COALESCE(SUM(pa.amount), 0) AS outstanding
FROM invoice inv
LEFT JOIN (SELECT a.invoice_id, a.amount
           FROM payment_allocation a
           JOIN payment p ON p.id = a.payment_id
           WHERE p.status = 'POSTED') pa ON pa.invoice_id = inv.id
WHERE inv.status = 'POSTED'
GROUP BY inv.id, inv.invoice_no, inv.customer_id, inv.invoice_date, inv.total;
