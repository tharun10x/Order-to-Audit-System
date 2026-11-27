# Business Rules — Order-to-Audit System

This document defines how the system behaves: the roles, units, stock ledger, production and BOM logic, GST billing, costing and audit rules, and the assumptions behind them. The code, database migrations and automated tests implement these rules. Rule IDs (for example **R6.4**) are referenced from code comments and test names.

Problem reference: *Order-to-Audit System for a Small Food Manufacturer*, Centre for Digital Infrastructure, NIT Tiruchirappalli (Winter Internship 2026).

---

## Contents

1. [Scope](#1-scope)
2. [Roles and access](#2-roles-and-access)
3. [Units and conversions](#3-units-and-conversions)
4. [Numbers and rounding](#4-numbers-and-rounding)
5. [Master data and BOM](#5-master-data-and-bom)
6. [Stock ledger](#6-stock-ledger)
7. [Purchases and stock valuation](#7-purchases-and-stock-valuation)
8. [Production runs and variance](#8-production-runs-and-variance)
9. [Sales orders](#9-sales-orders)
10. [GST invoice](#10-gst-invoice)
11. [Dispatch](#11-dispatch)
12. [Payments and receivables](#12-payments-and-receivables)
13. [Costing](#13-costing)
14. [Audit trail](#14-audit-trail)
15. [Reports](#15-reports)
16. [Benchmark scenario — expected results](#16-benchmark-scenario--expected-results)
17. [Assumptions](#17-assumptions)

---

## 1. Scope

The system links the two loops in the problem statement. They meet at finished-goods stock:

```
SALES LOOP:      Order → Admin verifies → GST invoice → Dispatch ──(FG stock −)──┐
                                                                                 ▼
PRODUCTION LOOP: Purchase → Raw stock ──(issued per BOM)──▶ Production run ──(FG stock +)──▶ Finished goods
                                                                                 │
All transactions → monthly reports (sales & GST, purchases, stock ledger, production variance, cost per packet, outstanding)
```

Every transaction is entered **once, at its source**. Stock, costing and reports are derived from those entries and are never re-typed.

---

## 2. Roles and access

Every user has exactly one role. ✔ = allowed; every other role gets HTTP 403. Without a valid token every endpoint except login answers 401.

| Action (endpoints) | Salesman | Admin | Production | Auditor | Owner |
|---|:-:|:-:|:-:|:-:|:-:|
| Log in (`POST /api/auth/login`, public) | ✔ | ✔ | ✔ | ✔ | ✔ |
| Who am I (`GET /api/auth/me`) | ✔ | ✔ | ✔ | ✔ | ✔ |
| Customer list (`GET /api/customers`; a salesman gets only their own). Master lists take `?status=active` (default), `inactive` or `all` | ✔ | ✔ | | ✔ | ✔ |
| Price list (`GET /api/products`) | ✔ | ✔ | | | |
| Supplier list (`GET /api/suppliers`) | | ✔ | ✔ | | |
| Item list with the units each item accepts (`GET /api/items`, units by R3.3) | | ✔ | ✔ | ✔ | ✔ |
| Recipe versions of a product (`GET /api/boms?productCode=`) | | ✔ | ✔ | ✔ | ✔ |
| Manage customers, suppliers, items and recipes: create, edit, deactivate / reactivate (`POST`/`PUT /api/customers`, `/api/suppliers`, `/api/items`, `POST /api/boms`, `/api/boms/{id}/activate`), and the salesman list for assigning customers (`GET /api/salesmen`) | | ✔ | | | |
| Create order (`POST /api/orders`; an order entered by the Admin belongs to the customer's salesman) | ✔ | ✔ | | | |
| View orders and status (`GET /api/orders`, `/{id}`; a salesman sees only their own) | ✔ | ✔ | | ✔ | ✔ |
| Stock check, edit qty/price, approve, reject, cancel an order | | ✔ | | | |
| View invoices and download the PDF (`GET /api/invoices`, `/{id}`, `/{id}/pdf`) | | ✔ | | ✔ | ✔ |
| Raise GST invoice, cancel it (credit note), record dispatch, record payment | | ✔ | | | |
| View payments and customer outstanding (`GET /api/payments`, `GET /api/customers/{id}/outstanding`) | | ✔ | | ✔ | ✔ |
| Record purchase, i.e. raw-material receipt (`POST /api/purchases`) | | ✔ | ✔ | | |
| View purchases (`GET /api/purchases`) | | ✔ | ✔ | ✔ | ✔ |
| Create a production run, enter actuals, post, cancel, add overheads | | | ✔ | | |
| View production runs, stock balances and stock ledger | | ✔ | ✔ | ✔ | ✔ |
| Reports (R15): sales register, GST summary, purchase register, collections, outstanding (`GET /api/reports/{report}`) | | ✔ | | ✔ | ✔ |
| Reports: stock summary, stock ledger, production report (follow stock access) | | ✔ | ✔ | ✔ | ✔ |
| Own customer-wise sales (brief 4.1): `GET /api/reports/my-sales`, only the customers assigned to the logged-in salesman | ✔ | | | | |
| Owner dashboard (`GET /api/dashboard`) | | ✔ | | | ✔ |

**Not built yet** (the roles they will get):

| Action | Salesman | Admin | Production | Auditor | Owner |
|---|:-:|:-:|:-:|:-:|:-:|
| Manage users | | ✔ | | | |
| Stock adjustment (with reason) | | ✔ | | | |
| View audit trail | | ✔ | | ✔ | ✔ |

A salesman has no access to the invoice, payment or outstanding endpoints. Their sales figures come from `my-sales` above (built in step 6b). The services already apply R2.2 to invoices and outstanding. The Admin also sees the owner dashboard, so the figures can be checked before the Owner looks.

- **R2.1** Every API endpoint declares the roles allowed (`@PreAuthorize`). Anything not declared is denied: a test fails if an endpoint other than login has no declaration or is missing from the role-matrix test.
- **R2.2** A salesman can only see their own orders (created by them, or entered for them by the Admin) and sales for customers assigned to them. A salesman can order only for their own customers (HTTP 403 otherwise). Reading someone else's order, invoice or customer outstanding answers 404, so its existence is not revealed.
- **R2.3** Auditor and Owner are read-only. They cannot create or change any transaction.
- **R2.4** Inactive users cannot log in. Users are deactivated, never deleted. A wrong password, an unknown username and an inactive user get the same 401 message, and an unknown username still costs one BCrypt comparison, so neither the answer nor its timing shows which usernames exist. Passwords are stored as BCrypt hashes; a password over 72 bytes (BCrypt's limit) never matches. A username over 50 characters is rejected with 400.
- **R2.5** **Tokens:** `POST /api/auth/login` returns a JWT signed with HS256, valid for **8 hours** (`JWT_EXPIRY`, setting `ota.security.jwt.expiry`). Claims: `sub` (user id), `username`, `role`, `iat`, `exp`. It is sent as `Authorization: Bearer <token>`. Only HS256 tokens with an expiry are accepted, with no clock-skew allowance.
- **R2.6** The API is stateless: no session or cookie, so CSRF protection is off. Only `POST /api/auth/login` is public (Spring's internal error forward is also let through, so a server error is not reported as 401; calling `/error` directly needs a token). 401 (missing, invalid or expired token) and 403 (wrong role) use the same JSON error body as all other errors and do not say why a token was refused. Browsers may call the API from `http://localhost:5173` only (`CORS_ALLOWED_ORIGIN`). These rules are the same in every profile, including dev and test.
- **R2.7** On **every** request the token's user must still exist, be active and have the role named in the token, otherwise 401. Deactivating a user or changing a role therefore takes effect at once.
- **R2.8** **Secret:** the signing key comes from `JWT_SECRET` and must be at least 32 bytes, or the application does not start. Only the `dev` and `test` profiles have a default (public, for those profiles only). There is **no default profile**: without one there is no secret and no test data. `spring-boot:run` selects `dev`.
- **R2.9** **Known limits:** no limit on repeated failed logins (they are audited, R14.4); no logout or token revocation (a token lasts until it expires unless its user is deactivated or changes role); no user management yet, so outside dev and test, which seed the A15 users, there is no way to create a user and nobody can log in.

---

## 3. Units and conversions

- **R3.1** Every inventory item has one **base unit**, and all stock movements, balances and BOM quantities are stored in it:

| Item type | Base unit |
|---|---|
| Flour, spice mix | KG |
| Cooking oil | LITRE |
| Pouches, cartons, labels | PIECE |
| Finished goods | PACKET |

- **R3.2** Input may be entered in any unit that converts to the base unit. It is converted at the API boundary before saving:

| Entered unit | Base unit | Conversion |
|---|---|---|
| G | KG | ÷ 1,000 |
| ML | LITRE | ÷ 1,000 |
| KG, LITRE, PIECE, PACKET | same | × 1 |
| CARTON (of finished goods) | PACKET | × packets per carton of that product (Murukku 200 g: **20**) |

- **R3.3** A unit that does not convert to the item's base unit (for example LITRE for flour) is rejected. There is no density-based conversion. Conversion rules, in order:
  1. The item's own base unit: no change.
  2. A unit configured for that item in `item_uom_conversion` (the carton of Murukku).
  3. **MASS and VOLUME items** accept any unit of the same dimension (g for flour, ml for oil) through `uom.factor_to_base`.
  4. **COUNT items** (pouches, cartons, packets) accept only their base unit or a unit configured for that item. A pouch entered in packets is therefore rejected, even though both are counts.
- **R3.4** Conversion factors are master data, not constants in code: `uom.factor_to_base` holds the global factors (g → kg, ml → L), and `item_uom_conversion` holds per-item units such as the carton (20 packets of Murukku 200 g).
- **R3.5** Orders may be entered in packets or cartons. Invoices and stock always show packets.

---

## 4. Numbers and rounding

| Kind | Database | Java | Scale |
|---|---|---|---|
| Quantity | `NUMERIC(14,4)` | `BigDecimal` | 4 |
| Money (₹) | `NUMERIC(14,2)` | `BigDecimal` | 2 |
| Rate / unit cost | `NUMERIC(14,4)` | `BigDecimal` | 4 |
| Percentage | `NUMERIC(5,2)` | `BigDecimal` | 2 |

- **R4.1** `float` and `double` are never used for quantity or money.
- **R4.2** The rounding mode is `HALF_UP` throughout.
- **R4.3** GST is calculated and rounded **per invoice line**, then summed.
- **R4.4** Intermediate values (average cost, variance) keep full precision and are rounded only when stored or displayed.

---

## 5. Master data and BOM

### Products, items, customers

- **R5.1** A **product** is a sellable pack (SKU): name, pack size, selling price (before tax), HSN code, GST rate, packets per carton.
- **R5.2** An **inventory item** is anything stocked, with category `RAW_MATERIAL`, `PACKAGING` or `FINISHED_GOOD`. Each product has exactly one linked `FINISHED_GOOD` item, so raw materials and finished packets use the same stock ledger.
- **R5.3** A customer has a unique code (fixed once created), a type (`RETAILER` / `DISTRIBUTOR`), a **state with GST state code** (mandatory, a real GST state code, used to decide the place of supply), an optional GSTIN, and an assigned salesman, who must be an active user with the Salesman role. A GSTIN, for a customer or a supplier, has 15 characters in the official layout (state code, PAN, entity number, `Z`, check character) and its first two digits must equal the state code. The check character is not verified. A supplier's state is optional, but required with a GSTIN, and two active suppliers may not share a name.
- **R5.4** A record that has been used in a transaction is never hard-deleted. It is deactivated, and inactive records cannot be used in new transactions: a deactivated customer cannot receive new orders, a deactivated supplier cannot be on a new purchase, and a deactivated finished good cannot be ordered or produced. Existing documents are untouched. An item that an **active** recipe uses cannot be deactivated (409), or posting that recipe would consume an inactive item: activate a version without it first. Reactivation is always allowed. Every create, edit, deactivation and reactivation is audited with the values before and after (R14).
- **R5.5** Prices come from the product price list. Customer-specific prices are not used in this version. A price change affects only orders taken afterwards: an order copies the price when it is taken (R9.2), and its invoice is built from the order, so existing orders and invoices keep their price.
- **R5.11** **Items:** the code is unique and never changes; the kind (`RAW`, `PACKAGING`, `FG`) never changes (a different kind is a new item). The base unit is a unit with factor 1 (kg, L, pc, pkt), and a finished good is counted in pkt. The base unit is **locked** once the item has any stock movement or is a line in any recipe version, because ledger and recipe quantities are stored in it (409 with the reason; the item list says why it is locked). A finished good needs an HSN code (4, 6 or 8 digits), a GST rate (0 to 100), a selling price above 0 and a pack weight; packets per carton is optional and is stored as the item's `ctn` conversion (R3.2). Other items have none of these fields. A reorder level is optional.

### Bill of Materials

- **R5.6** A BOM belongs to one product and defines, for **one batch**, the standard quantity of each raw material and packaging item plus the **expected yield** (packets per batch).
- **R5.7** BOM lines are split by behaviour:
  - **Input-driven** (raw materials): consumed per batch, whatever the output.
  - **Output-driven** (packaging): consumed per finished packet. Stored as quantity per batch, so the standard per packet = BOM quantity ÷ expected yield.
- **R5.8** BOMs are **versioned**. One version per product is `ACTIVE`. Editing creates a new version, and runs keep the version they used. A version is never edited: a new one is saved **inactive**, numbered after the last, and activating it deactivates the previous version in the same transaction (the product row is locked, so two activations of one product run one after the other). Runs already created keep their own copied quantities (R8.5). A line's kind follows its item: a raw material is an ingredient and a packaging item is packaging; finished goods cannot be lines, an item appears once per version, and every version has at least one ingredient. Quantities may be entered in any unit the item accepts and are stored converted to the base unit (R5.9). Packets per batch is a whole number.
- **R5.9** All BOM quantities are > 0 and in the item's base unit.
- **R5.10** *Extensibility:* the raw-material recipe can be shared by more than one pack size (for example a 500 g pack made from the same dough). Each pack size then has its own packaging lines and expected yield. The benchmark uses the 200 g pack only. A new version may start by **copying the ingredient lines** (same items, same quantities per batch) of another product's active recipe, then add its own packaging and yield. *Deliberate choice:* copying creates an independent recipe version for the new pack, so later changes to the 200 g recipe don't affect it. A shared base recipe (an intermediate "dough" item that both packs consume) is possible future work.

**Benchmark BOM — Murukku 200 g, 1 batch = 200 packets expected**

| Item | Type | Qty / batch | Unit | Standard per packet |
|---|---|---:|---|---:|
| Flour | Raw, input-driven | 40.000 | KG | — |
| Cooking oil | Raw, input-driven | 10.000 | LITRE | — |
| Salt and spice mix | Raw, input-driven | 1.200 | KG | — |
| 200 g pouch | Packaging, output-driven | 200 | PIECE | 1 |
| Carton (holds 20 packets) | Packaging, output-driven | 10 | PIECE | 1/20 |

---

## 6. Stock ledger

Stock balances are **derived from a ledger of movements**. No one ever overwrites a stock number.

### Movement types

| Movement type (`stock_movement.movement_type`) | `qty` sign | `source_type` |
|---|:-:|---|
| `OPENING` | + | `OPENING` (no source id) |
| `RECEIPT` | + | `PURCHASE` |
| `ISSUE` | − | `PRODUCTION_RUN` (everything issued, including scrap) |
| `PRODUCTION_OUTPUT` | + | `PRODUCTION_RUN` (good packets) |
| `SALE` | − | `DISPATCH` |
| `RETURN` | + | `CREDIT_NOTE` (goods back) |
| `ADJUSTMENT` | ± | `ADJUSTMENT` (a `stock_adjustment` row, reason mandatory) |
| `REVERSAL` | ± (opposite of the original) | the **original document's** `source_type` and `source_id` |

Scrap is not a separate movement. One `ISSUE` row per item carries the full quantity issued, and the scrap part is recorded on `production_consumption.scrap_qty` (a second row for the same run, item and type would break R6.8).

A **reversal** of a posted purchase, production run or dispatch posts `REVERSAL` movements, one per original movement, with the opposite sign and the same item, `source_type` and `source_id`. It is never recorded as an `ADJUSTMENT`, so adjustments stay purely manual stock-count corrections. Because the movement type differs from the original's, R6.8 allows exactly one reversal per document and item.

> **Planned, not built yet:** the reversal services (purchase, production run, payment). The schema is ready (`REVERSAL` movement type, `status` / `reversed_at` / `reversed_by` columns), but no code posts reversals yet. If they are not built by submission they are listed as a known gap in the README.

### Rules

- **R6.1** Every movement stores: item, movement type, **signed quantity** (base unit; + in, − out), unit cost (≥ 0), value, source document type and id, user and timestamp. The database enforces:
  - `sign_matches_type`: OPENING, RECEIPT, PRODUCTION_OUTPUT and RETURN have `qty > 0`. ISSUE and SALE have `qty < 0`. ADJUSTMENT and REVERSAL may be either.
  - `value_sign_matches_qty`: `value = 0 OR sign(value) = sign(qty)`. The zero case exists because a tiny quantity times a tiny cost can round to 0.00.
  - **Value comes first.** `value` is the exact money amount: the bill amount for a receipt (R7.1), the share of the item's stock value for an outgoing movement (`stock value × qty ÷ qty on hand`, rounded once; all of it when the item is emptied), the total of the issues for production output (R13.6), and the dispatched value for a return (R10.7). `unit_cost` is the matching cost per base unit at 4 decimals, so `qty × unit_cost` can differ from `value` by a rounding amount.
- **R6.2** **Traceability:** every movement links to its source document (purchase, production run, dispatch, credit note or adjustment) through `source_type` and `source_id`. Only `OPENING` rows may have no `source_id`.
- **R6.3** The ledger is **append-only**. Movements are never updated or deleted. Corrections are made only with reversal or adjustment movements.
- **R6.4** For every item and period:

```
closing balance = opening balance + receipts − issues/sales ± adjustments
                = Σ (+) movements − Σ (−) movements
```

- **R6.5** **No stored balance.** On-hand quantity and stock value are read from the `stock_balance` view, which sums `stock_movement` per item. A balance therefore cannot drift from the ledger. The current average cost of an item is `stock_value ÷ qty_on_hand` from the same view (R7.3). The reconciliation check (stock report and `SchemaAndSeedTest`) verifies R6.4 per item. `SchemaAndSeedTest` also checks that each seeded opening value equals quantity × unit cost.
- **R6.6** **No negative stock.** An outgoing movement that would take `qty_on_hand` below zero is rejected, and the whole transaction rolls back. The check runs after the lock in R6.7, so two transactions cannot both pass it.
- **R6.7** **Concurrency:** there is no balance row to lock, so before checking or changing stock a transaction locks the **`item` row** (`SELECT … FOR UPDATE`). When several items are involved, they are locked in ascending item-id order to avoid deadlocks.
- **R6.8** **No double posting:** the unique constraint `ux_stock_movement_no_double_post` on (source type, source id, item, movement type) prevents the same document from moving stock twice. It is `NULLS NOT DISTINCT`, so a second `OPENING` row for an item (source id NULL) is also rejected. To keep documents from ever hitting it, `purchase_line`, `dispatch_line` and `invoice_line` are unique per (document, item): a bill cannot carry two flour lines.
- **R6.9** Adjustments are Admin-only and require a reason.

---

## 7. Purchases and stock valuation

- **R7.1** A purchase records a supplier, date, supplier invoice number and lines (item, quantity, unit, rate before GST). Quantity and rate are entered **in the unit written on the bill**, for example 60000 g at ₹0.045 per g. The system then:
  - converts the quantity to the item's base unit (R3.2) and stores it in `purchase_line.qty`;
  - sets `purchase_line.amount = round(entered qty × entered rate, 2)`, exactly as on the supplier bill (R4.2);
  - stores `purchase_line.rate` as the rate per **base** unit, `entered qty × entered rate ÷ base qty` at scale 4 (60000 g at 0.045 gives ₹45.0000 per kg);
  - posts the RECEIPT with `value = amount` and `unit_cost = amount ÷ base qty` (scale 4).

  So stock value always equals the sum of the bill amounts. A bill carries at most one line per item and only RAW or PACKAGING items (not finished goods).
- **R7.2** Saving a purchase posts one `RECEIPT` movement per line, in one transaction. A bill has at most one line per item (R6.8).
- **R7.3** **Valuation method: weighted average cost (WAC), per item.** It is recalculated on every receipt:

```
new average cost = (balance qty × current average cost + received qty × rate)
                   ÷ (balance qty + received qty)
```

- **R7.4** Every outgoing movement (issue, wastage, dispatch) is valued at the item's average cost **at the time of posting**, and that cost is stored on the movement so later purchases do not change history.
- **R7.5** *Why weighted average:* flour is bulk-stored and physically mixed in the bin, so lots are not kept apart in practice. WAC matches that reality, smooths price swings between purchases, is simple to audit, and is accepted for inventory valuation (AS 2). FIFO would need lot-level tracking, which this version does not have. Last price would overstate cost when older, cheaper stock is used.
- **R7.6** A posted purchase is corrected only by reversal, never by editing or deleting. Reversing sets `purchase.status` to `REVERSED` together with `reversed_at` and `reversed_by` (the database requires all three together: constraints `purchase_reversed_state` and `purchase_reversed_by_with_at`) and posts one `REVERSAL` movement per line (section 6). A reversal that would make stock negative is refused (R6.6), for example when the flour has already been used. *(Planned: the reversal service is not built yet.)*

**Benchmark:** flour opening 100 kg @ ₹42 + purchase 60 kg @ ₹45 → 160 kg, average **₹43.125/kg**.

---

## 8. Production runs and variance

### Status flow

```
DRAFT ──post──▶ POSTED ──reverse──▶ REVERSED
  └──cancel──▶ CANCELLED
```

These are the only values `production_run.status` accepts (default `DRAFT`). `posted_at` / `posted_by` record who posted the run and when, and `reversed_at` / `reversed_by` who reversed it and when. `posted_has_output` requires `actual_output` on every POSTED or REVERSED run.

- **R8.1** A DRAFT run can be edited or cancelled and has no stock effect. Edits are audit-logged.
- **R8.2** **Posting** a run is the only moment it moves stock, and it can happen **once** (status checked inside the locked transaction, plus R6.8).
- **R8.3** A POSTED run cannot be edited. It can only be reversed: status becomes `REVERSED` and opposite `REVERSAL` movements are posted (section 6). Both are audit-logged (`REVERSE`). The time and user are stored on the run (`reversed_at`, `reversed_by`). The database requires status `REVERSED`, `reversed_at` and `reversed_by` to be set together and never otherwise (`production_run_reversed_state`, `production_run_reversed_by_with_at`). *(Planned: the reversal service is not built yet.)*

### What a run records

- **R8.4** Product, BOM version, number of batches, date, supervisor, and, per BOM item: **standard quantity** (copied from the BOM at creation), **actual quantity used** and **wastage/damaged quantity**. Also **good output** in packets.
- **R8.5** Standard quantities are copied onto the run. Later BOM changes never alter a posted run.
- **R8.6** Standard quantities:

```
expected output          = expected yield × batches
standard (input-driven)  = BOM qty × batches
standard (output-driven) = BOM qty ÷ expected yield × good output
```

Packaging is judged against what was actually packed. Packing 188 packets should need 188 pouches, not 200.

For **counted** items (pouches, cartons) only whole units count, rounded down: 188 packets need `floor(10 × 188 ÷ 200)` = 9 whole cartons, not 9.4. The system calculates this from the run's own copied standards (planned qty = BOM qty × batches, planned output = yield × batches), not from the BOM, so BOM changes cannot affect it (R8.5). At creation the packaging lines hold a provisional planned quantity (BOM qty × batches). It is recalculated per good packet when the run is posted.

### Posting (one transaction)

1. Lock the run row (`SELECT … FOR UPDATE`) and confirm it is DRAFT, that the good output is more than 0, and that every line has an actual quantity (0 is allowed). A second request for the same run waits for the lock and then finds it POSTED (409).
2. Recalculate the packaging standards from the good output (R8.6).
3. Make **one** `InventoryService` call. It locks the item rows, including the finished good, in ascending id order (R6.7) and checks that stock covers `actual_qty` for every material. If any are short, it rejects with the **complete** shortage list (HTTP 409), nothing is written, and the run stays DRAFT.
4. Post one `ISSUE` per item with `actual_qty` > 0, for the full quantity issued (used + scrap), and record the scrap part in `production_consumption.scrap_qty` (see the note under section 6).
5. Post `PRODUCTION_OUTPUT` for **good output** only, valued at the **total value of the ISSUE movements** from step 4, that is material cost + packaging cost (R13.6).
   (In the benchmark pouch row, "194 = 188 used + 6 damaged": `actual_qty` 194, `scrap_qty` 6.)
6. Store each line's `unit_cost` from its ISSUE movement, set the status to POSTED with `posted_at` and `posted_by`, and write the audit entry.

Variances (R8.7–R8.9) and costs (section 13) are **not stored**. They are calculated whenever the run is viewed, from the run's copied standards and the ledger, so they can never be stale.

### Variance

- **R8.7** Consumption variance per item, where actual = used + wasted:

```
consumption variance % = (actual − standard) ÷ standard × 100
```

- **R8.8** Yield variance:

```
yield variance % = (good output − expected output) ÷ expected output × 100
```

- **R8.9** A variance is **flagged** when |variance %| **> 3.00%**. The tolerance is configurable and stored in `company.variance_tolerance_pct` (default 3.00, never negative). Flags appear on the run, in the production report and on the owner dashboard.
- **R8.10** **Run numbers:** `RUN/<FY>/<NNNN>`, for example `RUN/2026-27/0001`, from the same per-financial-year counter table and `DocumentNumberService` as invoices and credit notes (R10.2). The counter is locked and incremented in the transaction that creates the run, so a failed creation does not use up a number. Each financial year starts again at 0001. Runs are not tax documents, so the numbering is not a legal requirement here, but it comes free with the shared service.

---

## 9. Sales orders

### Status flow

```
SUBMITTED ──approve──▶ APPROVED ──invoice──▶ INVOICED ──dispatch──▶ DISPATCHED
    │
    ├──reject (reason)──▶ REJECTED
    └──(insufficient stock)──▶ stays SUBMITTED, shortage shown       SUBMITTED or APPROVED ──cancel──▶ CANCELLED
```

- **R9.1** An order has one customer, the salesman, a date and at least one line. Quantity is in whole packets (or cartons converted per R3.2). A product appears on one line only.
- **R9.2** **Price snapshot:** the unit price is copied from the price list when the order is created.
- **R9.3** Before approval the Admin may edit quantities and prices. The edit replaces the order's lines (at least one; quantity in whole packets, rate per packet above 0). Every edit is audit-logged with old and new values.
- **R9.4** **Stock check before approval.** The approval screen shows, per line:

```
available = finished-goods balance − quantity on APPROVED/INVOICED orders not yet dispatched
shortage  = max(0, ordered − available)
```

- **R9.5** An order cannot be approved while any line has a shortage. The Admin can (a) keep it pending until production covers it, (b) reduce the quantity to what is available, or (c) reject it. The shortage and the batches needed (`ceil(shortage ÷ expected yield)`) are shown.
- **R9.6** Approval reserves stock (it counts in R9.4) but does **not** move it. Stock moves only at dispatch.
- **R9.7** **Offline orders:** each order carries a client-generated UUID. Re-sending the same UUID after a network drop returns the existing order (HTTP 200; a new order is 201) instead of creating a duplicate. A UUID already used for another customer, or by an order the caller cannot see, is refused (409). The customer row is locked first, so two simultaneous re-sends also create one order.
- **R9.8** **Order numbers:** `SO/<FY>/<NNNN>` from the same `DocumentNumberService` (R10.2), for the financial year of the order date.
- **R9.9** **Status changes:** approve and reject (reason required) apply to SUBMITTED orders only. Cancel applies to SUBMITTED or APPROVED orders and releases any reservation. From INVOICED on, an order is cancelled only by cancelling its invoice (R10.6). Each change locks the order row.
- **R9.10** **Partial approval:** the approved quantity of a line is a whole number of packets, above 0 and not more than ordered; it defaults to the ordered quantity. The invoice and dispatch use the approved quantity. The rest is not back-ordered. To drop a line completely, edit the order before approving (R9.3). Approval locks the finished-goods item rows (R6.7) and only then reads stock and reservations.

---

## 10. GST invoice

- **R10.1** An invoice is raised from an APPROVED order and covers the full order.
- **R10.2** **Numbering:** `INV/<FY>/<NNNN>`, e.g. `INV/2026-27/0001` (16 characters, within the GST limit). The financial year runs 1 April – 31 March. Numbers are **consecutive with no gaps** within a year, issued from a counter row locked in the same transaction (not a database sequence, which can skip numbers on rollback).
- **R10.3** **Place of supply** is the customer's state:
  - Customer in **Tamil Nadu (33)** = factory state → **CGST + SGST**, each at half the GST rate.
  - Customer in any other state → **IGST** at the full rate.
- **R10.4** Per line:

```
taxable = qty × unit price
CGST    = round(taxable × rate/2 %, 2)    intra-state
SGST    = round(taxable × rate/2 %, 2)    intra-state
IGST    = round(taxable × rate %, 2)      inter-state
total   = taxable + CGST + SGST + IGST
```

- **R10.5** The invoice **keeps its own copy of everything it prints**, taken at posting: the seller's name, GSTIN, state code and address (`invoice.seller_*`), the buyer's name, GSTIN (if registered) and address (`invoice.buyer_*`), the place of supply, and per line the item name, HSN, quantity, rate, GST rate and tax amounts. The PDF reads only the invoice and its lines (plus the credit-note number once cancelled), never the company, customer or item master, so a later rename or address change does not alter a reprint. It shows both GSTINs, place of supply, tax breakup and the amount in words (Indian system: lakh, crore). It can be downloaded as a PDF. A share link is planned with the login and frontend steps.
- **R10.6** A posted invoice is **never edited or deleted**. It is cancelled with a **credit note** (`CN/<FY>/<NNNN>`) that references it, needs a reason, and repeats its taxable value, CGST, SGST, IGST and total. The invoice **and its order** become CANCELLED, even if the goods had been dispatched; billing the customer again needs a new order. The credit note date defaults to today and cannot be before the invoice date or the dispatch date. An invoice with a POSTED payment cannot be cancelled until that payment is reversed.
- **R10.7** **Returned goods:** if the invoice had been dispatched, the credit note posts one `RETURN` per dispatched item for the full quantity, valued at **exactly the value of the `SALE` movement** (so its unit cost matches the dispatch, and dispatch plus return add up to ₹0.00). Credit notes are always for the full invoice. A credit note total must be above 0, so order rates must be above 0.

---

## 11. Dispatch

- **R11.1** Dispatch is recorded against an INVOICED order and posts one `SALE` movement per item for the invoiced quantity.
- **R11.2** Dispatch is the **only** event that reduces finished-goods stock. Invoicing does not touch stock, so stock is never reduced twice.
- **R11.3** Stock is re-checked under lock at dispatch (R6.6, R6.7). If it is insufficient, dispatch is refused.
- **R11.4** This version dispatches the full invoiced quantity. Dispatch is stored as its own record so that partial dispatch can be added later. The order becomes DISPATCHED; a second dispatch is refused (409).
- **R11.5** **Dispatch numbers:** `DC/<FY>/<NNNN>` from `DocumentNumberService`. The dispatch date cannot be before the invoice date.

---

## 12. Payments and receivables

- **R12.1** Each payment is a separate record: customer, amount, date, mode (Cash / UPI / Bank transfer / Cheque), reference, and its **allocations** to one or more invoices.
- **R12.2** Each allocation is > 0 (at most 2 decimals), to a POSTED invoice of the same customer, and ≤ that invoice's outstanding. The invoice date must be on or before the date the payment was received (`received_on`; the same day is allowed): money received earlier would be an advance (A16). Overpayment, payment against a cancelled invoice and an invoice dated after the payment are rejected (HTTP 400). The invoices are locked before outstanding is read, so two payments cannot pay the same amount twice.
- **R12.3** Outstanding and payment status are **derived**, never stored as an editable number:

```
outstanding = invoice total − Σ allocations of POSTED payments
status      = UNPAID | PARTIALLY_PAID | PAID
```

- **R12.4** Customer outstanding = Σ invoice outstanding. Ageing is by days since invoice date: **0–30, 31–60, 61–90, 90+**.
- **R12.5** A wrong payment is never deleted. Reversing it sets `payment.status` to `REVERSED` together with `reversed_at` and `reversed_by` (enforced by `payment_reversed_state` and `payment_reversed_by_with_at`). Its allocations stay on record, but only allocations of `POSTED` payments count: the `invoice_outstanding` view ignores the rest, so outstanding and payment status (R12.3) recover automatically. *(Planned: the reversal service is not built yet. Until it is, an invoice with a payment cannot be cancelled, R10.6.)*
- **R12.6** The allocations of a payment add up **exactly** to its amount, and an invoice appears at most once. Advances and unallocated money are not supported (A16).

---

## 13. Costing

- **R13.1** **Material cost** of a run = Σ over raw materials of (used + wasted) × average cost at posting.
- **R13.2** **Packaging cost** = Σ over packaging items of (used + damaged) × average cost at posting. Damaged packaging is a real cost of the run, so it is included.
- **R13.3** **Overheads** (labour, gas/power, others) are entered **against the run** when known, as in the benchmark. They can be added to a DRAFT or a POSTED run (benchmark step 8 adds them after posting), but not to a cancelled or reversed one. They appear in the costing report only, never in stock value (R13.6).
- **R13.4** **Overhead allocation rule (monthly overheads):** overheads entered for a month instead of a run are allocated to that month's runs **in proportion to good output in kg** (packets × pack weight). Heavier output uses more labour and fryer time, and the allocation stays fair when pack sizes differ.
- **R13.5**

```
total run cost   = material cost + packaging cost + overheads
cost per packet  = total run cost ÷ good output
margin per packet = selling price (before GST) − cost per packet
margin %          = margin ÷ selling price × 100
```

Cost per packet is kept at 4 decimals (5,855 ÷ 188 = **31.1436**). The margin is calculated from that full-precision cost and is rounded **only for display** (R4.4): 50.00 − 31.1436 = 18.8564, shown as ₹18.86, which is **37.71 %**. (Dividing the *rounded* 31.14 instead would give 18.86 / 37.72 %, which is not what the system reports.)

- **R13.6** **Valuation of finished goods.** `PRODUCTION_OUTPUT` enters stock at **material cost + packaging cost** of the run (the total value of its ISSUE movements, R13.1 and R13.2), so `unit_cost` = that total ÷ good output. **Overheads are not part of stock value.** In the benchmark they are entered after posting (step 8), and stock movements are never changed afterwards (R6.3). Overheads are included in the costing report only (R13.5). Trade-off: AS 2 would also include conversion costs such as labour and power in the cost of inventory. This version keeps them out of stock value as a deliberate simplification, so dispatches (and any gross-margin figure based on stock cost) use a lower unit cost than the full cost per packet in the report. The costing report is calculated on request and not stored, so it always reflects overheads added later.

---

## 14. Audit trail

- **R14.1** Every create, edit, approve, reject, post, cancel, reverse, dispatch, adjust and payment action writes an `audit_log` record: user, action, entity, entity id, timestamp and old/new values (JSON). Allowed actions: CREATE, UPDATE, APPROVE, REJECT, CANCEL, POST, REVERSE, DISPATCH, PAYMENT, LOGIN, LOGIN_FAILED. (The user's role is looked up from `app_user`, not copied into the row.)
- **R14.2** Edits to orders, invoices and production runs are always logged with user and time.
- **R14.3** Audit records are append-only and cannot be changed or deleted through the application.
- **R14.4** Successful and failed logins are logged. `LOGIN` records the user (`user_id`, and the user as the entity). **Every** `LOGIN_FAILED`, including a wrong password for a real username and an inactive user, stores only the attempted `username`: `user_id` and `entity_id` are NULL (both columns are nullable), because a failed attempt does not show who was typing. The row is committed although the request fails. A request rejected as invalid input (400) is not a login attempt and is not logged.

---

## 15. Reports

Every report is `GET /api/reports/{report}?from=&to=&format=json|csv|xlsx|pdf` (roles in R2). Reports only read: they never change data and need no schema of their own.

- **R15.1** **Period:** `from` and `to` are dates, both **inclusive**, in factory time (IST). A missing one defaults to the first or last day of the **current month** by the business clock (A17). `from` after `to`, an unknown format or a bad date is rejected (400). Downloads are named `<report>_<from>_<to>.<ext>` (the stock ledger adds the item code), for example `sales-register_2026-11-01_2026-11-30.xlsx`.
- **R15.2** **Formats.** Every report is a title, a period and one or more tables; each column has a type (text, date, quantity, rate, money, percentage, count) that decides how it is written:
  - **JSON:** the tables as they are; the default.
  - **CSV:** UTF-8 with a byte-order mark (so Excel reads it as UTF-8), plain numbers without grouping or ₹, ISO dates, the tables one after another. A text cell that starts with `=`, `+`, `-`, `@`, a tab or a carriage return gets a leading `'`, so a spreadsheet never runs it as a formula.
  - **Excel (.xlsx):** one sheet per table, real numeric and date cells with number formats, bold frozen header, bold totals row.
  - **PDF:** landscape A4, Indian digit grouping (1,00,000.00), company name, report title, period, generated at / by, page numbers.

| Report (`{report}`) | Contents |
|---|---|
| Sales register (`sales-register`) | One row per invoice (no, date, customer, GSTIN, place of supply, taxable, CGST, SGST, IGST, total, status) and per credit note (negative); then customer-wise and product-wise summaries |
| GST summary (`gst-summary`) | GSTR-1 B2B, B2CS, CDNR and HSN summary, and GSTR-3B 3.1(a) (R15.4) |
| Purchase register (`purchase-register`) | One row per bill line: date, bill no, supplier, item, quantity, unit, rate, amount, status. Reversed purchases are listed but not totalled |
| Stock summary (`stock-summary`) | Per item, grouped raw / packaging / finished goods: opening, receipts, issues/sales, adjustments, closing, closing value, average cost (R15.5) |
| Stock ledger (`stock-ledger?itemCode=`) | Every movement of one item with running balance and its source document (bill no and supplier, run no, dispatch and invoice no, credit note no) |
| Production report (`production-report`) | One row per POSTED run (expected vs good output, yield variance and flag, material, packaging, overheads, cost per packet), then per run: standard, actual, scrap, variance %, flag and value per item |
| Payments and collections (`collections`) | POSTED payments received in the period, with the invoices each settled. Reversed payments are left out |
| Outstanding with ageing (`outstanding`) | As of `to` (R15.6): per customer the total and the 0–30 / 31–60 / 61–90 / 90+ day buckets (R12.4), then per invoice |
| My sales (`my-sales`, salesman) | The salesman's own customers: orders dated in the period, invoiced value (net of credit notes), outstanding as of `to` |

- **R15.3** **Credit notes in sales and GST:** an invoice counts in the period of its **invoice date** at its original amounts, even if it is cancelled later (its status shows CANCELLED). Its credit note counts **negative** in the period of the **credit note date**. A credit note in the same month therefore nets the sale to zero; one in a later month reduces that later month and leaves the earlier one as it was reported. Product-wise figures reverse the invoice's lines (credit notes always cover the full invoice, R10.6).
- **R15.4** **GSTR mapping** (from the invoice's own copied details, R10.5):
  - **B2B** (GSTR-1 table 4): invoices to customers with a GSTIN, one row per invoice and rate. Cancelled invoices stay, because their credit note is reported separately.
  - **B2CS** (table 7): unregistered customers by place of supply and rate, **net** of their credit notes.
  - **CDNR** (table 9B): credit notes to registered customers, note type C, **positive** amounts as on the note.
  - **HSN summary** (table 12): by B2B / B2C, HSN, description, UQC, rate: total quantity, value, taxable, IGST, CGST, SGST, net of credit notes. The UQC comes from the item's base unit (packet → PAC, kg → KGS, litre → LTR, piece → PCS).
  - **GSTR-3B 3.1(a):** outward taxable supplies = B2B + B2CS − credit notes.
  - **Not covered (known limits):** B2CL (inter-state invoices to unregistered buyers above the large-invoice limit) and CDNUR (credit notes on those). Unregistered sales are all reported as B2CS. Nil-rated, exempt and export supplies do not occur in this business.
- **R15.5** **Opening balance rule (stock summary and stock ledger):** opening = all movements before `from` **plus every OPENING movement whatever its date** (the seeded opening stock is dated 1 November 00:00 and must not appear as a November receipt). Receipts = RECEIPT + PRODUCTION_OUTPUT + RETURN; issues/sales = ISSUE + SALE; adjustments = ADJUSTMENT + REVERSAL. For every item the report checks opening + receipts − issues ± adjustments = closing, in quantity and in value (R6.4), and refuses to produce a report that does not balance. The stock ledger starts from the same opening balance, so the two always agree.
- **R15.6** **Outstanding as of a date:** invoices dated on or before `to`, less those cancelled by a credit note dated on or before `to`, less allocations of payments received on or before `to` that had not been reversed by the end of that day. For today this equals the `invoice_outstanding` view. Ageing = days from invoice date to `to`.
- **R15.7** **Owner dashboard** (`GET /api/dashboard`, JSON): today's orders by status; stock per item with a low-stock flag when a reorder level is set and stock is below it; variance alerts (flagged items and yield) of the runs posted in the last 30 days; cost per packet of the latest posted run of each product (overheads included); total outstanding and the top 5 customers by outstanding. "Today" is the business date (A17).

---

## 16. Benchmark scenario — expected results

### Opening position

| Item | Opening | Rate (₹) | Value (₹) |
|---|---:|---:|---:|
| Flour | 100 kg | 42.00 | 4,200.00 |
| Cooking oil | 30 L | 140.00 | 4,200.00 |
| Salt and spice mix | 5 kg | 200.00 | 1,000.00 |
| 200 g pouch | 500 pcs | 2.50 | 1,250.00 |
| Carton | 30 pcs | 15.00 | 450.00 |
| Murukku 200 g (FG) | 50 packets | 30.00 (A14) | 1,500.00 |

### Step-by-step

| Step | Event | Stock effect |
|---|---|---|
| 1 | Purchase 60 kg flour @ ₹45 | Flour 100 → **160 kg**, average ₹43.125 |
| 2–3 | Production run posted: flour 40 kg, oil 10.5 L, spice 1.2 kg, pouches 188 used + 6 damaged, cartons 9, good output 188 | Flour **120 kg**, oil **19.5 L**, spice **3.8 kg**, pouches **306**, cartons **21**, FG 50 → **238** |
| 4–5 | Retailer A (TN), 150 packets: approved → invoice `INV/2026-27/0001` (CGST + SGST) → dispatched | FG 238 → **88** |
| 6 | Distributor B (Karnataka), 120 packets | Available 88 → **shortage 32 flagged**, approval blocked (R9.5). 1 batch needed. Order stays SUBMITTED |
| 7 | Payment ₹4,000 from Retailer A | Outstanding **₹3,875.00**, PARTIALLY_PAID |
| 8 | Labour ₹1,200 + gas/power ₹600 added to the run | Cost per packet calculated (below) |

### Production variance (tolerance 3%)

| Item | Standard | Actual (used + wasted) | Variance | Flag |
|---|---:|---:|---:|:-:|
| Flour | 40.000 kg | 40.000 kg | 0.00% | |
| Cooking oil | 10.000 L | 10.500 L | **+5.00%** | ⚑ |
| Spice mix | 1.200 kg | 1.200 kg | 0.00% | |
| Pouch (output-driven) | 188 | 194 (188 + 6 damaged) | **+3.19%** | ⚑ |
| Carton (output-driven) | 9.4 | 9 | −4.26% | — see note |
| **Yield** | 200 packets | 188 packets | **−6.00%** | ⚑ |

*Carton note:* 188 packets fill 9 full cartons (180) with 8 loose packets. Carton standard for flagging is therefore taken as **whole cartons = floor(good output ÷ 20) = 9**, giving 0.00%. Fractional cartons are not a real consumption.

*Pouch note:* the problem statement's expected flags are yield and oil. This system also flags the 6 damaged pouches (+3.19% over what 188 packets needed), because hidden packaging wastage is one of the problems the client described.

### Invoices

| | Retailer A (Tamil Nadu) | Distributor B (Karnataka), if invoiced |
|---|---:|---:|
| Quantity | 150 | 120 requested / 88 available |
| Taxable | ₹7,500.00 | ₹6,000.00 / ₹4,400.00 |
| CGST 2.5% | ₹187.50 | — |
| SGST 2.5% | ₹187.50 | — |
| IGST 5% | — | ₹300.00 / ₹220.00 |
| **Total** | **₹7,875.00** | ₹6,300.00 / ₹4,620.00 |

### Cost per packet (weighted average, overheads on the run)

| Component | Calculation | ₹ |
|---|---|---:|
| Flour | 40 kg × 43.125 | 1,725.00 |
| Cooking oil | 10.5 L × 140 | 1,470.00 |
| Spice mix | 1.2 kg × 200 | 240.00 |
| **Material cost** | | **3,435.00** |
| Pouches | 194 × 2.50 | 485.00 |
| Cartons | 9 × 15 | 135.00 |
| **Packaging cost** | | **620.00** |
| Labour | | 1,200.00 |
| Gas / power | | 600.00 |
| **Total run cost** | | **5,855.00** |
| **Cost per packet** | 5,855 ÷ 188 | **31.1436** (shown 31.14) |
| Selling price (before GST) | | 50.00 |
| **Margin per packet** | 50.00 − 31.1436 | **18.8564** (shown 18.86), **37.71%** |

Stock value is different from this full cost: finished goods enter stock at material + packaging only, (3,435.00 + 620.00) ÷ 188 = **₹21.5691** per packet (R13.6). FG stock after the run is worth 1,500.00 + 4,055.00 = **₹5,555.00** for 238 packets (average ≈ ₹23.34).

For comparison, with the other valuation methods the flour cost changes: FIFO (40 kg from opening @ ₹42) gives ₹30.90 per packet, and last price (@ ₹45) gives ₹31.54 per packet.

### Closing check

| Check | Problem statement | This system |
|---|---|---|
| Flour closing | 120 kg | 120 kg ✔ |
| Oil closing | 19.5 L | 19.5 L ✔ |
| Pouches closing | 306 | 306 ✔ |
| FG after run | 238 | 238 ✔ |
| FG after Retailer A dispatch | 88 | 88 ✔ |
| Yield variance | −6%, flagged | −6.00%, flagged ✔ |
| Oil variance | +5%, flagged | +5.00%, flagged ✔ |
| Retailer A invoice | ₹7,875 | ₹7,875.00 ✔ |
| Retailer A outstanding | ₹3,875 | ₹3,875.00 ✔ |
| Distributor B | Shortage 32, IGST if invoiced | Shortage 32 flagged, IGST ✔ |
| Cost per packet | Own figure + method | ₹31.1436, shown ₹31.14 (WAC; overheads on run) |
| Additional | — | Pouch wastage +3.19% flagged (explained above) |

Each row is an automated test in `src/test/java/com/ordertoaudit/benchmark`.

---

## 17. Assumptions

| # | Assumption | Reason |
|---|---|---|
| A1 | Factory GSTIN is a dummy value `33AAACO1234A1Z5` (Tamil Nadu). Retailer A is in Tamil Nadu (33), Distributor B in Karnataka (29) with dummy GSTINs. | Problem allows invented data; state codes drive CGST/SGST vs IGST. |
| A2 | HSN for murukku: **2106 90 99** (namkeen / savoury snacks), GST 5% as instructed. | HSN must appear on the invoice. The rate is fixed by the problem statement. |
| A3 | Purchase rates are **before GST**. Input tax credit is not tracked in this version. | Problem focuses on outward GST. Cost uses net rates. |
| A4 | Benchmark run = **1 batch**. All transactions fall in **November 2026**, FY 2026-27. | Matches the scenario. Invoice series starts at 0001. |
| A5 | Oil is issued per batch (input-driven). The 0.5 L over standard is consumption, not separate wastage. | Problem states "10.5 L oil (0.5 L over standard)". |
| A6 | Pouches: 188 used for good packets + 6 damaged = **194 consumed**. Cartons: **9 consumed**, 0 damaged. | Matches the expected pouch closing of 306 and "9 cartons used". |
| A7 | No flour or spice wastage was reported, so all issued quantity is treated as consumed. | Only pouch damage is given. |
| A8 | The 12 missing packets (200 expected − 188 good) are yield loss in processing. They are not stocked as separate scrap. | Problem gives only good output. Yield variance captures the loss. |
| A9 | Approved but undispatched orders reserve stock for the availability check. | Prevents two approvals promising the same packets. |
| A10 | Distributor B's order is not split automatically. The Admin decides whether to wait, reduce to 88 or reject. | Partial fulfilment is a business decision, so the system shows the shortage and options. |
| A11 | Overheads for the benchmark are entered on the run. Monthly overheads use the kg-of-output rule (R13.4). | Problem gives per-run figures and asks for a stated rule. |
| A12 | No round-off to the nearest rupee on invoices. | Benchmark totals are already whole paise. Avoids an extra adjustment line. |
| A13 | Batch numbers and expiry dates are a stretch goal and not part of this rule set. | Must-haves come first (problem section 6). |
| A14 | Opening finished-goods stock (50 packets) is valued at **₹30.00 per packet**. The 60 kg flour purchase is not part of the seed: the benchmark test posts it. | The brief gives no cost for opening FG, but a cost is needed so dispatches are valued (R7.4). 30.00 is a round figure of the same order as the run's full cost (₹31.14). After the run, FG average cost = (50 × 30 + 4,055.00) ÷ 238 = 5,555.00 ÷ 238 ≈ ₹23.34, because new output enters at material + packaging cost only (R13.6). Opening value does not affect the benchmark's quantity, invoice, outstanding or cost-per-packet results. |
| A15 | Seeded test logins, **dev and test profiles only**, for `POST /api/auth/login`: `admin` / `Admin@123` (Admin), `production` / `Prod@123` (Production), `salesman_a` / `Sales@123` (Salesman, Retailer A), `salesman_b` / `Sales@123` (Salesman, Distributor B), `auditor` / `Audit@123` (Auditor), `owner` / `Owner@123` (Owner). Stored as BCrypt hashes (cost 10). | Lets reviewers log in as each role. These are public dummy credentials and must never be used in production; no other profile loads them (R2.8). |
| A16 | No customer advances: every payment is allocated in full to invoices when it is recorded (R12.6). | The benchmark records a payment against an invoice. Advances would need their own balance and adjustment rules. |
| A17 | "Today" for business dates (order date; the default date of an invoice, dispatch, payment or credit note when none is given; the default report period; the dashboard; `businessDate` in the login and `/api/auth/me` responses, which screens use as their default date) comes from a business clock: the real date in IST, or a fixed date (10:00 IST) set with `OTA_BUSINESS_DATE` (setting `ota.clock.fixed-date`). Tests fix it at 28 November 2026. Security timestamps always use the real time. | Lets a demo and the tests run "in" the benchmark month on any real date, so results do not depend on when they are run. A warning is logged at startup whenever the date is fixed. |
