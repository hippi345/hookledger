import { useCallback, useEffect, useState } from 'react';

const TABS = [
  { id: 'list', label: 'Invoices' },
  { id: 'detail', label: 'Invoice detail' },
  { id: 'aging', label: 'Aging' },
  { id: 'statement', label: 'Customer statement' },
];

async function fetchJson(path) {
  const response = await fetch(path);
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Request failed (${response.status})`);
  }
  return response.json();
}

function formatMinor(amount, currency) {
  return `${amount} ${currency?.toUpperCase() ?? ''} (minor units)`;
}

function InvoiceListPanel({ onSelectInvoice }) {
  const [customer, setCustomer] = useState('');
  const [status, setStatus] = useState('');
  const [currency, setCurrency] = useState('');
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const params = new URLSearchParams({ limit: '50', offset: '0' });
      if (customer.trim()) params.set('customer', customer.trim());
      if (status) params.set('status', status);
      if (currency.trim()) params.set('currency', currency.trim());
      const json = await fetchJson(`/api/invoices?${params}`);
      setData(json);
    } catch (e) {
      setError(e.message);
      setData(null);
    } finally {
      setLoading(false);
    }
  }, [customer, status, currency]);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <div>
      <div className="filters">
        <label>
          Customer
          <input value={customer} onChange={(e) => setCustomer(e.target.value)} placeholder="Exact name" />
        </label>
        <label>
          Status
          <select value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="">Any</option>
            <option value="open">open</option>
            <option value="paid">paid</option>
            <option value="void">void</option>
          </select>
        </label>
        <label>
          Currency
          <input value={currency} onChange={(e) => setCurrency(e.target.value)} placeholder="usd" />
        </label>
        <button type="button" className="action" onClick={load} disabled={loading}>
          Refresh
        </button>
      </div>
      {error && <p className="error">{error}</p>}
      {data && (
        <>
          <p className="muted">
            Showing {data.invoices.length} of {data.total} (limit {data.limit}, offset {data.offset})
          </p>
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>Customer</th>
                <th>Due</th>
                <th>Status</th>
                <th>Total (minor)</th>
                <th>Currency</th>
              </tr>
            </thead>
            <tbody>
              {data.invoices.map((inv) => (
                <tr key={inv.id}>
                  <td>
                    <span className="linkish" onClick={() => onSelectInvoice(inv.id)} role="button" tabIndex={0}>
                      {inv.id.slice(0, 8)}…
                    </span>
                  </td>
                  <td>{inv.customerName}</td>
                  <td>{inv.dueDate}</td>
                  <td>{inv.status}</td>
                  <td>{inv.totalAmountMinor}</td>
                  <td>{inv.currency}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </div>
  );
}

function InvoiceDetailPanel({ invoiceId, setInvoiceId }) {
  const [id, setId] = useState(invoiceId || '');
  const [detail, setDetail] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (invoiceId) setId(invoiceId);
  }, [invoiceId]);

  const load = async () => {
    if (!id.trim()) return;
    setLoading(true);
    setError('');
    try {
      const json = await fetchJson(`/api/invoices/${encodeURIComponent(id.trim())}`);
      setDetail(json);
      setInvoiceId(id.trim());
    } catch (e) {
      setError(e.message);
      setDetail(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (invoiceId) load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div>
      <div className="form-row">
        <label>
          Invoice ID
          <input value={id} onChange={(e) => setId(e.target.value)} />
        </label>
        <button type="button" className="action" onClick={load} disabled={loading || !id.trim()}>
          Load
        </button>
      </div>
      {error && <p className="error">{error}</p>}
      {detail && (
        <>
          <div className="grid-two">
            <div className="card">
              <strong>Customer</strong>
              {detail.invoice.customerName}
            </div>
            <div className="card">
              <strong>Open balance</strong>
              {formatMinor(detail.openBalanceMinor, detail.invoice.currency)}
            </div>
            <div className="card">
              <strong>Status</strong>
              {detail.invoice.status}
            </div>
            <div className="card">
              <strong>Total</strong>
              {formatMinor(detail.invoice.totalAmountMinor, detail.invoice.currency)}
            </div>
          </div>
          <h3>Credits</h3>
          {detail.credits.length === 0 ? (
            <p className="muted">No credits applied.</p>
          ) : (
            <table>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Amount (minor)</th>
                  <th>Created</th>
                </tr>
              </thead>
              <tbody>
                {detail.credits.map((c) => (
                  <tr key={c.id}>
                    <td>{c.id}</td>
                    <td>{c.amountMinor}</td>
                    <td>{c.createdAt}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          <h3>Payments</h3>
          {detail.payments.length === 0 ? (
            <p className="muted">No payments recorded.</p>
          ) : (
            <table>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Amount (minor)</th>
                  <th>Ledger event</th>
                  <th>Created</th>
                </tr>
              </thead>
              <tbody>
                {detail.payments.map((p) => (
                  <tr key={p.id}>
                    <td>{p.id}</td>
                    <td>{p.amountMinor}</td>
                    <td>{p.ledgerEventId}</td>
                    <td>{p.createdAt}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </>
      )}
    </div>
  );
}

function AgingPanel() {
  const [asOf, setAsOf] = useState('');
  const [report, setReport] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const load = async () => {
    setLoading(true);
    setError('');
    try {
      const params = asOf ? `?asOf=${encodeURIComponent(asOf)}` : '';
      setReport(await fetchJson(`/api/invoices/aging${params}`));
    } catch (e) {
      setError(e.message);
      setReport(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const bucketKeys = ['current', '30', '60', '90', 'over90'];

  return (
    <div>
      <div className="form-row">
        <label>
          As of (optional)
          <input type="date" value={asOf} onChange={(e) => setAsOf(e.target.value)} />
        </label>
        <button type="button" className="action" onClick={load} disabled={loading}>
          Refresh
        </button>
      </div>
      {error && <p className="error">{error}</p>}
      {report && (
        <>
          <p className="muted">As of {report.asOf}</p>
          {report.currencies.map((cur) => (
            <div key={cur.currency} style={{ marginBottom: '1.25rem' }}>
              <h3>{cur.currency.toUpperCase()} — grand total {cur.grandTotalAmountMinor} (minor)</h3>
              {bucketKeys.map((key) => {
                const bucket = cur[key];
                if (!bucket || bucket.invoices.length === 0) return null;
                return (
                  <div key={key}>
                    <h4>Bucket {key} (total {bucket.totalAmountMinor})</h4>
                    <table>
                      <thead>
                        <tr>
                          <th>Invoice</th>
                          <th>Customer</th>
                          <th>Due</th>
                          <th>Open (minor)</th>
                        </tr>
                      </thead>
                      <tbody>
                        {bucket.invoices.map((line) => (
                          <tr key={line.id}>
                            <td>{line.id.slice(0, 8)}…</td>
                            <td>{line.customer}</td>
                            <td>{line.dueDate}</td>
                            <td>{line.openAmountMinor}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                );
              })}
            </div>
          ))}
        </>
      )}
    </div>
  );
}

function StatementPanel() {
  const [customer, setCustomer] = useState('');
  const [currency, setCurrency] = useState('usd');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [statement, setStatement] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const load = async () => {
    if (!customer.trim() || !currency.trim() || !from || !to) return;
    setLoading(true);
    setError('');
    try {
      const path = `/api/customers/${encodeURIComponent(customer.trim())}/statement?currency=${encodeURIComponent(currency.trim())}&from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`;
      setStatement(await fetchJson(path));
    } catch (e) {
      setError(e.message);
      setStatement(null);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <div className="filters">
        <label>
          Customer
          <input value={customer} onChange={(e) => setCustomer(e.target.value)} />
        </label>
        <label>
          Currency
          <input value={currency} onChange={(e) => setCurrency(e.target.value)} />
        </label>
        <label>
          From
          <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
        </label>
        <label>
          To
          <input type="date" value={to} onChange={(e) => setTo(e.target.value)} />
        </label>
        <button type="button" className="action" onClick={load} disabled={loading}>
          Load statement
        </button>
      </div>
      {error && <p className="error">{error}</p>}
      {statement && (
        <>
          <div className="grid-two">
            <div className="card">
              <strong>Starting balance</strong>
              {statement.startingBalanceMinor} (minor)
            </div>
            <div className="card">
              <strong>Ending balance</strong>
              {statement.endingBalanceMinor} (minor)
            </div>
          </div>
          <table>
            <thead>
              <tr>
                <th>Date</th>
                <th>Kind</th>
                <th>ID</th>
                <th>Amount</th>
                <th>Running balance</th>
              </tr>
            </thead>
            <tbody>
              {statement.lines.map((line, idx) => (
                <tr key={`${line.id}-${idx}`}>
                  <td>{line.date}</td>
                  <td>{line.type}</td>
                  <td>{line.id}</td>
                  <td>{line.amountMinor}</td>
                  <td>{line.balanceMinor}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </div>
  );
}

export default function App() {
  const [tab, setTab] = useState('list');
  const [selectedInvoiceId, setSelectedInvoiceId] = useState('');

  const onSelectInvoice = (id) => {
    setSelectedInvoiceId(id);
    setTab('detail');
  };

  return (
    <div className="app" data-hookledger-page="invoice-desk">
      <header>
        <h1>Invoice Desk</h1>
        <p>Read-only view of invoices, aging, and customer statements (amounts in minor units).</p>
      </header>
      <nav className="tabs" aria-label="Sections">
        {TABS.map((t) => (
          <button
            key={t.id}
            type="button"
            className={tab === t.id ? 'active' : ''}
            onClick={() => setTab(t.id)}
          >
            {t.label}
          </button>
        ))}
      </nav>
      <section className="panel">
        {tab === 'list' && <InvoiceListPanel onSelectInvoice={onSelectInvoice} />}
        {tab === 'detail' && (
          <InvoiceDetailPanel invoiceId={selectedInvoiceId} setInvoiceId={setSelectedInvoiceId} />
        )}
        {tab === 'aging' && <AgingPanel />}
        {tab === 'statement' && <StatementPanel />}
      </section>
    </div>
  );
}
