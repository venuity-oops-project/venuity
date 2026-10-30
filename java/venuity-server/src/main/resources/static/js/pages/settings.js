/**
 * Settings page: business configuration, fee, currency and branding controls.
 */

import { api } from '../api.js';
import { state, updateSettings as applySettings } from '../store.js';
import {
  escapeHtml,
  icon,
  pageHeader,
  qs,
  qsa,
  toast,
  busyButton,
} from '../ui.js';

const HEX_COLOR = /^#[0-9a-f]{6}$/i;

export function mount(root) {
  const isAdmin = state.user && state.user.role === 'ADMIN';
  const initial = state.settings;

  root.innerHTML = `
    ${pageHeader('Settings', 'Configure your business, branding, currency and check-in fees.')}
    ${isAdmin ? '' : `
      <div class="admin-notice">
        ${icon('bx-lock-alt')}
        <span>Only administrators can change these settings. You can review the current configuration here.</span>
      </div>
    `}
    <div class="settings-layout">
      <form class="glass-card" id="settings-form" style="padding:24px" autocomplete="off">
        <h3 class="settings-section-title">${icon('bx-store')} Business configuration</h3>

        <div class="form-group">
          <label for="settings-business-name">Business name</label>
          <input class="form-input" id="settings-business-name" maxlength="150" required
                 value="${escapeHtml(initial.businessName)}" ${isAdmin ? '' : 'disabled'}>
        </div>

        <div class="form-grid">
          <div class="form-group">
            <label for="settings-fee">Check-in fee</label>
            <input class="form-input" id="settings-fee" type="number" min="0" step="0.01" required
                   value="${escapeHtml(initial.checkinFee)}" ${isAdmin ? '' : 'disabled'}>
          </div>
          <div class="form-group">
            <label for="settings-currency">Currency symbol</label>
            <input class="form-input" id="settings-currency" maxlength="8" required
                   value="${escapeHtml(initial.currencySymbol)}" ${isAdmin ? '' : 'disabled'}>
          </div>
        </div>

        <div class="form-group">
          <label for="settings-app-title">Browser title</label>
          <input class="form-input" id="settings-app-title" maxlength="150" required
                 value="${escapeHtml(initial.appTitle)}" ${isAdmin ? '' : 'disabled'}>
          <small class="muted" style="display:block;margin-top:6px">Shown on the login screen and browser tab.</small>
        </div>

        <h3 class="settings-section-title" style="margin-top:28px">${icon('bx-palette')} Branding &amp; appearance</h3>

        <div class="form-group">
          <label for="settings-logo">Logo URL or path</label>
          <input class="form-input" id="settings-logo" maxlength="255" required
                 value="${escapeHtml(initial.logoUrl)}" ${isAdmin ? '' : 'disabled'}>
          <small class="muted" style="display:block;margin-top:6px">Relative path (/logo.png) or full https:// URL.</small>
        </div>

        <div class="form-grid">
          <div class="form-group">
            <label for="settings-primary-text">Primary colour</label>
            <div class="color-picker-input">
              <input type="color" class="color-swatch-picker" id="settings-primary-picker"
                     value="${escapeHtml(initial.primaryColor)}" ${isAdmin ? '' : 'disabled'}>
              <input class="form-input" id="settings-primary-text" maxlength="7" required
                     value="${escapeHtml(initial.primaryColor)}" ${isAdmin ? '' : 'disabled'}>
            </div>
          </div>
          <div class="form-group">
            <label for="settings-hover-text">Hover colour</label>
            <div class="color-picker-input">
              <input type="color" class="color-swatch-picker" id="settings-hover-picker"
                     value="${escapeHtml(initial.primaryHoverColor)}" ${isAdmin ? '' : 'disabled'}>
              <input class="form-input" id="settings-hover-text" maxlength="7" required
                     value="${escapeHtml(initial.primaryHoverColor)}" ${isAdmin ? '' : 'disabled'}>
            </div>
          </div>
        </div>

        <div class="settings-actions">
          <button type="submit" class="btn btn-primary" id="settings-save" ${isAdmin ? '' : 'disabled'}>
            ${icon('bx-save')} Save changes
          </button>
          <button type="button" class="btn btn-secondary" id="settings-reset" ${isAdmin ? '' : 'disabled'}>
            Reset
          </button>
        </div>
      </form>

      <aside class="settings-preview">
        <div class="glass-card" style="padding:18px">
          <div class="preview-label">Live appearance preview</div>
          <div class="preview-banner" id="preview-banner">
            <img class="preview-banner-logo" id="preview-logo" src="${escapeHtml(initial.logoUrl)}" alt="Logo">
            <div>
              <div style="font-weight:700;font-size:15px" id="preview-title">${escapeHtml(initial.appTitle)}</div>
              <div class="muted" style="font-size:12px" id="preview-business">${escapeHtml(initial.businessName)}</div>
            </div>
          </div>
          <div class="preview-btn-mock" id="preview-btn">Primary button sample</div>
          <div class="preview-swatch" id="preview-hover"></div>
          <p class="muted" style="font-size:12px;line-height:1.5;margin:12px 0 0">
            Changes preview live. Save to apply them for everyone.
          </p>
        </div>
        <div class="glass-card" style="padding:18px;margin-top:16px">
          <div class="preview-label">Check-in fee</div>
          <div style="font-size:24px;font-weight:700;margin-top:4px" id="preview-fee"></div>
          <p class="muted" style="font-size:12px;line-height:1.5;margin:8px 0 0">
            Deducted from the customer wallet on every check-in.
          </p>
        </div>
      </aside>
    </div>
  `;

  const form = qs('#settings-form', root);
  const businessName = qs('#settings-business-name', root);
  const fee = qs('#settings-fee', root);
  const currency = qs('#settings-currency', root);
  const appTitle = qs('#settings-app-title', root);
  const logo = qs('#settings-logo', root);
  const primaryText = qs('#settings-primary-text', root);
  const primaryPicker = qs('#settings-primary-picker', root);
  const hoverText = qs('#settings-hover-text', root);
  const hoverPicker = qs('#settings-hover-picker', root);
  const saveButton = qs('#settings-save', root);

  const banner = qs('#preview-banner', root);
  const previewLogo = qs('#preview-logo', root);
  const previewTitle = qs('#preview-title', root);
  const previewBusiness = qs('#preview-business', root);
  const previewBtn = qs('#preview-btn', root);
  const previewHover = qs('#preview-hover', root);
  const previewFee = qs('#preview-fee', root);

  let dirty = false;

  function updatePreview() {
    const pColor = primaryText.value.trim();
    const hColor = hoverText.value.trim();
    const feeVal = parseFloat(fee.value) || 0;
    const curVal = currency.value.trim() || '₹';

    previewTitle.textContent = appTitle.value.trim() || 'Venuity';
    previewBusiness.textContent = businessName.value.trim() || 'PlayArea Manager';
    previewFee.textContent = `${curVal} ${feeVal.toFixed(2)}`;

    if (HEX_COLOR.test(pColor)) {
      previewBtn.style.background = pColor;
      banner.style.borderColor = pColor;
    }
    if (HEX_COLOR.test(hColor)) {
      previewHover.style.background = hColor;
    }
    if (logo.value.trim()) {
      previewLogo.src = logo.value.trim();
      previewLogo.style.display = '';
    } else {
      previewLogo.style.display = 'none';
    }
  }

  function syncColors(source, target) {
    if (HEX_COLOR.test(source.value)) {
      target.value = source.value;
      updatePreview();
    }
  }

  primaryPicker.addEventListener('input', () => {
    primaryText.value = primaryPicker.value;
    dirty = true;
    updatePreview();
  });
  primaryText.addEventListener('input', () => {
    dirty = true;
    syncColors(primaryText, primaryPicker);
  });

  hoverPicker.addEventListener('input', () => {
    hoverText.value = hoverPicker.value;
    dirty = true;
    updatePreview();
  });
  hoverText.addEventListener('input', () => {
    dirty = true;
    syncColors(hoverText, hoverPicker);
  });

  [businessName, fee, currency, appTitle, logo].forEach((input) => {
    input.addEventListener('input', () => {
      dirty = true;
      updatePreview();
    });
  });

  previewLogo.addEventListener('error', () => {
    previewLogo.style.display = 'none';
  });

  function populate(data) {
    businessName.value = data.businessName || '';
    fee.value = data.checkinFee !== undefined ? data.checkinFee : '';
    currency.value = data.currencySymbol || '';
    appTitle.value = data.appTitle || '';
    logo.value = data.logoUrl || '';
    primaryText.value = data.primaryColor || '#6366f1';
    primaryPicker.value = HEX_COLOR.test(primaryText.value) ? primaryText.value : '#6366f1';
    hoverText.value = data.primaryHoverColor || '#4f46e5';
    hoverPicker.value = HEX_COLOR.test(hoverText.value) ? hoverText.value : '#4f46e5';
    updatePreview();
  }

  qs('#settings-reset', root).addEventListener('click', () => {
    dirty = false;
    populate(state.settings);
  });

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (!isAdmin) return;

    const pColor = primaryText.value.trim();
    const hColor = hoverText.value.trim();

    if (!HEX_COLOR.test(pColor) || !HEX_COLOR.test(hColor)) {
      toast('Colour values must be valid 6-character hex codes (e.g. #6366f1)', 'error');
      return;
    }

    const payload = {
      businessName: businessName.value.trim(),
      currencySymbol: currency.value.trim(),
      checkinFee: parseFloat(fee.value) || 0,
      primaryColor: pColor,
      primaryHoverColor: hColor,
      logoUrl: logo.value.trim(),
      appTitle: appTitle.value.trim(),
    };

    busyButton(saveButton, true, 'Saving...');
    try {
      const updated = await api.updateSettings(payload);
      applySettings(updated);
      dirty = false;
      toast('Settings saved successfully', 'success');
    } catch (error) {
      toast(error.message, 'error');
    } finally {
      busyButton(saveButton, false);
      if (!isAdmin) saveButton.disabled = true;
    }
  });

  api
    .settings()
    .then((settings) => {
      if (dirty) return;
      applySettings(settings);
      populate(settings);
    })
    .catch((error) => {
      if (error.status !== 401) toast(error.message, 'error');
    });

  updatePreview();
}
