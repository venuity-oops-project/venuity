/**
 * Small DOM, feedback and formatting helpers shared by the pages.
 */

export function escapeHtml(value) {
  return String(value === undefined || value === null ? '' : value).replace(
    /[&<>"']/g,
    (character) => {
      switch (character) {
        case '&':
          return '&amp;';
        case '<':
          return '&lt;';
        case '>':
          return '&gt;';
        case '"':
          return '&quot;';
        default:
          return '&#39;';
      }
    },
  );
}

export function qs(selector, root = document) {
  return root.querySelector(selector);
}

export function qsa(selector, root = document) {
  return Array.from(root.querySelectorAll(selector));
}

export function icon(name, className = '') {
  return `<i class="bx ${escapeHtml(name)}${className ? ` ${className}` : ''}"></i>`;
}

let toastContainer = null;

function ensureToastContainer() {
  if (!toastContainer || !toastContainer.isConnected) {
    toastContainer = document.createElement('div');
    toastContainer.className = 'toast-container';
    toastContainer.setAttribute('aria-live', 'polite');
    document.body.appendChild(toastContainer);
  }
  return toastContainer;
}

export function toast(message, type = 'success') {
  const container = ensureToastContainer();
  const element = document.createElement('div');
  const safeType = type === 'error' || type === 'info' ? type : 'success';
  const iconName =
    safeType === 'success' ? 'bx-check-circle' : safeType === 'error' ? 'bx-x-circle' : 'bx-info-circle';
  element.className = `toast ${safeType}`;
  element.innerHTML = `${icon(iconName)}<div>${escapeHtml(message)}</div>`;
  container.appendChild(element);
  setTimeout(() => {
    element.classList.add('exiting');
    setTimeout(() => element.remove(), 240);
  }, 4000);
}

let openModalCount = 0;

export function openModal({
  title,
  subtitle = '',
  body = '',
  footer = '',
  size = '',
  onMount,
  onClose,
  closeOnBackdrop = true,
} = {}) {
  const backdrop = document.createElement('div');
  backdrop.className = 'modal-backdrop';
  backdrop.innerHTML = `
    <div class="modal-card ${size}" role="dialog" aria-modal="true">
      <div class="modal-header">
        <div>
          <h2 class="modal-title">${escapeHtml(title || '')}</h2>
          ${subtitle ? `<p class="modal-subtitle">${escapeHtml(subtitle)}</p>` : ''}
        </div>
        <button type="button" class="modal-close" data-modal-close aria-label="Close">
          ${icon('bx-x')}
        </button>
      </div>
      <div class="modal-body">${body}</div>
      ${footer ? `<div class="modal-footer">${footer}</div>` : ''}
    </div>
  `;

  document.body.appendChild(backdrop);
  openModalCount += 1;
  document.body.style.overflow = 'hidden';

  let closed = false;
  const previousActive = document.activeElement;

  const close = () => {
    if (closed) return;
    closed = true;
    backdrop.classList.add('closing');
    document.removeEventListener('keydown', onKeyDown);
    openModalCount = Math.max(0, openModalCount - 1);
    if (openModalCount === 0) document.body.style.overflow = '';
    setTimeout(() => backdrop.remove(), 190);
    if (previousActive && typeof previousActive.focus === 'function') {
      try {
        previousActive.focus();
      } catch (error) {
        /* ignore */
      }
    }
    if (onClose) onClose();
  };

  const onKeyDown = (event) => {
    if (event.key === 'Escape') close();
  };

  qsa('[data-modal-close]', backdrop).forEach((button) => {
    button.addEventListener('click', close);
  });

  if (closeOnBackdrop) {
    backdrop.addEventListener('click', (event) => {
      if (event.target === backdrop) close();
    });
  }

  document.addEventListener('keydown', onKeyDown);

  if (onMount) onMount(backdrop, close);

  requestAnimationFrame(() => {
    const focusTarget = qs(
      'input:not([type="hidden"]):not([disabled]), select:not([disabled]), textarea:not([disabled]), button:not([data-modal-close])',
      backdrop,
    );
    if (focusTarget) focusTarget.focus();
  });

  return { root: backdrop, card: qs('.modal-card', backdrop), close };
}

export function confirmDialog({
  title,
  message,
  details = '',
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  danger = false,
} = {}) {
  return new Promise((resolve) => {
    let settled = false;
    openModal({
      title,
      size: 'narrow',
      body: `
        <p class="muted" style="line-height:1.65">${escapeHtml(message || '')}</p>
        ${details ? `<div class="modal-note mt-16">${details}</div>` : ''}
      `,
      footer: `
        <button type="button" class="btn btn-secondary" data-modal-close>${escapeHtml(cancelLabel)}</button>
        <button type="button" class="btn ${danger ? 'btn-danger' : 'btn-primary'}" data-confirm>
          ${escapeHtml(confirmLabel)}
        </button>
      `,
      onMount(root, close) {
        qs('[data-confirm]', root).addEventListener('click', () => {
          settled = true;
          resolve(true);
          close();
        });
      },
      onClose() {
        if (!settled) resolve(false);
      },
    });
  });
}

export function busyButton(button, busy, busyLabel = 'Working...') {
  if (!button) return;
  if (busy) {
    if (!button.dataset.idleHtml) button.dataset.idleHtml = button.innerHTML;
    button.disabled = true;
    button.innerHTML = `${icon('bx-loader-alt', 'bx-spin')}<span>${escapeHtml(busyLabel)}</span>`;
  } else {
    button.disabled = false;
    if (button.dataset.idleHtml) {
      button.innerHTML = button.dataset.idleHtml;
      delete button.dataset.idleHtml;
    }
  }
}

export function debounce(callback, wait = 300) {
  let timer = null;
  const wrapped = (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => callback(...args), wait);
  };
  wrapped.cancel = () => clearTimeout(timer);
  return wrapped;
}

export function idempotencyKey() {
  if (window.crypto && typeof window.crypto.randomUUID === 'function') {
    return window.crypto.randomUUID();
  }
  return `idem-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export function emptyState(iconName, title, text) {
  return `
    <div class="empty-state">
      ${icon(iconName)}
      <h3>${escapeHtml(title)}</h3>
      <p>${escapeHtml(text)}</p>
    </div>
  `;
}

export function pageHeader(title, subtitle = '', actions = '') {
  return `
    <div class="page-header">
      <div>
        <h1 class="page-title">${escapeHtml(title)}</h1>
        ${subtitle ? `<p class="page-subtitle">${escapeHtml(subtitle)}</p>` : ''}
      </div>
      ${actions ? `<div class="page-actions">${actions}</div>` : ''}
    </div>
  `;
}

export function skeletonRows(columns, rows = 5) {
  return Array.from(
    { length: rows },
    () => `<tr><td colspan="${columns}"><div class="skeleton-row"></div></td></tr>`,
  ).join('');
}

export function statusBadge(active) {
  return active
    ? '<span class="badge badge-active">Active</span>'
    : '<span class="badge badge-inactive">Inactive</span>';
}

export function printHtml(title, html) {
  const printWindow = window.open('', '_blank', 'width=440,height=680');
  if (!printWindow) {
    toast('Allow pop-ups to print this receipt.', 'error');
    return;
  }
  printWindow.document.open();
  printWindow.document.write(`<!doctype html>
<html>
<head>
  <meta charset="utf-8">
  <title>${escapeHtml(title)}</title>
  <style>
    * { box-sizing: border-box; }
    body { margin: 0; padding: 26px; font-family: 'Segoe UI', system-ui, sans-serif; color: #111827; }
    h1 { font-size: 20px; margin: 0 0 4px; }
    p { margin: 0; color: #64748b; font-size: 13px; }
    .receipt-head { text-align: center; padding-bottom: 18px; border-bottom: 1px dashed #cbd5e1; }
    .receipt-check { width: 54px; height: 54px; border-radius: 50%; margin: 0 auto 12px; display: flex; align-items: center; justify-content: center; font-size: 28px; background: #d1fae5; color: #059669; }
    .receipt-rows { margin-top: 18px; }
    .receipt-row { display: flex; justify-content: space-between; gap: 18px; padding: 11px 0; border-bottom: 1px dashed #e2e8f0; font-size: 13px; }
    .receipt-row:last-child { border-bottom: none; }
    .receipt-row span:first-child { color: #64748b; }
    .receipt-row strong { text-align: right; }
    .receipt-foot { margin-top: 22px; text-align: center; font-size: 11px; color: #94a3b8; }
  </style>
</head>
<body>
  ${html}
  <script>
    window.onload = function () {
      setTimeout(function () { window.print(); window.close(); }, 150);
    };
  <\/script>
</body>
</html>`);
  printWindow.document.close();
}

export function downloadBlob(url, filename) {
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
}

let audioContext = null;

export function playTone(success = true) {
  try {
    const AudioCtor = window.AudioContext || window.webkitAudioContext;
    if (!AudioCtor) return;
    if (!audioContext) audioContext = new AudioCtor();
    if (audioContext.state === 'suspended') audioContext.resume().catch(() => {});
    const oscillator = audioContext.createOscillator();
    const gain = audioContext.createGain();
    oscillator.type = 'sine';
    oscillator.frequency.value = success ? 880 : 220;
    gain.gain.setValueAtTime(0.0001, audioContext.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.12, audioContext.currentTime + 0.01);
    gain.gain.exponentialRampToValueAtTime(0.0001, audioContext.currentTime + 0.18);
    oscillator.connect(gain);
    gain.connect(audioContext.destination);
    oscillator.start();
    oscillator.stop(audioContext.currentTime + 0.2);
  } catch (error) {
    /* Sound is optional. */
  }
}
