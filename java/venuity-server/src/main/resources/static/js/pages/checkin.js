import { api } from '../api.js';
import {
  formatMoney,
  formatTime,
  state,
} from '../store.js';
import {
  escapeHtml,
  icon,
  idempotencyKey,
  pageHeader,
  playTone,
  qs,
  toast,
} from '../ui.js';
import { QrScanner } from '../scanner.js';

export function mount(root, { addCleanup } = {}) {
  let scanner = null;
  let recent = [];
  let processing = false;
  let pendingCheckIn = null;
  let lastCode = '';
  let lastCodeAt = 0;
  let successTimer = null;
  let errorTimer = null;

  const fee = Number(state.settings.checkinFee) || 0;
  const overlayData = {
    name: '',
    balance: 0,
    fee,
    errorTitle: 'Check-in failed',
    errorText: '',
  };

  root.innerHTML = `
    ${pageHeader('Check-in', 'Scan a customer QR code to deduct the entry fee.')}
    <div class="glass-card scanner-card">
      <div class="scanner-header">
        <h2>${icon('bx-qr-scan', 'text-gradient')} Kiosk scanner</h2>
        <p class="subtitle">Quick, frictionless entry for your customers.</p>
      </div>
      <div class="scanner-layout">
        <div class="scanner-wrapper">
          <video id="checkin-video" autoplay playsinline muted></video>
          <canvas id="checkin-canvas" hidden></canvas>
          <div class="scanner-target-overlay" id="checkin-target" hidden>
            <div class="scanner-target-box"></div>
          </div>
          <div class="scanner-overlay visible clickable" id="checkin-overlay"></div>
        </div>
        <div class="scanner-side">
          <div class="scanner-controls" style="margin-top:0">
            <select class="form-select" id="checkin-camera" aria-label="Camera"></select>
            <button type="button" class="btn btn-primary" id="checkin-scan-toggle">
              ${icon('bx-play-circle')} Start scanner
            </button>
            <span class="scanner-active-pill" id="checkin-pill" hidden>Scanning</span>
          </div>
          <div class="fee-info">
            <div class="fee-badge">${formatMoney(fee)}</div>
            <span>Standard entry fee</span>
          </div>
          <div class="manual-entry">
            <h3>Manual entry</h3>
            <p>Enter the customer code if the QR code will not scan.</p>
            <form id="manual-form" class="input-group" autocomplete="off">
              <input class="form-input" id="manual-code" placeholder="Customer code" aria-label="Customer code">
              <button type="submit" class="btn btn-primary" title="Check in">${icon('bx-log-in')}</button>
            </form>
          </div>
          <div class="glass-card flat" style="padding:16px">
            <div style="display:flex;align-items:center;justify-content:space-between;gap:10px;margin-bottom:4px">
              <h3 style="font-size:15px;margin:0">Recent check-ins</h3>
              <span class="muted" style="font-size:11.5px">This session</span>
            </div>
            <div class="activity-list" id="recent-checkins">
              <p class="muted" style="font-size:13px;padding:12px 0">No check-ins yet.</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  `;

  const video = qs('#checkin-video', root);
  const canvas = qs('#checkin-canvas', root);
  const overlay = qs('#checkin-overlay', root);
  const target = qs('#checkin-target', root);
  const cameraSelect = qs('#checkin-camera', root);
  const scanToggle = qs('#checkin-scan-toggle', root);
  const activePill = qs('#checkin-pill', root);
  const manualForm = qs('#manual-form', root);
  const manualInput = qs('#manual-code', root);
  const recentElement = qs('#recent-checkins', root);

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

  function updateControls() {
    const active = scanner ? scanner.active : false;
    scanToggle.classList.toggle('stop-btn', active);
    scanToggle.classList.toggle('btn-primary', !active);
    scanToggle.innerHTML = active
      ? `${icon('bx-stop-circle')} Stop scanner`
      : `${icon('bx-play-circle')} Start scanner`;
    activePill.hidden = !active;
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
          <h3>${escapeHtml(overlayData.name)}</h3>
          <p class="highlight-text">Balance ${formatMoney(overlayData.balance)}</p>
          <div class="fee-deducted">-${formatMoney(overlayData.fee)} entry fee</div>
        </div>
      `;
      return;
    }

    if (scannerState === 'error') {
      overlay.innerHTML = `
        <div class="overlay-content">
          <div class="icon-circle error shake">${icon('bx-x')}</div>
          <h3>${escapeHtml(overlayData.errorTitle)}</h3>
          <p>${escapeHtml(overlayData.errorText)}</p>
          <small>Tap to resume scanning</small>
        </div>
      `;
      return;
    }

    overlay.innerHTML = `
      <div class="overlay-content">
        <div class="icon-circle primary">${icon('bx-camera')}</div>
        <h3>Camera inactive</h3>
        <p>Click to begin scanning QR codes, or use manual entry.</p>
      </div>
    `;
  }

  function renderRecent() {
    if (recent.length === 0) {
      recentElement.innerHTML =
        '<p class="muted" style="font-size:13px;padding:12px 0">No check-ins yet.</p>';
      return;
    }
    recentElement.innerHTML = recent
      .map(
        (entry) => `
          <div class="activity-item">
            <div class="activity-icon debit">${icon('bx-log-in')}</div>
            <div class="activity-main">
              <div class="activity-title">${escapeHtml(entry.name)}</div>
              <div class="activity-meta">
                ${escapeHtml(formatTime(entry.at))} &middot; Balance ${formatMoney(entry.balance)}
              </div>
            </div>
            <div class="activity-amount debit">-${formatMoney(entry.fee)}</div>
          </div>
        `,
      )
      .join('');
  }

  function keyForCheckIn(code) {
    if (pendingCheckIn && pendingCheckIn.code === code) return pendingCheckIn.key;
    pendingCheckIn = { code, key: idempotencyKey() };
    return pendingCheckIn.key;
  }

  async function processCode(rawCode) {
    const code = String(rawCode || '').trim();
    if (!code || processing) return;

    const now = Date.now();
    if (code === lastCode && now - lastCodeAt < 3000) return;
    lastCode = code;
    lastCodeAt = now;
    processing = true;
    if (scanner) scanner.pause(5000);

    try {
      const response = await api.checkIn(code, keyForCheckIn(code));
      pendingCheckIn = null;
      overlayData.name = response.customerName || 'Customer';
      overlayData.balance = Number(response.balanceAfter) || 0;
      overlayData.fee = Number(response.feeCharged) || fee;
      if (scanner) scanner.setState('success');
      playTone(true);
      toast(`${overlayData.name} checked in.`, 'success');

      recent = [
        {
          name: overlayData.name,
          fee: overlayData.fee,
          balance: overlayData.balance,
          at: response.checkedInAt || new Date().toISOString(),
        },
        ...recent,
      ].slice(0, 8);
      renderRecent();

      if (successTimer) clearTimeout(successTimer);
      successTimer = setTimeout(() => {
        if (scanner && scanner.active) scanner.resume();
      }, 2600);
    } catch (error) {
      if (!error.status || error.status >= 500) {
        // Keep the idempotency key so a retry cannot charge twice.
      } else {
        pendingCheckIn = null;
      }
      overlayData.errorTitle = 'Check-in failed';
      overlayData.errorText = error.message;
      if (scanner) scanner.setState('error');
      playTone(false);
      toast(error.message, 'error');

      if (errorTimer) clearTimeout(errorTimer);
      errorTimer = setTimeout(() => {
        if (scanner && scanner.active) scanner.resume();
      }, 6000);
    } finally {
      processing = false;
    }
  }

  scanner = new QrScanner({
    video,
    canvas,
    onScan: processCode,
    onStateChange(scannerState) {
      renderOverlay(scannerState);
      updateControls();
    },
    onCamerasChange: renderCameras,
  });

  renderCameras(scanner.cameras, scanner.selectedCamera);
  renderOverlay('placeholder');
  updateControls();
  renderRecent();

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

  manualForm.addEventListener('submit', (event) => {
    event.preventDefault();
    const code = manualInput.value.trim();
    if (!code) {
      manualInput.focus();
      return;
    }
    manualInput.value = '';
    processCode(code);
  });

  addCleanup?.(() => {
    if (successTimer) clearTimeout(successTimer);
    if (errorTimer) clearTimeout(errorTimer);
    if (scanner) scanner.destroy();
  });
}
