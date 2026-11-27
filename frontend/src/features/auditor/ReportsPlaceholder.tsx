import { Panel } from '../../components/Panel'

/** The auditor's and owner's start page until the report screens are built. */
export function ReportsPlaceholder() {
  return (
    <>
      <h1 className="mb-6 text-2xl font-semibold">Reports</h1>
      <Panel>
        <p className="text-sm">Report screens and downloads come in a later milestone.</p>
      </Panel>
    </>
  )
}
