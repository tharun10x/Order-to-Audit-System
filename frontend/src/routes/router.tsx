import type { ReactNode } from 'react'
import { createBrowserRouter } from 'react-router'

import type { Role } from '../api/types'
import { PAGE_ROLES } from '../auth/permissions'
import { RequireRole } from '../auth/RequireRole'
import { OrdersPage } from '../features/admin/OrdersPage'
import { ReportsPlaceholder } from '../features/auditor/ReportsPlaceholder'
import { InvoiceDetailPage } from '../features/billing/InvoiceDetailPage'
import { InvoicesPage } from '../features/billing/InvoicesPage'
import { LoginPage } from '../features/login/LoginPage'
import { CustomersPage } from '../features/masters/CustomersPage'
import { ItemsPage } from '../features/masters/ItemsPage'
import { MastersHome, MastersLayout } from '../features/masters/MastersLayout'
import { RecipesPage } from '../features/masters/RecipesPage'
import { SuppliersPage } from '../features/masters/SuppliersPage'
import { OutstandingPage } from '../features/payments/OutstandingPage'
import { PaymentsPage } from '../features/payments/PaymentsPage'
import { RecordPaymentPage } from '../features/payments/RecordPaymentPage'
import { NewRunPage } from '../features/production/NewRunPage'
import { RunDetailPage } from '../features/production/RunDetailPage'
import { RunsPage } from '../features/production/RunsPage'
import { PurchasesPage } from '../features/purchases/PurchasesPage'
import { RecordPurchasePage } from '../features/purchases/RecordPurchasePage'
import { MyOrdersPage } from '../features/salesman/MyOrdersPage'
import { NewOrderPage } from '../features/salesman/NewOrderPage'
import { SalesmanLayout } from '../features/salesman/SalesmanLayout'
import { StockLedgerPage } from '../features/stock/StockLedgerPage'
import { StockPage } from '../features/stock/StockPage'
import { DesktopLayout } from './DesktopLayout'
import { Home, NotFound, OldOrderLink } from './SimplePages'

/** A desktop page open to some roles (R2). */
function page(path: string, roles: Role[], element: ReactNode) {
  return { path, element: <RequireRole roles={roles}>{element}</RequireRole> }
}

export const routes = [
  { path: '/', element: <Home /> },
  { path: '/login', element: <LoginPage /> },
  {
    element: (
      <RequireRole roles={['SALESMAN']}>
        <SalesmanLayout />
      </RequireRole>
    ),
    children: [
      { path: '/sales/new', element: <NewOrderPage /> },
      { path: '/sales/orders', element: <MyOrdersPage /> },
    ],
  },
  {
    element: <DesktopLayout />,
    children: [
      page('/orders/:orderId?', PAGE_ROLES.orders, <OrdersPage />),
      { path: '/admin/orders/:orderId?', element: <OldOrderLink /> },
      page('/invoices', PAGE_ROLES.invoices, <InvoicesPage />),
      page('/invoices/:invoiceId', PAGE_ROLES.invoices, <InvoiceDetailPage />),
      page('/payments', PAGE_ROLES.payments, <PaymentsPage />),
      page('/payments/new', PAGE_ROLES.recordPayment, <RecordPaymentPage />),
      page('/outstanding', PAGE_ROLES.payments, <OutstandingPage />),
      page('/purchases', PAGE_ROLES.purchases, <PurchasesPage />),
      page('/purchases/new', PAGE_ROLES.recordPurchase, <RecordPurchasePage />),
      page('/production/runs', PAGE_ROLES.production, <RunsPage />),
      page('/production/runs/new', PAGE_ROLES.newRun, <NewRunPage />),
      page('/production/runs/:runId', PAGE_ROLES.production, <RunDetailPage />),
      page('/stock', PAGE_ROLES.stock, <StockPage />),
      page('/stock/ledger/:itemCode', PAGE_ROLES.stock, <StockLedgerPage />),
      page('/reports', PAGE_ROLES.reports, <ReportsPlaceholder />),
      {
        // Every role that can open at least one masters list; each page then checks its own roles
        element: (
          <RequireRole roles={PAGE_ROLES.masterItems}>
            <MastersLayout />
          </RequireRole>
        ),
        children: [
          page('/masters', PAGE_ROLES.masterItems, <MastersHome />),
          page('/masters/items/new', PAGE_ROLES.manageMasters, <ItemsPage creating />),
          page('/masters/items/:code?', PAGE_ROLES.masterItems, <ItemsPage />),
          page('/masters/recipes/:productCode/new', PAGE_ROLES.manageMasters, <RecipesPage creating />),
          page('/masters/recipes/:productCode?', PAGE_ROLES.masterRecipes, <RecipesPage />),
          page('/masters/customers/new', PAGE_ROLES.manageMasters, <CustomersPage creating />),
          page('/masters/customers/:customerId?', PAGE_ROLES.masterCustomers, <CustomersPage />),
          page('/masters/suppliers/new', PAGE_ROLES.manageMasters, <SuppliersPage creating />),
          page('/masters/suppliers/:supplierId?', PAGE_ROLES.masterSuppliers, <SuppliersPage />),
        ],
      },
    ],
  },
  { path: '*', element: <NotFound /> },
]

export const router = createBrowserRouter(routes)
