-- =====================================================================
-- V1000__benchmark_seed.sql  (dev and test profiles only)
-- Problem statement section 5 master data + opening position.
-- Matches business-rules.md section 16 and assumptions A1, A2, A14.
--
-- The 60 kg flour purchase (benchmark step 1) is NOT seeded. The benchmark
-- test records it through PurchaseService so that WAC is really exercised.
--
-- TEST LOGINS (dummy credentials for the dev and test profiles only):
--   username      password     role
--   admin         Admin@123    ADMIN
--   production    Prod@123     PRODUCTION
--   salesman_a    Sales@123    SALESMAN  (Retailer A)
--   salesman_b    Sales@123    SALESMAN  (Distributor B)
--   auditor       Audit@123    AUDITOR
--   owner         Owner@123    OWNER
-- password_hash values are BCrypt, cost 10 (BCryptPasswordEncoder). They are not
-- used until JWT login arrives in build step 6.
-- =====================================================================

INSERT INTO app_user (username, password_hash, full_name, role) VALUES
    ('admin',      '$2a$10$QY8wA8I6PUS2we7aFD8F0eFHE49nrLkIsVPhIKV5np39M8lOTvNY2', 'Admin',              'ADMIN'),
    ('production', '$2a$10$Pew4jA.QwiDCXZ9BWUmv4uiKur2qXOQp8gTKCJ0CJOFLeyToGLn4e', 'Production Manager', 'PRODUCTION'),
    ('salesman_a', '$2a$10$RcXOiwFGdiKuBDI4Zsac5OZDFS2JjOHgXjudwmSJrwD6eKq9sMLIG', 'Salesman A',         'SALESMAN'),
    ('salesman_b', '$2a$10$EknKyvorRN8w6DfITUda6OUbAHVeQ7PbF6InW.8Aww4DVd9GWwOXq', 'Salesman B',         'SALESMAN'),
    ('auditor',    '$2a$10$tGxBOX3jQi9m/lxhZTHUvuiO.xbkQdx845GDSjl4cwr2qpsHTmsxm', 'Auditor',            'AUDITOR'),
    ('owner',      '$2a$10$pCKsn1rm/yBAClolbAyLj.KFCdDfHmJxm1d8G6WK4aYijJZyN8DHW', 'Owner',              'OWNER');

-- A1: dummy factory GSTIN in Tamil Nadu (33). variance_tolerance_pct uses its default, 3.00.
INSERT INTO company (name, gstin, state_code, address) VALUES
    ('Sample Murukku Foods', '33AAACO1234A1Z5', '33', 'Tiruchirappalli, Tamil Nadu');

-- Units (R3.1, R3.2). 'ctn' has no global factor: it is per item (item_uom_conversion).
INSERT INTO uom (code, name, dimension, factor_to_base) VALUES
    ('kg',  'Kilogram',  'MASS',   1),
    ('g',   'Gram',      'MASS',   0.001),
    ('L',   'Litre',     'VOLUME', 1),
    ('ml',  'Millilitre','VOLUME', 0.001),
    ('pc',  'Piece',     'COUNT',  1),
    ('pkt', 'Packet',    'COUNT',  1),
    ('ctn', 'Carton',    'COUNT',  NULL);

-- Items. A2: HSN 21069099, GST 5%, selling price 50 before tax, 200 g pack.
INSERT INTO item (code, name, kind, base_uom_id, hsn_code, gst_rate, selling_price, pack_weight_kg) VALUES
    ('FLOUR',  'Flour',               'RAW',       (SELECT id FROM uom WHERE code = 'kg'),  NULL, NULL, NULL, NULL),
    ('OIL',    'Cooking oil',         'RAW',       (SELECT id FROM uom WHERE code = 'L'),   NULL, NULL, NULL, NULL),
    ('SPICE',  'Salt and spice mix',  'RAW',       (SELECT id FROM uom WHERE code = 'kg'),  NULL, NULL, NULL, NULL),
    ('POUCH',  '200 g pouch',         'PACKAGING', (SELECT id FROM uom WHERE code = 'pc'),  NULL, NULL, NULL, NULL),
    ('CARTON', 'Carton (20 packets)', 'PACKAGING', (SELECT id FROM uom WHERE code = 'pc'),  NULL, NULL, NULL, NULL);

INSERT INTO item (code, name, kind, base_uom_id, hsn_code, gst_rate, selling_price, pack_weight_kg) VALUES
    ('MURUKKU200', 'Murukku 200 g', 'FG', (SELECT id FROM uom WHERE code = 'pkt'), '21069099', 5.00, 50.00, 0.2000);

-- R3.2: one carton of Murukku 200 g = 20 packets
INSERT INTO item_uom_conversion (item_id, uom_id, qty_in_base) VALUES
    ((SELECT id FROM item WHERE code = 'MURUKKU200'), (SELECT id FROM uom WHERE code = 'ctn'), 20);

-- Customers (A1). Retailer A: Tamil Nadu (33) -> CGST+SGST. Distributor B: Karnataka (29) -> IGST.
INSERT INTO customer (code, name, type, gstin, state_code, address, salesman_id) VALUES
    ('RET-A',  'Retailer A',     'RETAILER',    '33AAACR5055K1Z5', '33', 'Tiruchirappalli, Tamil Nadu',
        (SELECT id FROM app_user WHERE username = 'salesman_a')),
    ('DIST-B', 'Distributor B',  'DISTRIBUTOR', '29AAACD7654B1Z2', '29', 'Bengaluru, Karnataka',
        (SELECT id FROM app_user WHERE username = 'salesman_b'));

INSERT INTO supplier (name, gstin, state_code) VALUES
    ('Sample Flour Mills', '33AAACS1111F1Z8', '33');

-- BOM: Murukku 200 g, one batch = 200 packets (R5.6 to R5.9)
INSERT INTO bom (product_id, version, output_qty) VALUES
    ((SELECT id FROM item WHERE code = 'MURUKKU200'), 1, 200);

INSERT INTO bom_line (bom_id, item_id, qty, line_kind)
SELECT b.id, i.id, v.qty, v.line_kind
FROM bom b
JOIN (VALUES ('FLOUR',  40.0, 'INGREDIENT'),
             ('OIL',    10.0, 'INGREDIENT'),
             ('SPICE',   1.2, 'INGREDIENT'),
             ('POUCH', 200.0, 'PACKAGING'),
             ('CARTON', 10.0, 'PACKAGING')) AS v(code, qty, line_kind) ON TRUE
JOIN item i ON i.code = v.code
WHERE b.product_id = (SELECT id FROM item WHERE code = 'MURUKKU200') AND b.version = 1;

-- Invoice and credit-note numbers start at 0001 for FY 2026-27 (R10.2)
INSERT INTO invoice_sequence (doc_type, financial_year, prefix, next_number) VALUES
    ('INVOICE',     '2026-27', 'INV', 1),
    ('CREDIT_NOTE', '2026-27', 'CN',  1);

-- Opening stock (section 5). One OPENING movement per item, source_id NULL (only OPENING may).
-- A14: FG opening cost is not given in the brief, so it is assumed to be 30.00 per packet.
INSERT INTO stock_movement (item_id, movement_date, qty, unit_cost, value, movement_type, source_type, remarks, created_by)
SELECT i.id, TIMESTAMPTZ '2026-11-01 00:00:00+05:30', v.qty, v.unit_cost, ROUND(v.qty * v.unit_cost, 2),
       'OPENING', 'OPENING', 'Opening stock', (SELECT id FROM app_user WHERE username = 'admin')
FROM (VALUES ('FLOUR',      100, 42.00),
             ('OIL',         30, 140.00),
             ('SPICE',        5, 200.00),
             ('POUCH',      500, 2.50),
             ('CARTON',      30, 15.00),
             ('MURUKKU200',  50, 30.00)) AS v(code, qty, unit_cost)
JOIN item i ON i.code = v.code;
