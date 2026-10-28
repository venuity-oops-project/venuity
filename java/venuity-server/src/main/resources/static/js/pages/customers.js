import { api } from '../api.js';
import {
  formatDate,
  formatDateTime,
  formatMoney,
  initials,
  state,
  transactionMeta,
} from '../store.js';
import {
  busyButton,
  confirmDialog,
  debounce,
  downloadBlob,
  emptyState,
  escapeHtml,
  icon,
  openModal,
  pageHeader,
  printHtml,
  qs,
  skeletonRows,
  statusBadge,
  toast,
} from '../ui.js';

function safeFilename(value) {
  return String(value || 'customer')
    .trim()
    .replace(/[^a-z0-9_-]+/gi, '_')
    .replace(/^_+|_+$/g, '') || 'customer';
}

function blobToDataUrl(blob) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(new Error('Could not prepare the QR image for printing.'));
    reader.readAsDataURL(blob);
  });
}

export function mount(root, { addCleanup } = {}) {
  const isAdmin = state.user && state.user.role === 'ADMIN';
  const pageSize = 20;

  let query = '';
  let page = 0;
  let totalPages = 0;
  let totalElements = 0;
  let activeFilter = null;
  let items = [];
  let loading = false;
  let requestSeq = 0;

  root.innerHTML = `
    ${pageHeader(
      'Customers',
      'Search wallets, issue QR codes and keep customer details up to date.',
      `<button type="button" class="btn btn-primary" id="new-customer">
        ${icon('bx-plus-circle')} New customer
      </button>`,
    )}
    <div class="glass-card table-card">
      <div class="toolbar">
        <div class="search-field">
          ${icon('bx-search')}
          <input type="search" id="customer-search" placeholder="Search by name, phone or code..." autocomplete="off">
        </div>
        <div class="pill-toggle" id="status-toggle">
          <button type="button" data-active="" class="active">All</button>
          <button type="button" data-active="true">Active</button>
          <button type="button" data-active="false">Inactive</button>
        </div>
        <span class="spacer"></span>
        <span class="muted" id="customer-count" style="font-size:13px">\u2014</span>
        <button type="button" class="btn btn-ghost btn-sm" id="customer-refresh">
          ${icon('bx-refresh')} Refresh
        </button>
      </div>
      <div class="table-container">
        <table>
          <thead>
            <tr>
              <th>Customer</th>
              <th>Balance</th>
              <th>Status</th>
              <th>Registered</th>
              <th class="text-right">Actions</th>
            </tr>
          </thead>
          <tbody id="customer-rows">
            ${skeletonRows(5, 4)}
          </tbody>
        </table>
      </div>
      <div class="load-more" id="load-more-wrap" hidden>
        <button type="button" class="btn btn-secondary" id="load-more">
          ${icon('bx-down-arrow-alt')} Load more customers
        </button>
      </div>
    </div>
  `;

  const searchInput = qs('#customer-search', root);
  const statusToggle = qs('#status-toggle', root);
  const countElement = qs('#customer-count', root);
  const tbody = qs('#customer-rows', root);
  const refreshButton = qs('#customer-refresh', root);
  const newButton = qs('#new-customer', root);
  const loadMoreWrap = qs('#load-more-wrap', root);
  const loadMoreButton = qs('#load-more', root);

  function updateCount() {
    if (totalElements === 0) {
      countElement.textContent = 'No customers';
      return;
    }
    countElement.textContent = `${totalElements} customer${totalElements === 1 ? '' : 's'}`;
  }

  function updateLoadMore() {
    const hasMore = items.length > 0 && page + 1 < totalPages;
    loadMoreWrap.hidden = !hasMore;
  }

  function renderRows() {
    if (items.length === 0) {
      tbody.innerHTML = `
        <tr>
          <td colspan="5">
            ${emptyState(
              'bx-user',
              'No customers found',
              query
                ? 'Try a different search term or status filter.'
                : 'Create a customer to issue their first QR code.',
            )}
          </td>
        </tr>
      `;
      updateLoadMore();
      return;
    }

    tbody.innerHTML = items
      .map((customer) => {
        const balance = Number(customer.walletBalance) || 0;
        const code = customer.publicCode || customer.publicId;
        return `
          <tr class="table-row" data-id="${escapeHtml(customer.publicId)}" style="cursor:pointer">
            <td data-label="Customer">
              <div class="customer-identity">
                <div class="customer-avatar">${escapeHtml(initials(customer.name))}</div>
                <div style="min-width:0">
                  <div class="cell-name">${escapeHtml(customer.name)}</div>
                  <span class="cell-sub">
                    ${escapeHtml(customer.phone || 'No phone')} &middot; ${escapeHtml(String(code).slice(0, 10))}...
                  </span>
                </div>
              </div>
            </td>
            <td data-label="Balance">
              <span class="${balance > 0 ? 'amount-credit' : 'muted'}">${formatMoney(balance)}</span>
            </td>
            <td data-label="Status">${statusBadge(customer.active)}</td>
            <td data-label="Registered" class="text-light">${escapeHtml(formatDate(customer.createdAt))}</td>
            <td data-label="Actions">
              <div class="cell-actions">
                <button type="button" class="btn btn-soft btn-sm" data-action="qr" data-id="${escapeHtml(customer.publicId)}">
                  ${icon('bx-qr')} QR
                </button>
                <button type="button" class="btn btn-secondary btn-sm" data-action="edit" data-id="${escapeHtml(customer.publicId)}">
                  ${icon('bx-edit')} Edit
                </button>
                ${
                  isAdmin
                    ? `<button type="button" class="btn ${customer.active ? 'btn-danger-soft' : 'btn-soft'} btn-sm" data-action="toggle" data-id="${escapeHtml(customer.publicId)}">
                        ${icon(customer.active ? 'bx-block' : 'bx-check-circle')}
                        ${customer.active ? 'Deactivate' : 'Reactivate'}
                      </button>`
                    : ''
                }
              </div>
            </td>
          </tr>
        `;
      })
      .join('');

    updateLoadMore();
  }

  async function load({ append = false } = {}) {
    if (append && loading) return;
    loading = true;
    const seq = ++requestSeq;

    if (!append) {
      page = 0;
      items = [];
      countElement.textContent = '\u2014';
      tbody.innerHTML = skeletonRows(5, 4);
    } else {
      busyButton(loadMoreButton, true, 'Loading...');
    }

    try {
      const data = await api.customers({
        query,
        page: append ? page + 1 : 0,
        size: pageSize,
        active: activeFilter,
      });
      if (seq !== requestSeq) return;
      const content = data.content || [];
      items = append ? [...items, ...content] : content;
      page = Number(data.page) || 0;
      totalPages = Number(data.totalPages) || 0;
      totalElements = Number(data.totalElements) || 0;
      renderRows();
      updateCount();
    } catch (error) {
      if (seq === requestSeq) {
        tbody.innerHTML = `<tr><td colspan="5">${emptyState('bx-error-circle', 'Could not load customers', error.message)}</td></tr>`;
        updateLoadMore();
        toast(error.message, 'error');
      }
    } finally {
      if (seq === requestSeq) {
        loading = false;
        busyButton(loadMoreButton, false);
      }
    }
  }

  function findCustomer(publicId) {
    return items.find((customer) => customer.publicId === publicId);
  }

  async function toggleCustomer(customer, button) {
    const deactivating = customer.active;
    const confirmed = await confirmDialog({
      title: deactivating ? 'Deactivate customer?' : 'Reactivate customer?',
      message: deactivating
        ? `${customer.name} will be hidden from the active customer list. Their wallet and history are kept.`
        : `${customer.name} will be visible in the active customer list again.`,
      details: deactivating
        ? 'You can reactivate the customer later from the Inactive filter.'
        : '',
      confirmLabel: deactivating ? 'Deactivate' : 'Reactivate',
      danger: deactivating,
    });
    if (!confirmed) return;

    busyButton(button, true, deactivating ? 'Deactivating...' : 'Reactivating...');
    try {
      if (deactivating) await api.deactivateCustomer(customer.publicId);
      else await api.reactivateCustomer(customer.publicId);
      toast(`${customer.name} ${deactivating ? 'deactivated' : 'reactivated'}.`, 'success');
      await load();
    } catch (error) {
      toast(error.message, 'error');
      busyButton(button, false);
    }
  }

  async function handleRowAction(action, customer, button) {
    if (action === 'qr') {
      openQrModal(customer);
      return;
    }
    if (action === 'edit') {
      openCustomerForm(customer);
      return;
    }
    if (action === 'toggle') {
      await toggleCustomer(customer, button);
    }
  }

  tbody.addEventListener('click', (event) => {
    const row = event.target.closest('tr[data-id]');
    if (!row) return;
    const customer = findCustomer(row.dataset.id);
    if (!customer) return;
    const button = event.target.closest('button[data-action]');
    if (button) {
      event.stopPropagation();
      handleRowAction(button.dataset.action, customer, button);
    } else {
      openCustomerDetail(customer);
    }
  });

  const onSearch = debounce(() => {
    query = searchInput.value.trim();
    load();
  }, 300);

  searchInput.addEventListener('input', onSearch);

  statusToggle.addEventListener('click', (event) => {
    const button = event.target.closest('button[data-active]');
    if (!button) return;
    const value = button.dataset.active;
    activeFilter = value === 'true' ? true : value === 'false' ? false : null;
    statusToggle.querySelectorAll('button').forEach((item) => {
      item.classList.toggle('active', item === button);
    });
    load();
  });

  refreshButton.addEventListener('click', () => load());
  newButton.addEventListener('click', () => openCustomerForm(null));
  loadMoreButton.addEventListener('click', () => load({ append: true }));

  addCleanup?.(() => {
    onSearch.cancel();
  });

  function openCustomerForm(customer) {
    const isEdit = Boolean(customer);
    const symbol = state.settings.currencySymbol;
    openModal({
      title: isEdit ? 'Edit customer' : 'New customer',
      subtitle: isEdit
        ? 'Update the customer contact details.'
        : 'Create a wallet and generate a QR code.',
      size: 'narrow',
      body: `
        <form id="customer-form" autocomplete="off">
          <div class="form-group">
            <label for="cf-name">Name</label>
            <input class="form-input" id="cf-name" name="name" maxlength="150" required
              placeholder="Customer name" value="${escapeHtml(customer ? customer.name : '')}">
          </div>
          <div class="form-grid">
            <div class="form-group">
              <label for="cf-phone">Phone</label>
              <input class="form-input" id="cf-phone" name="phone" maxlength="30"
                placeholder="Optional" value="${escapeHtml(customer && customer.phone ? customer.phone : '')}">
            </div>
            ${
              isEdit
                ? ''
                : `<div class="form-group">
                    <label for="cf-balance">Initial balance (${escapeHtml(symbol)})</label>
                    <input class="form-input" id="cf-balance" name="initialBalance" type="number"
                      min="0" step="0.01" value="0">
                  </div>`
            }
          </div>
          ${
            isEdit
              ? ''
              : `<div class="form-group">
                  <label for="cf-code">Existing QR code (optional)</label>
                  <input class="form-input" id="cf-code" name="publicCode" maxlength="64"
                    placeholder="Leave blank to generate a new QR code">
                  <span class="form-hint">Paste a pre-printed QR code to assign it to this customer.</span>
                </div>`
          }
        </form>
      `,
      footer: `
        <button type="button" class="btn btn-secondary" data-modal-close>Cancel</button>
        <button type="submit" class="btn btn-primary" form="customer-form" data-save>
          ${icon('bx-save')} ${isEdit ? 'Save changes' : 'Create customer'}
        </button>
      `,
      onMount(modalRoot, close) {
        const form = qs('#customer-form', modalRoot);
        const saveButton = qs('[data-save]', modalRoot);

        form.addEventListener('submit', async (event) => {
          event.preventDefault();
          const name = qs('#cf-name', form).value.trim();
          const phone = qs('#cf-phone', form).value.trim();
          if (!name) {
            toast('Customer name is required.', 'error');
            qs('#cf-name', form).focus();
            return;
          }

          const payload = { name, phone: phone || null };
          if (!isEdit) {
            const initialRaw = qs('#cf-balance', form).value.trim() || '0';
            const initial = Number(initialRaw);
            if (!Number.isFinite(initial) || initial < 0) {
              toast('Initial balance must be zero or more.', 'error');
              qs('#cf-balance', form).focus();
              return;
            }
            payload.initialBalance = initialRaw;
            const code = qs('#cf-code', form).value.trim();
            if (code) payload.publicCode = code;
          }

          busyButton(saveButton, true, isEdit ? 'Saving...' : 'Creating...');
          try {
            const result = isEdit
              ? await api.updateCustomer(customer.publicId, payload)
              : await api.createCustomer(payload);
            close();
            toast(isEdit ? 'Customer updated.' : 'Customer created.', 'success');
            await load();
            if (!isEdit && result) openQrModal(result);
          } catch (error) {
            toast(error.message, 'error');
            busyButton(saveButton, false);
          }
        });
      },
    });
  }

  function openQrModal(customer) {
    const displayCode = customer.publicCode || customer.publicId;
    let objectUrl = null;
    let closed = false;

    openModal({
      title: customer.name,
      subtitle: 'Customer QR code',
      size: 'narrow',
      body: `
        <div style="text-align:center">
          <div class="qr-frame" id="qr-frame">
            <div class="qr-loading">${icon('bx-loader-alt', 'bx-spin')}</div>
          </div>
          <p class="qr-meta">
            ${escapeHtml(displayCode)}<br>
            Balance: <strong>${formatMoney(customer.walletBalance)}</strong>
          </p>
        </div>
      `,
      footer: `
        <button type="button" class="btn btn-secondary" data-modal-close>Close</button>
        <button type="button" class="btn btn-secondary" data-print disabled>${icon('bx-printer')} Print</button>
        <button type="button" class="btn btn-primary" data-download disabled>${icon('bx-download')} Download</button>
      `,
      onMount(modalRoot) {
        const frame = qs('#qr-frame', modalRoot);
        const downloadButton = qs('[data-download]', modalRoot);
        const printButton = qs('[data-print]', modalRoot);

        api
          .qrBlob(customer.publicId, 512)
          .then(async (blob) => {
            if (closed) return;
            objectUrl = URL.createObjectURL(blob);
            frame.innerHTML = `<img class="qr-image" alt="QR code for ${escapeHtml(customer.name)}" src="${objectUrl}">`;
            downloadButton.disabled = false;
            printButton.disabled = false;

            downloadButton.addEventListener('click', () => {
              downloadBlob(objectUrl, `QR_${safeFilename(customer.name)}.png`);
            });

            printButton.addEventListener('click', async () => {
              try {
                const dataUrl = await blobToDataUrl(blob);
                printHtml(
                  `QR code - ${customer.name}`,
                  `<div class="receipt-head">
                    <h1>${escapeHtml(customer.name)}</h1>
                    <p>${escapeHtml(displayCode)}</p>
                  </div>
                  <div style="text-align:center;margin-top:20px">
                    <img src="${dataUrl}" alt="QR code" style="width:280px;height:280px">
                  </div>`,
                );
              } catch (error) {
                toast(error.message, 'error');
              }
            });
          })
          .catch((error) => {
            if (closed) return;
            frame.innerHTML = `
              <div class="qr-loading" style="color:var(--danger);flex-direction:column;gap:8px">
                ${icon('bx-error-circle')}
                <span style="font-size:12px">${escapeHtml(error.message)}</span>
              </div>
            `;
            toast(error.message, 'error');
          });
      },
      onClose() {
        closed = true;
        if (objectUrl) {
          URL.revokeObjectURL(objectUrl);
          objectUrl = null;
        }
      },
    });
  }

  function openCustomerDetail(customer) {
    openModal({
      title: 'Customer details',
      subtitle: customer.name,
      size: 'wide',
      body: `
        <div style="display:flex;gap:16px;align-items:center;flex-wrap:wrap">
          <div class="customer-avatar" style="width:58px;height:58px;font-size:18px">
            ${escapeHtml(initials(customer.name))}
          </div>
          <div style="flex:1;min-width:180px">
            <div class="cell-name" style="font-size:18px">${escapeHtml(customer.name)}</div>
            <span class="cell-sub">${escapeHtml(customer.phone || 'No phone')} &middot; ${escapeHtml(customer.publicCode || customer.publicId)}</span>
          </div>
          ${statusBadge(customer.active)}
        </div>
        <div class="summary-strip mt-24" style="grid-template-columns:repeat(2,minmax(0,1fr))">
          <div class="summary-tile">
            <div class="label">Wallet balance</div>
            <div class="value">${formatMoney(customer.walletBalance)}</div>
          </div>
          <div class="summary-tile">
            <div class="label">Registered</div>
            <div class="value" style="font-size:14px">${escapeHtml(formatDate(customer.createdAt))}</div>
          </div>
        </div>
        <h3 style="margin:24px 0 12px;font-size:15px">Recent transactions</h3>
        <div id="customer-transactions">
          <div class="skeleton-row" style="height:52px"></div>
          <div class="skeleton-row" style="height:52px"></div>
        </div>
      `,
      footer: '<button type="button" class="btn btn-secondary" data-modal-close>Close</button>',
      onMount(modalRoot) {
        const target = qs('#customer-transactions', modalRoot);
        api
          .transactions({ customerId: customer.publicId, page: 0, size: 10 })
          .then((data) => {
            if (!modalRoot.isConnected) return;
            const transactions = data.content || [];
            if (transactions.length === 0) {
              target.innerHTML = emptyState(
                'bx-receipt',
                'No transactions yet',
                'Wallet activity will appear here.',
              );
              return;
            }
            target.innerHTML = `<div class="activity-list">${transactions
              .map((transaction) => {
                const meta = transactionMeta(transaction.type);
                const amount = Math.abs(Number(transaction.amount) || 0);
                return `
                  <div class="activity-item">
                    <div class="activity-icon ${meta.credit ? 'credit' : 'debit'}">${icon(meta.icon)}</div>
                    <div class="activity-main">
                      <div class="activity-title">${escapeHtml(meta.label)}</div>
                      <div class="activity-meta">
                        ${escapeHtml(formatDateTime(transaction.createdAt))}
                        ${transaction.note ? ` &middot; ${escapeHtml(transaction.note)}` : ''}
                      </div>
                    </div>
                    <div class="activity-amount ${meta.credit ? 'credit' : 'debit'}">
                      ${meta.credit ? '+' : '-'}${formatMoney(amount)}
                    </div>
                  </div>
                `;
              })
              .join('')}</div>`;
          })
          .catch((error) => {
            if (!modalRoot.isConnected) return;
            target.innerHTML = emptyState('bx-error-circle', 'Could not load activity', error.message);
          });
      },
    });
  }

  load();
}
