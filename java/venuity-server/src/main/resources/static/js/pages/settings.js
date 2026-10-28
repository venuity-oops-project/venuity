import { api } from '../api.js';
import {
  state,
  updateSettings as applySettings,
} from '../store.js';
import {
  busyButton,
  escapeHtml,
  icon,
  openModal,
  pageHeader,
  qs,
  toast,
} from '../ui.js';

const HEX_COLOR = /^#[0-9a-f]{6}$/i;

export function mount(root) {
  const isAdmin = state.user && state.user.role === 'ADMIN';
  const initial = state.settings;

  const headerActions = isAdmin
    ? `<button type="button" class="btn btn-primary" id="btn-header-add-user">${icon('bx-user-plus')} Add user</button>`
    : '';

  root.innerHTML = `
    ${pageHeader('Settings', 'Configure your business, branding, fees and staff accounts.', headerActions)}
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

        <hr style="border:none;border-top:1px solid var(--glass-border);margin:24px 0">

        <h3 class="settings-section-title">${icon('bx-palette')} Branding &amp; white-label</h3>

        <div class="form-group">
          <label for="settings-app-title">Application title</label>
          <input class="form-input" id="settings-app-title" maxlength="150"
                 value="${escapeHtml(initial.appTitle)}" ${isAdmin ? '' : 'disabled'}>
          <small class="muted" style="display:block;margin-top:6px">Shown on the login screen and browser tab.</small>
        </div>

        <div class="form-group">
          <label for="settings-logo">Logo URL</label>
          <input class="form-input" id="settings-logo" maxlength="255"
                 value="${escapeHtml(initial.logoUrl)}" ${isAdmin ? '' : 'disabled'}>
        </div>

        <div class="form-grid">
          <div class="form-group">
            <label for="settings-primary-text">Primary color</label>
            <div style="display:flex;align-items:center;gap:12px">
              <input type="color" id="settings-primary-picker" value="${escapeHtml(initial.primaryColor)}"
                     style="width:48px;height:48px;padding:2px;border:2px solid var(--glass-border);border-radius:10px;cursor:pointer;background:transparent"
                     ${isAdmin ? '' : 'disabled'} aria-label="Primary color picker">
              <input class="form-input" id="settings-primary-text" maxlength="7" value="${escapeHtml(initial.primaryColor)}"
                     style="font-family:monospace" ${isAdmin ? '' : 'disabled'} aria-label="Primary color hex">
            </div>
          </div>
          <div class="form-group">
            <label for="settings-hover-text">Hover color</label>
            <div style="display:flex;align-items:center;gap:12px">
              <input type="color" id="settings-hover-picker" value="${escapeHtml(initial.primaryHoverColor)}"
                     style="width:48px;height:48px;padding:2px;border:2px solid var(--glass-border);border-radius:10px;cursor:pointer;background:transparent"
                     ${isAdmin ? '' : 'disabled'} aria-label="Hover color picker">
              <input class="form-input" id="settings-hover-text" maxlength="7" value="${escapeHtml(initial.primaryHoverColor)}"
                     style="font-family:monospace" ${isAdmin ? '' : 'disabled'} aria-label="Hover color hex">
            </div>
          </div>
        </div>

        <div class="form-group">
          <div class="preview-label" style="margin-bottom:8px">Gradient preview</div>
          <div id="settings-gradient" style="height:42px;border-radius:10px"></div>
        </div>

        <div style="margin-top:26px">
          <button type="submit" class="btn btn-primary" id="settings-save"
                  style="width:100%;justify-content:center" ${isAdmin ? '' : 'disabled'}>
            ${icon('bx-save')} Save settings
          </button>
        </div>
      </form>

      <aside class="settings-preview">
        <div class="preview-banner" id="preview-banner">
          <img class="preview-logo" id="preview-logo" alt="Business logo">
          <div class="preview-name" id="preview-name"></div>
          <div class="preview-app" id="preview-app"></div>
        </div>
        <div class="glass-card" style="padding:18px">
          <div class="preview-label">Primary color</div>
          <div class="preview-swatch" id="preview-primary"></div>
          <div class="preview-label">Hover color</div>
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
        <div class="glass-card" style="padding:18px;margin-top:16px">
          <div class="preview-label" style="display:flex;align-items:center;gap:6px">
            ${icon('bx-user')} User accounts
          </div>
          <p class="muted" style="font-size:12px;line-height:1.5;margin:8px 0 14px">
            Add staff or administrator accounts for your team.
          </p>
          <button type="button" class="btn btn-secondary" id="btn-aside-add-user" style="width:100%;justify-content:center" ${isAdmin ? '' : 'disabled'}>
            ${icon('bx-user-plus')} Add new user
          </button>
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
  const gradient = qs('#settings-gradient', root);
  const saveButton = qs('#settings-save', root);

  const banner = qs('#preview-banner', root);
  const previewLogo = qs('#preview-logo', root);
  const previewName = qs('#preview-name', root);
  const previewApp = qs('#preview-app', root);
  const previewPrimary = qs('#preview-primary', root);
  const previewHover = qs('#preview-hover', root);
  const previewFee = qs('#preview-fee', root);

  let dirty = false;

  function safeColor(value, fallback) {
    const text = String(value || '').trim();
    return HEX_COLOR.test(text) ? text : fallback;
  }

  function updateGradient() {
    const primary = safeColor(primaryText.value, '#6366f1');
    const hover = safeColor(hoverText.value, '#4f46e5');
    const background = `linear-gradient(135deg, ${primary}, ${hover})`;
    gradient.style.background = background;
    banner.style.background = background;
    previewPrimary.style.background = primary;
    previewHover.style.background = hover;
  }

  function updatePreview() {
    const name = businessName.value.trim() || 'Your business';
    const title = appTitle.value.trim() || name;
    previewName.textContent = name;
    previewApp.textContent = title;

    const symbol = currency.value.trim();
    const amount = Number(fee.value);
    previewFee.textContent =
      Number.isFinite(amount) && amount >= 0
        ? `${symbol}${amount.toLocaleString(undefined, {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2,
          })}`
        : '—';

    const source = logo.value.trim() || '/logo.png';
    previewLogo.style.display = '';
    previewLogo.src = source;
    updateGradient();
  }

  function populate(settings) {
    businessName.value = settings.businessName || '';
    fee.value = settings.checkinFee === undefined || settings.checkinFee === null ? '' : String(settings.checkinFee);
    currency.value = settings.currencySymbol || '';
    appTitle.value = settings.appTitle || '';
    logo.value = settings.logoUrl || '/logo.png';

    const primary = safeColor(settings.primaryColor, '#6366f1');
    const hover = safeColor(settings.primaryHoverColor, '#4f46e5');
    primaryText.value = primary;
    primaryPicker.value = primary;
    hoverText.value = hover;
    hoverPicker.value = hover;

    dirty = false;
    updatePreview();
  }

  populate(initial);

  previewLogo.addEventListener('error', () => {
    previewLogo.style.display = 'none';
  });

  function bindColor(textInput, picker, otherInput) {
    picker.addEventListener('input', () => {
      textInput.value = picker.value;
      dirty = true;
      updatePreview();
    });
    textInput.addEventListener('input', () => {
      dirty = true;
      const value = textInput.value.trim();
      if (HEX_COLOR.test(value)) {
        picker.value = value;
        updatePreview();
      } else if (value === '') {
        updatePreview();
      }
    });
    textInput.addEventListener('blur', () => {
      const fallback = otherInput === primaryText ? '#6366f1' : '#4f46e5';
      if (!HEX_COLOR.test(textInput.value.trim())) {
        textInput.value = safeColor(picker.value, fallback);
        updatePreview();
      }
    });
  }

  bindColor(primaryText, primaryPicker, hoverText);
  bindColor(hoverText, hoverPicker, primaryText);

  [businessName, fee, currency, appTitle, logo].forEach((input) => {
    input.addEventListener('input', () => {
      dirty = true;
      updatePreview();
    });
  });

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (!isAdmin) return;

    const payload = {
      businessName: businessName.value.trim(),
      checkinFee: fee.value.trim(),
      currencySymbol: currency.value.trim(),
      primaryColor: primaryText.value.trim(),
      primaryHoverColor: hoverText.value.trim(),
      appTitle: appTitle.value.trim() || businessName.value.trim(),
      logoUrl: logo.value.trim() || '/logo.png',
    };

    if (!payload.businessName || !payload.currencySymbol) {
      toast('Business name and currency symbol are required.', 'error');
      return;
    }
    if (!Number.isFinite(Number(payload.checkinFee)) || Number(payload.checkinFee) < 0) {
      toast('Enter a valid check-in fee.', 'error');
      return;
    }
    if (!HEX_COLOR.test(payload.primaryColor) || !HEX_COLOR.test(payload.primaryHoverColor)) {
      toast('Colors must be six-digit hex values, for example #6366f1.', 'error');
      return;
    }

    busyButton(saveButton, true, 'Saving...');
    try {
      const response = await api.updateSettings(payload);
      applySettings(response);
      populate(response);
      toast('Settings saved successfully!', 'success');
    } catch (error) {
      toast(error.message, 'error');
    } finally {
      busyButton(saveButton, false);
      if (!isAdmin) saveButton.disabled = true;
    }
  });

  function openAddUserModal() {
    openModal({
      title: 'Add New User',
      subtitle: 'Create a staff or administrator login account.',
      size: 'narrow',
      body: `
        <form id="new-user-form" autocomplete="off">
          <div id="new-user-error" style="display:none;margin-bottom:16px;padding:10px 14px;border-radius:10px;background:rgba(239,68,68,0.15);border:1px solid rgba(239,68,68,0.3);color:#ef4444;font-size:13px"></div>
          <div class="form-group">
            <label for="nu-username">Username *</label>
            <input class="form-input" id="nu-username" maxlength="150" required placeholder="e.g. cashier1" autocomplete="new-username">
          </div>
          <div class="form-group">
            <label for="nu-password">Password *</label>
            <input class="form-input" id="nu-password" type="password" required minlength="4" placeholder="Minimum 4 characters" autocomplete="new-password">
          </div>
          <div class="form-group">
            <label for="nu-role">Role *</label>
            <select class="form-input" id="nu-role" required style="cursor:pointer">
              <option value="STAFF" selected>Staff (Recharge, Check-in, View)</option>
              <option value="ADMIN">Admin (Full administrative access)</option>
            </select>
          </div>
          <div class="form-group">
            <label for="nu-email">Email (optional)</label>
            <input class="form-input" id="nu-email" type="email" maxlength="254" placeholder="staff@playarea.com">
          </div>
          <div class="form-grid">
            <div class="form-group">
              <label for="nu-firstname">First name (optional)</label>
              <input class="form-input" id="nu-firstname" maxlength="150">
            </div>
            <div class="form-group">
              <label for="nu-lastname">Last name (optional)</label>
              <input class="form-input" id="nu-lastname" maxlength="150">
            </div>
          </div>
        </form>
      `,
      footer: `
        <button type="button" class="btn btn-secondary" data-modal-close>Cancel</button>
        <button type="submit" form="new-user-form" class="btn btn-primary" id="nu-submit">
          ${icon('bx-user-plus')} Create user
        </button>
      `,
      onMount(modalRoot, close) {
        const userForm = qs('#new-user-form', modalRoot);
        const usernameInput = qs('#nu-username', modalRoot);
        const passwordInput = qs('#nu-password', modalRoot);
        const roleSelect = qs('#nu-role', modalRoot);
        const emailInput = qs('#nu-email', modalRoot);
        const firstNameInput = qs('#nu-firstname', modalRoot);
        const lastNameInput = qs('#nu-lastname', modalRoot);
        const submitBtn = qs('#nu-submit', modalRoot);
        const errorBox = qs('#new-user-error', modalRoot);

        userForm.addEventListener('submit', async (e) => {
          e.preventDefault();
          errorBox.style.display = 'none';

          const uname = usernameInput.value.trim();
          const pwd = passwordInput.value;
          if (!uname) {
            errorBox.textContent = 'Username is required.';
            errorBox.style.display = 'block';
            return;
          }
          if (!pwd || pwd.length < 4) {
            errorBox.textContent = 'Password must be at least 4 characters.';
            errorBox.style.display = 'block';
            return;
          }

          busyButton(submitBtn, true, 'Creating...');
          try {
            const newUser = await api.createUser({
              username: uname,
              password: pwd,
              role: roleSelect.value,
              email: emailInput.value.trim() || undefined,
              firstName: firstNameInput.value.trim() || undefined,
              lastName: lastNameInput.value.trim() || undefined,
            });
            toast(`User '${newUser.username}' created successfully as ${newUser.role}!`, 'success');
            close();
          } catch (err) {
            errorBox.textContent = err.message || 'Failed to create user.';
            errorBox.style.display = 'block';
          } finally {
            busyButton(submitBtn, false);
          }
        });
      },
    });
  }

  const headerAddUserBtn = qs('#btn-header-add-user', root);
  if (headerAddUserBtn) {
    headerAddUserBtn.addEventListener('click', openAddUserModal);
  }

  const asideAddUserBtn = qs('#btn-aside-add-user', root);
  if (asideAddUserBtn) {
    asideAddUserBtn.addEventListener('click', openAddUserModal);
  }

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
