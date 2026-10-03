import { useCallback, useEffect, useState } from 'react';

const TABS = [
  { id: 'list', label: 'Invoices' },
  { id: 'create', label: 'Create invoice' },
  { id: 'detail', label: 'Invoice detail' },
  { id: 'overdue', label: 'Overdue' },
  { id: 'schedule', label: 'Schedule generate' },
  { id: 'customer-pay', label: 'Customer payment' },
  { id: 'aging', label: 'Aging' },
  { id: 'statement', label: 'Customer statement' },
];

async function fetchJson(path, init) {
  const response = await fetch(path, init);
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Request failed (${response.status})`);
  }
  if (response.status === 204) return null;
  const contentType = response.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    return response.json();
  }
  return null;
}

async function postJson(path, body) {
  return fetchJson(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

async function postNoBody(path) {
  return fetchJson(path, { method: 'POST' });
}

function formatMinor(amount, currency) {
  return `${amount} ${currency?.toUpperCase() ?? ''} (minor units)`;
}

function PdfPreview({ title, buildUrl, disabled }) {
  const [blobUrl, setBlobUrl] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    return () => {
      if (blobUrl) URL.revokeObjectURL(blobUrl);
    };
  }, [blobUrl]);

  const load = async () => {
    const url = buildUrl();
    if (!url) return;
    setLoading(true);
    setError('');
    if (blobUrl) {
      URL.revokeObjectURL(blobUrl);
      setBlobUrl('');
    }
    try {
      const response = await fetch(url);
      if (!response.ok) {
        const text = await response.text();
        throw new Error(text || `PDF request failed (${response.status})`);
      }
      const blob = await response.blob();
      if (!blob.type.includes('pdf') && blob.size < 5) {
        throw new Error('Response was not a PDF');
      }
      setBlobUrl(URL.createObjectURL(blob));
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="pdf-block">
      <div className="form-row">
        <button type="button" className="action secondary" onClick={load} disabled={disabled || loading}>
          {loading ? 'Loading PDF…' : title}
        </button>
      </div>
      {error && <p className="error">{error}</p>}
      {blobUrl && (
        <object className="pdf-embed" data={blobUrl} type="application/pdf" title={title}>
          <p className="muted">PDF preview is not supported in this browser.</p>
        </object>
      )}
    </div>
  );
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

function CreateInvoicePanel({ onCreated }) {
  const [customerName, setCustomerName] = useState('Fable Harbor Supplies');
  const [customerAddress, setCustomerAddress] = useState('12 Sample Wharf\nHarborview, HV 00001');
  const [dueDate, setDueDate] = useState('2026-08-01');
  const [currency, setCurrency] = useState('usd');
  const [description, setDescription] = useState('Monthly service');
  const [amountMinor, setAmountMinor] = useState('5000');
  const [taxBps, setTaxBps] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);

  const submit = async () => {
    setLoading(true);
    setError('');
    setSuccess('');
    try {
      const lineItem = {
        description: description.trim(),
        amountMinor: Number(amountMinor),
        ...(taxBps.trim() ? { taxRateBasisPoints: Number(taxBps) } : {}),
      };
      const created = await postJson('/api/invoices', {
        customerName: customerName.trim(),
        customerAddress: customerAddress.trim() || null,
        dueDate,
        currency: currency.trim(),
        lineItems: [lineItem],
      });
      const detail = await fetchJson(`/api/invoices/${encodeURIComponent(created.id)}`);
      setSuccess(
        `Created invoice ${created.id.slice(0, 8)}… — open balance ${formatMinor(detail.openBalanceMinor, created.currency)}`,
      );
      onCreated(created.id);
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <p className="muted">Posts to POST /api/invoices (does not post to the ledger until payments or charges).</p>
      <div className="filters">
        <label>
          Customer name
          <input value={customerName} onChange={(e) => setCustomerName(e.target.value)} />
        </label>
        <label>
          Due date
          <input type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} />
        </label>
        <label>
          Currency
          <input value={currency} onChange={(e) => setCurrency(e.target.value)} />
        </label>
      </div>
      <label>
        Customer address (optional)
        <textarea
          rows={2}
          value={customerAddress}
          onChange={(e) => setCustomerAddress(e.target.value)}
          style={{ width: '100%', font: 'inherit' }}
        />
      </label>
      <h3>Line item</h3>
      <div className="filters">
        <label>
          Description
          <input value={description} onChange={(e) => setDescription(e.target.value)} />
        </label>
        <label>
          Amount (minor)
          <input value={amountMinor} onChange={(e) => setAmountMinor(e.target.value)} inputMode="numeric" />
        </label>
        <label>
          Tax rate (basis points, optional)
          <input value={taxBps} onChange={(e) => setTaxBps(e.target.value)} placeholder="e.g. 825" />
        </label>
        <button
          type="button"
          className="action"
          onClick={submit}
          disabled={loading || !customerName.trim() || !dueDate || !description.trim()}
        >
          Create invoice
        </button>
      </div>
      {error && <p className="error">{error}</p>}
      {success && <p className="success">{success}</p>}
    </div>
  );
}

function InvoiceDetailPanel({ invoiceId, setInvoiceId }) {
  const [id, setId] = useState(invoiceId || '');
  const [detail, setDetail] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [actionError, setActionError] = useState('');
  const [balanceNotice, setBalanceNotice] = useState('');
  const [actionBusy, setActionBusy] = useState(false);
  const [creditAmount, setCreditAmount] = useState('');
  const [paymentAmount, setPaymentAmount] = useState('');
  const [lateFeeMinor, setLateFeeMinor] = useState('');
  const [lateFeeAsOf, setLateFeeAsOf] = useState('');

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

  const reloadAfterAction = async (run) => {
    setActionBusy(true);
    setActionError('');
    setBalanceNotice('');
    try {
      await run();
      const json = await fetchJson(`/api/invoices/${encodeURIComponent(id.trim())}`);
      setDetail(json);
      setBalanceNotice(`Updated open balance: ${formatMinor(json.openBalanceMinor, json.invoice.currency)}`);
    } catch (e) {
      setActionError(e.message);
    } finally {
      setActionBusy(false);
    }
  };

  const invoicePath = (suffix) => `/api/invoices/${encodeURIComponent(id.trim())}${suffix}`;

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
          {balanceNotice && <p className="success">{balanceNotice}</p>}
          {actionError && <p className="error">{actionError}</p>}

          <h3>Actions</h3>
          <div className="action-grid">
            <div className="card">
              <strong>Apply credit</strong>
              <div className="form-row">
                <label>
                  Amount (minor)
                  <input value={creditAmount} onChange={(e) => setCreditAmount(e.target.value)} />
                </label>
                <button
                  type="button"
                  className="action"
                  disabled={actionBusy || !creditAmount}
                  onClick={() =>
                    reloadAfterAction(() =>
                      postJson(invoicePath('/credits'), {
                        amountMinor: Number(creditAmount),
                        currency: detail.invoice.currency,
                      }),
                    )
                  }
                >
                  POST …/credits
                </button>
              </div>
            </div>
            <div className="card">
              <strong>Record payment</strong>
              <div className="form-row">
                <label>
                  Amount (minor)
                  <input value={paymentAmount} onChange={(e) => setPaymentAmount(e.target.value)} />
                </label>
                <button
                  type="button"
                  className="action"
                  disabled={actionBusy || !paymentAmount}
                  onClick={() =>
                    reloadAfterAction(() =>
                      postJson(invoicePath('/payments'), {
                        amountMinor: Number(paymentAmount),
                        currency: detail.invoice.currency,
                      }),
                    )
                  }
                >
                  POST …/payments
                </button>
              </div>
            </div>
            <div className="card">
              <strong>Pay remainder</strong>
              <button
                type="button"
                className="action"
                disabled={actionBusy}
                onClick={() => reloadAfterAction(() => postNoBody(invoicePath('/pay')))}
              >
                POST …/pay
              </button>
            </div>
            <div className="card">
              <strong>Void invoice</strong>
              <button
                type="button"
                className="action danger"
                disabled={actionBusy}
                onClick={() => reloadAfterAction(() => postNoBody(invoicePath('/void')))}
              >
                POST …/void
              </button>
            </div>
            <div className="card">
              <strong>Write off balance</strong>
              <button
                type="button"
                className="action danger"
                disabled={actionBusy}
                onClick={() => reloadAfterAction(() => postNoBody(invoicePath('/write-off')))}
              >
                POST …/write-off
              </button>
            </div>
            <div className="card">
              <strong>Late fee</strong>
              <div className="form-row">
                <label>
                  Fee (minor)
                  <input value={lateFeeMinor} onChange={(e) => setLateFeeMinor(e.target.value)} />
                </label>
                <label>
                  As of (optional)
                  <input type="date" value={lateFeeAsOf} onChange={(e) => setLateFeeAsOf(e.target.value)} />
                </label>
                <button
                  type="button"
                  className="action"
                  disabled={actionBusy || !lateFeeMinor}
                  onClick={() =>
                    reloadAfterAction(() =>
                      postJson(invoicePath('/late-fee'), {
                        feeMinor: Number(lateFeeMinor),
                        asOf: lateFeeAsOf || null,
                      }),
                    )
                  }
                >
                  POST …/late-fee
                </button>
              </div>
            </div>
          </div>

          <h3>Invoice PDF</h3>
          <PdfPreview
            title="Preview invoice PDF"
            disabled={!id.trim()}
            buildUrl={() => invoicePath('/pdf')}
          />

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
                  <th />
                </tr>
              </thead>
              <tbody>
                {detail.payments.map((p) => (
                  <tr key={p.id}>
                    <td>{p.id}</td>
                    <td>{p.amountMinor}</td>
                    <td>{p.ledgerEventId}</td>
                    <td>{p.createdAt}</td>
                    <td>
                      <button
                        type="button"
                        className="action secondary"
                        disabled={actionBusy}
                        onClick={() =>
                          reloadAfterAction(() =>
                            postNoBody(invoicePath(`/payments/${encodeURIComponent(p.id)}/refund`)),
                          )
                        }
                      >
                        Refund
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          <p className="muted">Refund calls POST /api/invoices/&#123;id&#125;/payments/&#123;paymentId&#125;/refund, then reloads open balance.</p>
        </>
      )}
    </div>
  );
}

function OverduePanel({ onSelectInvoice }) {
  const [asOf, setAsOf] = useState('');
  const [report, setReport] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const load = async () => {
    setLoading(true);
    setError('');
    try {
      const params = asOf ? `?asOf=${encodeURIComponent(asOf)}` : '';
      setReport(await fetchJson(`/api/invoices/overdue${params}`));
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

  return (
    <div>
      <p className="muted">GET /api/invoices/overdue — unpaid invoices past due, grouped by customer.</p>
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
          {report.customers.length === 0 ? (
            <p className="muted">No overdue invoices.</p>
          ) : (
            report.customers.map((group) => (
              <div key={group.customer} className="card" style={{ marginBottom: '1rem' }}>
                <h3>{group.customer}</h3>
                {group.totalsByCurrency.map((t) => (
                  <p className="muted" key={t.currency}>
                    Total {t.currency.toUpperCase()}: {t.totalAmountMinor} (minor)
                  </p>
                ))}
                <table>
                  <thead>
                    <tr>
                      <th>Invoice</th>
                      <th>Due</th>
                      <th>Currency</th>
                      <th>Open (minor)</th>
                      <th>Days past due</th>
                    </tr>
                  </thead>
                  <tbody>
                    {group.invoices.map((line) => (
                      <tr key={line.id}>
                        <td>
                          <span
                            className="linkish"
                            onClick={() => onSelectInvoice(line.id)}
                            role="button"
                            tabIndex={0}
                          >
                            {line.id.slice(0, 8)}…
                          </span>
                        </td>
                        <td>{line.dueDate}</td>
                        <td>{line.currency}</td>
                        <td>{line.openAmountMinor}</td>
                        <td>{line.daysPastDue}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ))
          )}
        </>
      )}
    </div>
  );
}

function ScheduleGeneratePanel({ onGenerated }) {
  const [scheduleId, setScheduleId] = useState('');
  const [generated, setGenerated] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const generate = async () => {
    if (!scheduleId.trim()) return;
    setLoading(true);
    setError('');
    setGenerated(null);
    try {
      const invoice = await postNoBody(
        `/api/invoice-schedules/${encodeURIComponent(scheduleId.trim())}/generate`,
      );
      setGenerated(invoice);
      onGenerated(invoice.id);
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <p className="muted">
        POST /api/invoice-schedules/&#123;id&#125;/generate — creates the next invoice from an existing schedule (create
        schedules via POST /api/invoice-schedules outside this page if needed).
      </p>
      <div className="form-row">
        <label>
          Schedule ID
          <input value={scheduleId} onChange={(e) => setScheduleId(e.target.value)} placeholder="Paste schedule id" />
        </label>
        <button type="button" className="action" onClick={generate} disabled={loading || !scheduleId.trim()}>
          Generate next invoice
        </button>
      </div>
      {error && <p className="error">{error}</p>}
      {generated && (
        <div className="card">
          <strong>Generated invoice</strong>
          <p>ID: {generated.id}</p>
          <p>Customer: {generated.customerName}</p>
          <p>Due: {generated.dueDate}</p>
          <p>Status: {generated.status}</p>
          <p>Total: {formatMinor(generated.totalAmountMinor, generated.currency)}</p>
        </div>
      )}
    </div>
  );
}

function CustomerPaymentPanel() {
  const [customer, setCustomer] = useState('Fable Harbor Supplies');
  const [currency, setCurrency] = useState('usd');
  const [amountMinor, setAmountMinor] = useState('');
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const submit = async () => {
    if (!customer.trim() || !currency.trim() || !amountMinor) return;
    setLoading(true);
    setError('');
    setResult(null);
    try {
      const path = `/api/customers/${encodeURIComponent(customer.trim())}/payments`;
      const json = await postJson(path, {
        amountMinor: Number(amountMinor),
        currency: currency.trim(),
      });
      setResult(json);
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <p className="muted">
        POST /api/customers/&#123;customer&#125;/payments — allocates across open invoices oldest-due first (greedy).
      </p>
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
          Amount (minor)
          <input value={amountMinor} onChange={(e) => setAmountMinor(e.target.value)} inputMode="numeric" />
        </label>
        <button type="button" className="action" onClick={submit} disabled={loading || !amountMinor}>
          Apply payment
        </button>
      </div>
      {error && <p className="error">{error}</p>}
      {result && (
        <>
          <p className="success">
            Applied {result.appliedAmountMinor} {result.currency?.toUpperCase()} (minor) for {result.customer}
          </p>
          {result.payments.length === 0 ? (
            <p className="muted">No invoices received a slice.</p>
          ) : (
            <table>
              <thead>
                <tr>
                  <th>Invoice</th>
                  <th>Payment id</th>
                  <th>Amount (minor)</th>
                  <th>Ledger event</th>
                </tr>
              </thead>
              <tbody>
                {result.payments.map((p) => (
                  <tr key={p.id}>
                    <td>{p.invoiceId.slice(0, 8)}…</td>
                    <td>{p.id}</td>
                    <td>{p.amountMinor}</td>
                    <td>{p.ledgerEventId}</td>
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
  const [customer, setCustomer] = useState('Fable Harbor Supplies');
  const [currency, setCurrency] = useState('usd');
  const [from, setFrom] = useState('2026-01-01');
  const [to, setTo] = useState('2026-12-31');
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

  const statementPdfUrl = () => {
    if (!customer.trim() || !currency.trim() || !from || !to) return null;
    return `/api/customers/${encodeURIComponent(customer.trim())}/statement.pdf?currency=${encodeURIComponent(currency.trim())}&from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`;
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
      <h3>Statement PDF</h3>
      <PdfPreview title="Preview statement PDF" buildUrl={statementPdfUrl} disabled={!statementPdfUrl()} />
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

  const onCreated = (id) => {
    setSelectedInvoiceId(id);
    setTab('detail');
  };

  return (
    <div className="app" data-hookledger-page="invoice-desk">
      <header>
        <h1>Invoice Desk</h1>
        <p>
          Browse invoices, run write actions against the existing invoice APIs (including payment refunds, schedule
          generation, and customer-level payment allocation), preview PDFs, view overdue and aging reports, and load
          customer statements (amounts in minor units).
        </p>
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
        {tab === 'create' && <CreateInvoicePanel onCreated={onCreated} />}
        {tab === 'detail' && (
          <InvoiceDetailPanel invoiceId={selectedInvoiceId} setInvoiceId={setSelectedInvoiceId} />
        )}
        {tab === 'overdue' && <OverduePanel onSelectInvoice={onSelectInvoice} />}
        {tab === 'schedule' && <ScheduleGeneratePanel onGenerated={onCreated} />}
        {tab === 'customer-pay' && <CustomerPaymentPanel />}
        {tab === 'aging' && <AgingPanel />}
        {tab === 'statement' && <StatementPanel />}
      </section>
    </div>
  );
}
