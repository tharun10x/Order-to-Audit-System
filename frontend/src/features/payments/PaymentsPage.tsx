import { Link } from 'react-router'

import { usePayments } from '../../api/billing'
import { useSession } from '../../auth/authContext'
import { can } from '../../auth/permissions'
import { LinkButton, PageHeader, StatusWord } from '../../components/Layout'
import { EmptyState, ErrorBox } from '../../components/Messages'
import { Panel } from '../../components/Panel'
import { Table, Td, Th } from '../../components/Table'
import { formatDate, formatMoney } from '../../lib/format'
import { PAYMENT_MODE, PAYMENT_RECORD_STATUS } from '../../lib/labels'

/** R12: payments received, newest first, with the invoices each one settled. */
export function PaymentsPage() {
  const session = useSession()
  const payments = usePayments()
  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Payments"
        action={
          <div className="flex gap-3">
            <LinkButton to="/outstanding">Customer outstanding</LinkButton>
            {can.manageSales(session.user.role) ? <LinkButton variant="primary" to="/payments/new">Record payment</LinkButton> : null}
          </div>
        }
      />
      <Panel>
        {payments.error ? <ErrorBox error={payments.error} /> : null}
        {payments.data && payments.data.length === 0 ? (
          <EmptyState>No payments yet. Payments recorded against invoices appear here.</EmptyState>
        ) : null}
        {payments.data && payments.data.length > 0 ? (
          <Table label="Payments">
            <thead>
              <tr>
                <Th>Received on</Th>
                <Th>Customer</Th>
                <Th>Mode</Th>
                <Th>Reference</Th>
                <Th numeric>Amount (₹)</Th>
                <Th>Settled</Th>
                <Th>Status</Th>
              </tr>
            </thead>
            <tbody>
              {payments.data.map((payment) => (
                <tr key={payment.id}>
                  <Td className="whitespace-nowrap">{formatDate(payment.receivedOn)}</Td>
                  <Td>{payment.customerName}</Td>
                  <Td>{PAYMENT_MODE[payment.mode]}</Td>
                  <Td>{payment.reference ?? ''}</Td>
                  <Td numeric>{formatMoney(payment.amount)}</Td>
                  <Td>
                    {payment.allocations.map((allocation) => (
                      <span key={allocation.invoiceId} className="num block whitespace-nowrap">
                        <Link to={`/invoices/${allocation.invoiceId}`} className="text-primary hover:underline">
                          {allocation.invoiceNo}
                        </Link>
                        {`: ${formatMoney(allocation.amount)}`}
                      </span>
                    ))}
                  </Td>
                  <Td><StatusWord look={PAYMENT_RECORD_STATUS[payment.status]} /></Td>
                </tr>
              ))}
            </tbody>
          </Table>
        ) : null}
      </Panel>
    </div>
  )
}
