package com.ordertoaudit.security;

/**
 * The @PreAuthorize expressions, one per distinct set of roles in the R2 matrix, so each set is written once.
 * R2.1: every endpoint except login carries one of these (RoleMatrixTest fails if one does not).
 */
public final class Roles {

    /** Any logged-in user. */
    public static final String SIGNED_IN = "isAuthenticated()";

    public static final String ADMIN_ONLY = "hasRole('ADMIN')";

    /** R8: only the factory floor changes production runs. */
    public static final String PRODUCTION_ONLY = "hasRole('PRODUCTION')";

    /** Taking orders, and the customer and price lists needed for it. */
    public static final String ORDER_ENTRY = "hasAnyRole('SALESMAN', 'ADMIN')";

    /** A salesman sees only their own orders: the service filters them (R2.2). */
    public static final String ORDER_READ = "hasAnyRole('SALESMAN', 'ADMIN', 'AUDITOR', 'OWNER')";

    /** Invoices, payments and receivables. Auditor and Owner can only read (R2.3). */
    public static final String ACCOUNTS_READ = "hasAnyRole('ADMIN', 'AUDITOR', 'OWNER')";

    /** Raw-material receipts (R7). */
    public static final String PURCHASE_ENTRY = "hasAnyRole('ADMIN', 'PRODUCTION')";

    /** Purchases, production runs and stock, including the stock and production reports (F2). */
    public static final String OPERATIONS_READ = "hasAnyRole('ADMIN', 'PRODUCTION', 'AUDITOR', 'OWNER')";

    /** The accounts reports (R15): sales, GST, purchases, collections, outstanding. */
    public static final String REPORTS_READ = "hasAnyRole('ADMIN', 'AUDITOR', 'OWNER')";

    /** A salesman's own customer-wise sales (brief 4.1). */
    public static final String SALESMAN_ONLY = "hasRole('SALESMAN')";

    public static final String DASHBOARD = "hasAnyRole('OWNER', 'ADMIN')";

    private Roles() {
    }
}
