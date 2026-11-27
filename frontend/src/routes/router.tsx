import { createBrowserRouter } from 'react-router'

import { RequireRole } from '../auth/RequireRole'
import { OrdersPage } from '../features/admin/OrdersPage'
import { ReportsPlaceholder } from '../features/auditor/ReportsPlaceholder'
import { LoginPage } from '../features/login/LoginPage'
import { RunsPlaceholder } from '../features/production/RunsPlaceholder'
import { MyOrdersPage } from '../features/salesman/MyOrdersPage'
import { NewOrderPage } from '../features/salesman/NewOrderPage'
import { SalesmanLayout } from '../features/salesman/SalesmanLayout'
import { DesktopLayout } from './DesktopLayout'
import { Home, NotFound } from './SimplePages'

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
      {
        path: '/admin/orders/:orderId?',
        element: (
          <RequireRole roles={['ADMIN']}>
            <OrdersPage />
          </RequireRole>
        ),
      },
      {
        path: '/production/runs',
        element: (
          <RequireRole roles={['PRODUCTION']}>
            <RunsPlaceholder />
          </RequireRole>
        ),
      },
      {
        path: '/reports',
        element: (
          <RequireRole roles={['AUDITOR', 'OWNER']}>
            <ReportsPlaceholder />
          </RequireRole>
        ),
      },
    ],
  },
  { path: '*', element: <NotFound /> },
]

export const router = createBrowserRouter(routes)
