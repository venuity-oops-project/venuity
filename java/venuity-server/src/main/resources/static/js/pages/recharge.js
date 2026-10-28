import { api } from '../api.js';
import {
  formatDateTime,
  formatMoney,
  initials,
  state,
} from '../store.js';
import {
  busyButton,
  debounce,
  escapeHtml,
  icon,
  idempotencyKey,
  openModal,
  pageHeader,
  printHtml,
  qs,
  toast,
} from '../ui.js';
import { QrScanner } from '../scanner.js';

export function mount(root, { addCleanup } = {}) {
  let scanner = null;
  let selectedCustomer = null;
  let autocompleteItems = [];
  let searchSeq = 0;
  let pendingRecharge = null;
  let scanStopTimer = null;
  const overlayData = {
    successName: '',
    successText: '',
    errorTitle: 'Customer not found',
    errorText: '',
  };

  root.innerHTML = `
    ${pageHeader('Recharge', 'Top up a wallet by scanning a QR code or searching by name.')}
    <div class="glass-card recharge-card">
      <div class="recharge-heading">
        <div class="head-icon">${icon('bx-wallet')}</div>
        <h2>Top up balance</h2>
        <p class="muted">Scan the customer QR code or search for them below.</p>
      </div>
      <div class="recharge-grid">
        <div>
          <div class="scanner-wrapper">
            <video id="recharge-video" autoplay playsinline muted></video>
            <canvas id="recharge-canvas" hidden></canvas>
            <div class="scanner-target-overlay" id="recharge-target" hidden>
              <div class="scanner-target-box"></div>
            </div>
            <div class="scanner-overlay visible clickable" id="recharge-overlay"></div>
          </div>
          <div class="scanner-controls">
            <select class="form-select" id="recharge-camera" aria-label="Camera"></select>
            <button type="button" class="btn btn-primary" id="recharge-scan-toggle">
              ${icon('bx-play-circle')} Start scanner
            </button>
          </div>
        </div>
        <div>
          <form id="recharge-form" autocomplete="off">
            <div class="form-group autocomplete-wrap">
              <label for="recharge-search">Customer</label>
              <input class="form-input" id="recharge-search" placeholder="Search by name..." autocomplete="off">
              <div class="autocomplete-list" id="recharge-autocomplete" hidden></div>
            </div>
            <div id="recharge-selected" hidden></div>
            <div class="form-group">
              <label for="recharge-amount">Amount (${escapeHtml(state.settings.currencySymbol)})</label>
              <div class="amount-input-wrap">
                <span class="currency">${escapeHtml(state.settings.currencySymbol)}</span>
                <input class="form-input" id="recharge-amount" type="number" min="0.01" max="99999999.99"
                  step="0.01" placeholder="0.00" required>
              </div>
            </div>
            <div class="form-group">
              <label for="recharge-note">Note (optional)</label>
              <input class="form-input" id="recharge-note" maxlength="1000" placeholder="e.g. Weekend top-up">
            </div>
            <div class="quick-amounts">
              <button type="button" class="btn btn-secondary" data-amount="100">${escapeHtml(state.settings.currencySymbol)}100</button>
              <button type="button" class="btn btn-secondary" data-amount="500">${escapeHtml(state.settings.currencySymbol)}500</button>
              <button type="button" class="btn btn-secondary" data-amount="1000">${escapeHtml(state.settings.currencySymbol)}1000</button>
            </div>
            <button type="submit" class="btn btn-primary btn-block" id="recharge-submit">
              ${icon('bx-check-circle')} Review recharge
            </button>
          </form>
        </div>
      </div>
    </div>
  `;

  const video = qs('#recharge-video', root);
  const canvas = qs('#recharge-canvas', root);
  const overlay = qs('#recharge-overlay', root);
  const target = qs('#recharge-target', root);
  const cameraSelect = qs('#recharge-camera', root);
  const scanToggle = qs('#recharge-scan-toggle', root);
  const searchInput = qs('#recharge-search', root);
  const autocomplete = qs('#recharge-autocomplete', root);
  const selectedElement = qs('#recharge-selected', root);
  const form = qs('#recharge-form', root);
  const amountInput = qs('#recharge-amount', root);
  const noteInput = qs('#recharge-note', root);

  function renderCameras(cameras, selected) {
    if (!cameras || cameras.length === 0) {
      cameraSelect.innerHTML = '<option value="">Click Start to detect cameras...</option>';
      cameraSelect.disabled = true;
      return;
    }
    cameraSelect.innerHTML = cameras
      .map(
        (camera) =>
          `<option value="${escapeHtml(camera.id)}" ${camera.id === selected ? 'selected' : ''}>${escapeHtml(camera.label)}</option>`,
      )
      .join('');
    cameraSelect.disabled = scanner ? scanner.active : false;
  }

  function updateScanButton() {
    const active = scanner ? scanner.active : false;
    scanToggle.classList.toggle('stop-btn', active);
    scanToggle.classList.toggle('btn-primary', !active);
    scanToggle.innerHTML = active
      ? `${icon('bx-stop-circle')} Stop scanner`
      : `${icon('bx-play-circle')} Start scanner`;
    cameraSelect.disabled = active;
  }

  function renderOverlay(scannerState) {
    const visible = scannerState !== 'active';
    overlay.classList.toggle('visible', visible);
    overlay.classList.toggle('success', scannerState === 'success');
    overlay.classList.toggle('error', scannerState === 'error');
    overlay.classList.toggle('clickable', scannerState === 'placeholder' || scannerState === 'error');
    target.hidden = scannerState !== 'active';

    if (!visible) {
      overlay.innerHTML = '';
      return;
    }

    if (scannerState === 'loading') {
      overlay.innerHTML = `
        <div class="overlay-content">
          ${icon('bx-loader-alt', 'bx-spin loading-icon')}
          <h3>Starting camera...</h3>
          <p>Allow camera access if your browser asks.</p>
        </div>
      `;
      return;
    }

    if (scannerState === 'success') {
      overlay.innerHTML = `
        <div class="overlay-content">
          <div class="icon-circle success pulse">${icon('bx-check')}</div>
          <h3>${escapeHtml(overlayData.successName || 'QR captured')}</h3>
          <p class="highlight-text">${escapeHtml(overlayData.successText || '')}</p>
        </div>
      `;
      return;
    }

    if (scannerState === 'error') {
      overlay.innerHTML = `
        <div class="overlay-content">
          <div class="icon-circle error shake">${icon('bx-x')}</div>
          <h3>${escapeHtml(overlayData.errorTitle || 'Scan failed')}</h3>
          <p>${escapeHtml(overlayData.errorText || 'Try again.')}</p>
          <small>Tap to resume scanning</small>
        </div>
      `;
      return;
    }

    overlay.innerHTML = `
      <div class="overlay-content">
        <div class="icon-circle primary">${icon('bx-camera')}</div>
        <h3>Camera inactive</h3>
        <p>Click to begin scanning, or search for the customer by name.</p>
      </div>
    `;
  }

  function clearSelectedCustomer() {
    selectedCustomer = null;
    selectedElement.hidden = true;
    selectedElement.innerHTML = '';
  }

  function renderSelectedCustomer() {
    if (!selectedCustomer) {
      clearSelectedCustomer();
      return;
    }
    selectedElement.hidden = false;
    selectedElement.innerHTML = `
      <div class="selected-customer">
        <div class="customer-avatar">${escapeHtml(initials(selectedCustomer.name))}</div>
        <div class="customer-main">
          <div class="customer-name">${escapeHtml(selectedCustomer.name)}</div>
          <div class="customer-balance">
            Balance <strong>${formatMoney(selectedCustomer.walletBalance)}</strong>
            &middot; ${escapeHtml(selectedCustomer.publicCode || selectedCustomer.publicId)}
          </div>
        </div>
        <button type="button" class="icon-btn" id="clear-customer" title="Clear customer">
          ${icon('bx-x')}
        </button>
      </div>
    `;
    qs('#clear-customer', selectedElement).addEventListener('click', () => {
      clearSelectedCustomer();
      searchInput.value = '';
      searchInput.focus();
    });
  }

  function selectCustomer(customer) {
    selectedCustomer = customer;
    searchInput.value = customer.name;
    autocomplete.hidden = true;
    autocomplete.innerHTML = '';
    renderSelectedCustomer();
  }

  function hideAutocomplete() {
    autocomplete.hidden = true;
    autocomplete.innerHTML = '';
  }

  function renderAutocomplete() {
    if (autocompleteItems.length === 0) {
      hideAutocomplete();
      return;
    }
    autocomplete.hidden = false;
    autocomplete.innerHTML = autocompleteItems
      .map(
        (customer) => `
          <div class="autocomplete-item" data-id="${escapeHtml(customer.publicId)}">
            <span>${escapeHtml(customer.name)}</span>
            <small>${formatMoney(customer.walletBalance)}</small>
          </div>
        `,
      )
      .join('');
  }

  const onSearch = debounce(async () => {
    const value = searchInput.value.trim();
    if (!value) {
      autocompleteItems = [];
      hideAutocomplete();
      return;
    }
    const seq = ++searchSeq;
    try {
      const data = await api.customers({ query: value, page: 0, size: 8, active: true });
      if (seq !== searchSeq) return;
      autocompleteItems = data.content || [];
      renderAutocomplete();
    } catch (error) {
      if (seq === searchSeq) hideAutocomplete();
    }
  }, 300);

  searchInput.addEventListener('input', () => {
    if (selectedCustomer && searchInput.value.trim() !== selectedCustomer.name) {
      clearSelectedCustomer();
    }
    onSearch();
  });

  autocomplete.addEventListener('click', (event) => {
    const item = event.target.closest('[data-id]');
    if (!item) return;
    const customer = autocompleteItems.find((entry) => entry.publicId === item.dataset.id);
    if (customer) selectCustomer(customer);
  });

  const onDocumentClick = (event) => {
    if (!event.target.closest('.autocomplete-wrap')) hideAutocomplete();
  };
  document.addEventListener('click', onDocumentClick);

  async function handleScan(code) {
    scanner.pause(4000);
    try {
      const customer = await api.customerByCode(code);
      if (!customer.active) {
        throw new Error('This customer is inactive and cannot be recharged.');
      }
      selectCustomer(customer);
      overlayData.successName = customer.name;
      overlayData.successText = `Wallet balance ${formatMoney(customer.walletBalance)}`;
      scanner.showSuccessFor(2200);
      toast('Customer QR captured.', 'success');
      if (scanStopTimer) clearTimeout(scanStopTimer);
      scanStopTimer = setTimeout(() => {
        if (scanner) scanner.stop();
      }, 1800);
    } catch (error) {
      overlayData.errorTitle = 'Customer not found';
      overlayData.errorText = error.message;
      scanner.setState('error');
      toast(error.message, 'error');
    }
  }

  scanner = new QrScanner({
    video,
    canvas,
    onScan: handleScan,
    onStateChange(scannerState) {
      renderOverlay(scannerState);
      updateScanButton();
    },
    onCamerasChange: renderCameras,
  });

  renderCameras(scanner.cameras, scanner.selectedCamera);
  renderOverlay('placeholder');
  updateScanButton();

  async function toggleScanner() {
    if (!scanner) return;
    if (scanner.active) {
      scanner.stop();
      return;
    }
    try {
      await scanner.start();
    } catch (error) {
      overlayData.errorTitle = 'Camera unavailable';
      overlayData.errorText = error.message;
      renderOverlay('error');
      toast(error.message, 'error');
    }
  }

  scanToggle.addEventListener('click', toggleScanner);

  overlay.addEventListener('click', () => {
    if (!scanner) return;
    if (scanner.active) scanner.resume();
    else toggleScanner();
  });

  cameraSelect.addEventListener('change', () => {
    if (scanner && !scanner.active) scanner.setSelectedCamera(cameraSelect.value);
  });

  root.querySelectorAll('[data-amount]').forEach((button) => {
    button.addEventListener('click', () => {
      amountInput.value = button.dataset.amount;
      amountInput.focus();
    });
  });

  function keyForRecharge(publicId, amount, note) {
    if (
      pendingRecharge &&
      pendingRecharge.publicId === publicId &&
      pendingRecharge.amount === amount &&
      pendingRecharge.note === note
    ) {
      return pendingRecharge.key;
    }
    pendingRecharge = { publicId, amount, note, key: idempotencyKey() };
    return pendingRecharge.key;
  }

  function openReceipt(response, amount, note) {
    const customer = response.customer || selectedCustomer;
    const balanceAfter = response.balanceAfter;
    const receiptHtml = `
      <div class="receipt-hero">
        <div class="receipt-check">${icon('bx-check')}</div>
        <h3>${formatMoney(amount)} added</h3>
        <p>${escapeHtml(formatDateTime(new Date().toISOString()))}</p>
      </div>
      <div class="receipt-rows">
        <div class="receipt-row"><span>Customer</span><strong>${escapeHtml(customer ? customer.name : 'Customer')}</strong></div>
        <div class="receipt-row"><span>Amount</span><strong>${formatMoney(amount)}</strong></div>
        <div class="receipt-row"><span>New balance</span><strong>${formatMoney(balanceAfter)}</strong></div>
        ${response.transactionId ? `<div class="receipt-row"><span>Transaction</span><strong>#${escapeHtml(response.transactionId)}</strong></div>` : ''}
        ${note ? `<div class="receipt-row"><span>Note</span><strong>${escapeHtml(note)}</strong></div>` : ''}
      </div>
    `;

    openModal({
      title: 'Recharge complete',
      subtitle: customer ? customer.name : '',
      size: 'narrow',
      body: receiptHtml,
      footer: `
        <button type="button" class="btn btn-secondary" data-modal-close>Done</button>
        <button type="button" class="btn btn-primary" data-print>${icon('bx-printer')} Print receipt</button>
      `,
      onMount(modalRoot) {
        qs('[data-print]', modalRoot).addEventListener('click', () => {
          printHtml(
            'Recharge receipt',
            `<div class="receipt-head">
              <div class="receipt-check">&#10003;</div>
              <h1>${formatMoney(amount)} added</h1>
              <p>${escapeHtml(customer ? customer.name : 'Customer')}</p>
            </div>
            <div class="receipt-rows">
              <div class="receipt-row"><span>Amount</span><strong>${formatMoney(amount)}</strong></div>
              <div class="receipt-row"><span>New balance</span><strong>${formatMoney(balanceAfter)}</strong></div>
              ${response.transactionId ? `<div class="receipt-row"><span>Transaction</span><strong>#${escapeHtml(response.transactionId)}</strong></div>` : ''}
              ${note ? `<div class="receipt-row"><span>Note</span><strong>${escapeHtml(note)}</strong></div>` : ''}
            </div>
            <div class="receipt-foot">${escapeHtml(formatDateTime(new Date().toISOString()))}</div>`,
          );
        });
      },
    });
  }

  function openConfirm(amountRaw, note) {
    const customer = selectedCustomer;
    if (!customer) return;
    const before = Number(customer.walletBalance) || 0;
    const amount = Number(amountRaw);
    const after = before + amount;

    openModal({
      title: 'Confirm recharge',
      subtitle: customer.name,
      size: 'narrow',
      body: `
        <p class="muted" style="line-height:1.65">
          Add <strong>${formatMoney(amount)}</strong> to this wallet?
        </p>
        <div class="receipt-rows mt-16">
          <div class="receipt-row"><span>Current balance</span><strong>${formatMoney(before)}</strong></div>
          <div class="receipt-row"><span>New balance</span><strong>${formatMoney(after)}</strong></div>
          ${note ? `<div class="receipt-row"><span>Note</span><strong>${escapeHtml(note)}</strong></div>` : ''}
        </div>
        <div class="modal-note mt-16">
          The recharge is recorded in the wallet ledger and can be retried safely.
        </div>
      `,
      footer: `
        <button type="button" class="btn btn-secondary" data-modal-close>Cancel</button>
        <button type="button" class="btn btn-primary" data-confirm-recharge>
          ${icon('bx-check-circle')} Recharge
        </button>
      `,
      onMount(modalRoot, close) {
        const confirmButton = qs('[data-confirm-recharge]', modalRoot);
        confirmButton.addEventListener('click', async () => {
          const key = keyForRecharge(customer.publicId, amountRaw, note);
          busyButton(confirmButton, true, 'Recharging...');
          try {
            const response = await api.recharge(customer.publicId, amountRaw, note, key);
            pendingRecharge = null;
            selectedCustomer = response.customer || {
              ...customer,
              walletBalance: response.balanceAfter,
            };
            renderSelectedCustomer();
            amountInput.value = '';
            noteInput.value = '';
            close();
            toast(`Recharge successful. New balance ${formatMoney(response.balanceAfter)}.`, 'success');
            openReceipt(response, amountRaw, note);
          } catch (error) {
            if (error.code === 'IDEMPOTENCY_CONFLICT') pendingRecharge = null;
            toast(error.message, 'error');
            busyButton(confirmButton, false);
          }
        });
      },
    });
  }

  form.addEventListener('submit', (event) => {
    event.preventDefault();
    if (!selectedCustomer) {
      toast('Select a customer by name or scan their QR code first.', 'error');
      searchInput.focus();
      return;
    }
    const amountRaw = amountInput.value.trim();
    const amount = Number(amountRaw);
    if (!Number.isFinite(amount) || amount < 0.01) {
      toast('Enter a recharge amount of at least 0.01.', 'error');
      amountInput.focus();
      return;
    }
    if (amount > 99999999.99) {
      toast('The recharge amount is too large.', 'error');
      amountInput.focus();
      return;
    }
    openConfirm(amountRaw, noteInput.value.trim());
  });

  addCleanup?.(() => {
    onSearch.cancel();
    document.removeEventListener('click', onDocumentClick);
    if (scanStopTimer) clearTimeout(scanStopTimer);
    if (scanner) scanner.destroy();
  });
}
