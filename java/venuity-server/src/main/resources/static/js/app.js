import { api, clearTokens, getAccessToken, getRefreshToken } from './api.js';
import {
  applyBranding,
  initials,
  roleLabel,
  state,
  toggleTheme,
  updateSettings as applySettings,
} from './store.js';
import {
  busyButton,
  emptyState,
  escapeHtml,
  icon,
  qs,
  qsa,
  toast,
  openModal,
  confirmDialog,
} from './ui.js';
import { mount as mountDashboard } from './pages/dashboard.js';
import { mount as mountCustomers } from './pages/customers.js';
import { mount as mountRecharge } from './pages/recharge.js';
import { mount as mountCheckin } from './pages/checkin.js';
import { mount as mountAnalytics } from './pages/analytics.js';
import { mount as mountSettings } from './pages/settings.js';
import { mount as mountUsers } from './pages/users.js';

const NAV_ITEMS = [
  { path: '/', icon: 'bx-home', label: 'Home' },
  { path: '/dashboard', icon: 'bx-grid-alt', label: 'Dashboard' },
  { path: '/customers', icon: 'bx-user', label: 'Customers' },
  { path: '/recharge', icon: 'bx-wallet', label: 'Recharge' },
  { path: '/scan', icon: 'bx-qr-scan', label: 'Check-in' },
  { path: '/analytics', icon: 'bx-bar-chart-alt-2', label: 'Analytics' },
  { path: '/users', icon: 'bx-group', label: 'Users', adminOnly: true },
  { path: '/settings', icon: 'bx-cog', label: 'Settings' },
];

const HOME_CARDS = [
  { path: '/dashboard', cls: 'card-dashboard', icon: 'bx-grid-alt', label: 'Dashboard' },
  { path: '/customers', cls: 'card-customers', icon: 'bx-user', label: 'Customers' },
  { path: '/recharge', cls: 'card-recharge', icon: 'bx-wallet', label: 'Recharge' },
  { path: '/scan', cls: 'card-scan', icon: 'bx-qr-scan', label: 'Check-in' },
  { path: '/analytics', cls: 'card-analytics', icon: 'bx-bar-chart-alt-2', label: 'Analytics' },
  { path: '/users', cls: 'card-users', icon: 'bx-group', label: 'Users', adminOnly: true },
  { path: '/settings', cls: 'card-settings', icon: 'bx-cog', label: 'Settings' },
];

const ROUTES = {
  '/': { title: 'Home' },
  '/dashboard': { title: 'Dashboard', mount: mountDashboard },
  '/customers': { title: 'Customers & QR Codes', mount: mountCustomers },
  '/recharge': { title: 'Recharge Balance', mount: mountRecharge },
  '/scan': { title: 'Scanner Check-in', mount: mountCheckin },
  '/analytics': { title: 'Business Statistics', mount: mountAnalytics },
  '/users': { title: 'User Management', mount: mountUsers, adminOnly: true },
  '/settings': { title: 'Settings', mount: mountSettings },
};

let appRoot = null;
let pageCleanups = [];
let shellCleanups = [];
let healthTimer = null;
let sidebarCollapsed = localStorage.getItem('desktop_sidebar_state') === 'closed';
let mobileSidebarOpen = false;
let forcePasswordModalActive = false;

function isMobile() {
  return window.innerWidth <= 768;
}

function currentPath() {
  const raw = location.hash.replace(/^#/, '');
  if (!raw) return '/';
  const path = raw.split('?')[0];
  return path.startsWith('/') ? path : `/${path}`;
}

function addPageCleanup(cleanup) {
  if (typeof cleanup === 'function') pageCleanups.push(cleanup);
}

function addShellCleanup(cleanup) {
  if (typeof cleanup === 'function') shellCleanups.push(cleanup);
}

function destroyPage() {
  const cleanups = pageCleanups;
  pageCleanups = [];
  cleanups.forEach((cleanup) => {
    try {
      cleanup();
    } catch (error) {
      console.error(error);
    }
  });
}

function stopHealthPolling() {
  if (healthTimer) {
    clearInterval(healthTimer);
    healthTimer = null;
  }
}

function teardownShell() {
  stopHealthPolling();
  const cleanups = shellCleanups;
  shellCleanups = [];
  cleanups.forEach((cleanup) => {
    try {
      cleanup();
    } catch (error) {
      console.error(error);
    }
  });
}

function closeMobileSidebar() {
  if (!mobileSidebarOpen) return;
  mobileSidebarOpen = false;
  const sidebar = qs('#sidebar', appRoot);
  const overlay = qs('#sidebar-overlay', appRoot);
  const closeButton = qs('#sidebar-close', appRoot);
  if (sidebar) sidebar.classList.remove('mobile-open');
  if (overlay) overlay.classList.remove('active');
  if (closeButton) closeButton.style.display = 'none';
}

function updateActiveNav(path) {
  qsa('.nav-links a', appRoot).forEach((link) => {
    const route = link.dataset.route;
    const active = route === '/' ? path === '/' : path === route || path.startsWith(`${route}/`);
    link.classList.toggle('active', active);
  });
}

function updateConnectionPill(online, label) {
  const pill = qs('#connection-pill', appRoot);
  const text = qs('#connection-text', appRoot);
  if (!pill || !text) return;

  pill.classList.remove('online', 'offline', 'checking');
  pill.classList.add(online ? 'online' : 'offline');
  text.textContent = label || (online ? 'Online' : 'Offline');
  pill.title = online ? 'Server reachable' : 'Cannot reach server';
}

async function pingHealth() {
  try {
    const health = await api.health();
    const ok = health && (health.status === 'UP' || health.database === 'UP');
    updateConnectionPill(ok, ok ? 'Online' : 'Degraded');
  } catch (error) {
    updateConnectionPill(false, 'Offline');
  }
}

function startHealthPolling() {
  stopHealthPolling();
  pingHealth();
  healthTimer = setInterval(pingHealth, 15000);
}

function updateThemeIcons() {
  const isDark = state.theme === 'dark';
  const iconName = isDark ? 'bx-sun' : 'bx-moon';
  const title = isDark ? 'Switch to light mode' : 'Switch to dark mode';

  const shellBtn = qs('#theme-toggle', appRoot);
  if (shellBtn) {
    shellBtn.innerHTML = icon(iconName);
    shellBtn.title = title;
  }
  const homeBtn = qs('#home-theme', appRoot);
  if (homeBtn) {
    homeBtn.innerHTML = icon(iconName);
    homeBtn.title = title;
  }
  const loginBtn = qs('#login-theme', appRoot);
  if (loginBtn) {
    loginBtn.innerHTML = icon(iconName);
    loginBtn.title = title;
  }
}

function updateBrandingDom() {
  const settings = state.settings;
  const logo = settings.logoUrl || '/logo.png';

  document.title = settings.appTitle || 'Venuity';

  const shellLogo = qs('#shell-logo', appRoot);
  if (shellLogo) {
    shellLogo.src = logo;
    shellLogo.style.display = '';
  }
  const shellName = qs('#shell-name', appRoot);
  if (shellName) shellName.textContent = settings.businessName;

  const homeLogo = qs('#home-logo', appRoot);
  if (homeLogo) {
    homeLogo.src = logo;
    homeLogo.style.display = '';
  }
  const homeTitle = qs('#home-title span', appRoot);
  if (homeTitle) homeTitle.textContent = settings.businessName;

  const loginLogo = qs('#login-logo', appRoot);
  if (loginLogo) {
    loginLogo.src = logo;
    loginLogo.style.display = '';
  }
  const loginTitle = qs('#login-title', appRoot);
  if (loginTitle) loginTitle.textContent = settings.businessName;
}

async function loadSettings() {
  try {
    const fresh = await api.settings();
    applySettings(fresh);
  } catch (error) {
    /* Cached branding is used when the public endpoint is unavailable. */
  }
}

async function loadPublicBranding() {
  try {
    const branding = await api.branding();
    applySettings(branding);
  } catch (error) {
    /* Use defaults in store.js */
  }
}

function renderLogin() {
  teardownShell();

  const settings = state.settings;
  const logo = settings.logoUrl || '/logo.png';
  const themeIcon = state.theme === 'dark' ? 'bx-sun' : 'bx-moon';
  const themeTitle = state.theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode';

  appRoot.innerHTML = `
    <div class="login-page">
      <div class="login-bg-orb login-bg-orb-1"></div>
      <div class="login-bg-orb login-bg-orb-2"></div>
      <div class="login-bg-orb login-bg-orb-3"></div>
      <div class="login-card">
        <div class="login-header">
          <div class="login-logo-wrapper">
            <img class="login-logo" id="login-logo" src="${escapeHtml(logo)}"
                 alt="${escapeHtml(settings.businessName)}"
                 onerror="this.style.display='none'">
          </div>
          <h1 class="login-title" id="login-title">${escapeHtml(settings.businessName)}</h1>
          <p class="login-subtitle">Sign in to your account</p>
        </div>
        <div class="login-error" id="login-error" hidden>
          ${icon('bx-error-circle')}
          <span id="login-error-text"></span>
        </div>
        <form class="login-form" id="login-form">
          <div class="login-field">
            <label for="login-username">Username</label>
            <div class="login-input-wrapper">
              ${icon('bx-user', 'login-input-icon')}
              <input id="login-username" type="text" autocomplete="username" required
                     placeholder="Enter your username">
            </div>
          </div>
          <div class="login-field">
            <label for="login-password">Password</label>
            <div class="login-input-wrapper">
              ${icon('bx-lock-alt', 'login-input-icon')}
              <input id="login-password" type="password" autocomplete="current-password" required
                     placeholder="Enter your password">
              <i class="bx bx-show login-toggle-password" id="login-toggle-password"
                 title="Show password" role="button" tabindex="0"></i>
            </div>
          </div>
          <button type="submit" class="login-submit-btn" id="login-submit" disabled>
            <span>Sign In</span>
            ${icon('bx-right-arrow-alt')}
          </button>
        </form>
        <p class="login-footer-text">Contact your administrator to create an account.</p>
      </div>
      <button type="button" class="login-theme-toggle" id="login-theme" title="${themeTitle}">
        ${icon(themeIcon)}
      </button>
    </div>
  `;

  const form = qs('#login-form', appRoot);
  const username = qs('#login-username', appRoot);
  const password = qs('#login-password', appRoot);
  const submit = qs('#login-submit', appRoot);
  const errorBox = qs('#login-error', appRoot);
  const errorText = qs('#login-error-text', appRoot);
  const toggle = qs('#login-toggle-password', appRoot);
  const logoElement = qs('#login-logo', appRoot);

  if (logoElement) {
    logoElement.addEventListener('error', () => {
      logoElement.style.display = 'none';
    });
  }

  let submitting = false;

  function updateSubmit() {
    submit.disabled = submitting || !username.value.trim() || !password.value;
  }

  function hideError() {
    errorBox.hidden = true;
    errorText.textContent = '';
  }

  function showError(message) {
    errorText.textContent = message || 'Unable to sign in. Please try again.';
    errorBox.hidden = false;
  }

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (submitting) return;

    hideError();
    submitting = true;
    username.disabled = true;
    password.disabled = true;
    busyButton(submit, true, 'Signing in...');

    try {
      const response = await api.login(username.value.trim(), password.value);
      state.user = response.user || (await api.me());
      await loadSettings();
      if (!state.user) return;
      renderShell();
      handleRoute();
    } catch (error) {
      submitting = false;
      username.disabled = false;
      password.disabled = false;
      busyButton(submit, false);
      updateSubmit();
      showError(error.message);
      username.focus();
    }
  });

  username.addEventListener('input', () => {
    hideError();
    updateSubmit();
  });
  password.addEventListener('input', () => {
    hideError();
    updateSubmit();
  });

  function togglePasswordVisibility() {
    const show = password.type === 'password';
    password.type = show ? 'text' : 'password';
    toggle.className = `bx ${show ? 'bx-hide' : 'bx-show'} login-toggle-password`;
    toggle.title = show ? 'Hide password' : 'Show password';
  }

  toggle.addEventListener('click', togglePasswordVisibility);
  toggle.addEventListener('keydown', (event) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      togglePasswordVisibility();
    }
  });

  qs('#login-theme', appRoot).addEventListener('click', () => toggleTheme());

  setTimeout(() => username.focus(), 50);
}

function renderShell() {
  teardownShell();

  const user = state.user || {};
  const settings = state.settings;
  const logo = settings.logoUrl || '/logo.png';
  const displayName = user.fullName || user.username || 'User';
  const role = roleLabel(user.role);
  const collapsed = sidebarCollapsed && !isMobile();
  const themeIcon = state.theme === 'dark' ? 'bx-sun' : 'bx-moon';
  const themeTitle = state.theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode';

  appRoot.innerHTML = `
    <div class="app-container">
      <nav class="sidebar ${collapsed ? 'collapsed' : ''}" id="sidebar">
        <div class="sidebar-header">
          <img class="logo-img" id="shell-logo" src="${escapeHtml(logo)}" alt="Logo">
          <span class="logo-name" id="shell-name">${escapeHtml(settings.businessName)}</span>
          <button type="button" class="icon-btn" id="sidebar-close" style="display:none;margin-left:auto" aria-label="Close menu">
            ${icon('bx-x')}
          </button>
        </div>
        <ul class="nav-links">
          ${NAV_ITEMS
      .filter((item) => !item.adminOnly || (user.role === 'ADMIN'))
      .map(
        (item) => `
                <li>
                  <a href="#${item.path}" data-route="${item.path}">
                    ${icon(item.icon)}
                    <span>${escapeHtml(item.label)}</span>
                  </a>
                </li>
              `,
      ).join('')}
        </ul>
        <div class="sidebar-footer">
          <a href="#" id="sidebar-logout">
            ${icon('bx-log-out')}
            <span>Logout</span>
          </a>
        </div>
      </nav>
      <div class="sidebar-overlay" id="sidebar-overlay"></div>
      <section class="main-content">
        <header class="topbar">
          <div class="topbar-left">
            <button type="button" class="icon-btn" id="sidebar-toggle" aria-label="Toggle sidebar">
              ${icon('bx-menu')}
            </button>
            <h1 class="topbar-title" id="topbar-title"></h1>
          </div>
          <div class="topbar-right">
            <span class="connection-pill" id="connection-pill" title="Checking server">
              <span class="dot"></span>
              <span id="connection-text">Checking</span>
            </span>
            <span class="user-chip clickable-chip" id="topbar-user-chip" title="${escapeHtml(`${displayName} (${role}) - Click for My Account`)}" style="cursor:pointer">
              <span class="avatar">${escapeHtml(initials(displayName))}</span>
              <span style="display:flex;flex-direction:column;line-height:1.25;min-width:0">
                <strong>${escapeHtml(displayName)}</strong>
                <span class="muted" style="font-size:10.5px">${escapeHtml(role)}</span>
              </span>
            </span>
            <button type="button" class="icon-btn" id="theme-toggle" title="${themeTitle}">
              ${icon(themeIcon)}
            </button>
            <button type="button" class="icon-btn danger logout-topbar" id="topbar-logout" title="Logout">
              ${icon('bx-log-out')}
            </button>
          </div>
        </header>
        <main class="content" id="page-content"></main>
      </section>
    </div>
  `;

  const sidebar = qs('#sidebar', appRoot);
  const overlay = qs('#sidebar-overlay', appRoot);
  const closeButton = qs('#sidebar-close', appRoot);
  const toggleButton = qs('#sidebar-toggle', appRoot);

  function updateMobileChrome() {
    if (!sidebar) return;
    sidebar.classList.toggle('mobile-open', mobileSidebarOpen);
    if (overlay) overlay.classList.toggle('active', mobileSidebarOpen);
    if (closeButton) {
      closeButton.style.display = isMobile() && mobileSidebarOpen ? 'inline-flex' : 'none';
    }
  }

  function toggleSidebar() {
    if (isMobile()) {
      mobileSidebarOpen = !mobileSidebarOpen;
      updateMobileChrome();
      return;
    }
    sidebarCollapsed = !sidebarCollapsed;
    sidebar.classList.toggle('collapsed', sidebarCollapsed);
    localStorage.setItem(
      'desktop_sidebar_state',
      sidebarCollapsed ? 'closed' : 'open',
    );
  }

  const onResize = () => {
    if (!isMobile() && mobileSidebarOpen) {
      mobileSidebarOpen = false;
    }
    updateMobileChrome();
  };

  toggleButton.addEventListener('click', toggleSidebar);
  if (overlay) overlay.addEventListener('click', closeMobileSidebar);
  if (closeButton) closeButton.addEventListener('click', closeMobileSidebar);

  qs('#theme-toggle', appRoot).addEventListener('click', () => toggleTheme());
  qs('#topbar-logout', appRoot).addEventListener('click', logout);
  qs('#sidebar-logout', appRoot).addEventListener('click', (event) => {
    event.preventDefault();
    logout();
  });

  const userChip = qs('#topbar-user-chip', appRoot);
  if (userChip) {
    userChip.addEventListener('click', openMyAccountModal);
  }

  qs('.nav-links', appRoot).addEventListener('click', (event) => {
    if (event.target.closest('a')) closeMobileSidebar();
  });

  const sidebarLogo = qs('#shell-logo', appRoot);
  if (sidebarLogo) {
    sidebarLogo.addEventListener('error', () => {
      sidebarLogo.style.display = 'none';
    });
  }

  window.addEventListener('resize', onResize);
  addShellCleanup(() => window.removeEventListener('resize', onResize));

  updateMobileChrome();
  updateActiveNav(currentPath());
  startHealthPolling();
}

function renderHome() {
  teardownShell();

  const settings = state.settings;
  const user = state.user || {};
  const logo = settings.logoUrl || '/logo.png';
  const themeIcon = state.theme === 'dark' ? 'bx-sun' : 'bx-moon';
  const themeTitle = state.theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode';

  appRoot.innerHTML = `
    <div class="home-page">
      <button type="button" class="theme-toggle-fab" id="home-theme" title="${themeTitle}">
        ${icon(themeIcon)}
      </button>
      <button type="button" class="logout-fab" id="home-logout" title="Logout">
        ${icon('bx-log-out')}
      </button>
      <h1 class="hero-title" id="home-title">
        <img class="hero-logo-img" id="home-logo" src="${escapeHtml(logo)}" alt="Logo">
        <span>${escapeHtml(settings.businessName)}</span>
      </h1>
      ${user.username ? `<p class="welcome-line">Welcome, <strong>${escapeHtml(user.fullName || user.username)}</strong></p>` : ''}
      <div class="home-grid">
        ${HOME_CARDS
      .filter((card) => !card.adminOnly || (user.role === 'ADMIN'))
      .map(
        (card) => `
              <a class="home-card ${card.cls}" href="#${card.path}">
                <div class="icon-container">${icon(card.icon)}</div>
                <span>${escapeHtml(card.label)}</span>
              </a>
            `,
      ).join('')}
      </div>
    </div>
  `;

  qs('#home-theme', appRoot).addEventListener('click', () => toggleTheme());
  qs('#home-logout', appRoot).addEventListener('click', logout);

  const homeLogo = qs('#home-logo', appRoot);
  if (homeLogo) {
    homeLogo.addEventListener('error', () => {
      homeLogo.style.display = 'none';
    });
  }
}

async function logout() {
  closeMobileSidebar();
  try {
    await api.logout();
  } finally {
    state.user = null;
    if (location.hash && location.hash !== '#/') location.hash = '#/';
    renderLogin();
    toast('Logged out successfully', 'success');
  }
}

function handleUnauthorized() {
  if (!state.user) return;
  state.user = null;
  if (location.hash && location.hash !== '#/') location.hash = '#/';
  renderLogin();
  toast('Your session has expired. Please sign in again.', 'error');
}

function ensureShell() {
  if (!appRoot.querySelector('.app-container')) renderShell();
}

function handleRoute() {
  if (!state.user) return;

  if (state.user.mustChangePassword) {
    promptForcedPasswordChange();
    return;
  }

  const path = currentPath();
  const route = ROUTES[path];
  if (!route) {
    location.hash = '#/';
    return;
  }

  if (route.adminOnly && state.user.role !== 'ADMIN') {
    location.hash = '#/';
    toast('Administrator access required.', 'error');
    return;
  }

  destroyPage();
  closeMobileSidebar();

  if (path === '/') {
    renderHome();
    return;
  }

  ensureShell();
  const content = qs('#page-content', appRoot);
  if (!content) return;

  const title = qs('#topbar-title', appRoot);
  if (title) title.textContent = route.title;

  updateActiveNav(path);
  content.innerHTML = '';

  try {
    route.mount(content, { addCleanup: addPageCleanup });
  } catch (error) {
    console.error(error);
    content.innerHTML = emptyState('bx-error-circle', 'Could not load page', error.message);
    toast(error.message, 'error');
  }
}

function promptForcedPasswordChange() {
  if (forcePasswordModalActive) return;
  forcePasswordModalActive = true;

  openModal({
    title: 'Password Change Required',
    subtitle: 'You must set a new secure password before you can proceed.',
    size: 'narrow',
    closeOnBackdrop: false,
    body: `
      <form id="force-pass-form" autocomplete="off">
        <div id="fp-error" style="display:none;margin-bottom:14px;padding:10px 14px;border-radius:8px;background:rgba(239,68,68,0.15);border:1px solid rgba(239,68,68,0.3);color:#ef4444;font-size:13px"></div>
        <div class="form-group">
          <label for="fp-current">Current / Temporary Password *</label>
          <input class="form-input" id="fp-current" type="password" required>
        </div>
        <div class="form-group">
          <label for="fp-new">New Password *</label>
          <input class="form-input" id="fp-new" type="password" required minlength="8" placeholder="At least 8 chars, 1 letter, 1 number">
        </div>
        <div class="form-group">
          <label for="fp-confirm">Confirm New Password *</label>
          <input class="form-input" id="fp-confirm" type="password" required>
        </div>
      </form>
    `,
    footer: `
      <button type="button" class="btn btn-secondary" id="fp-logout-btn">Log out</button>
      <button type="submit" form="force-pass-form" class="btn btn-primary" id="fp-submit">Update Password</button>
    `,
    onMount(modalRoot, close) {
      const closeBtn = qs('.modal-close', modalRoot);
      if (closeBtn) closeBtn.style.display = 'none';

      qs('#fp-logout-btn', modalRoot).addEventListener('click', () => {
        close();
        forcePasswordModalActive = false;
        logout();
      });

      const form = qs('#force-pass-form', modalRoot);
      const errBox = qs('#fp-error', modalRoot);
      const submitBtn = qs('#fp-submit', modalRoot);

      form.addEventListener('submit', async (e) => {
        e.preventDefault();
        errBox.style.display = 'none';
        const currentPassword = qs('#fp-current', modalRoot).value;
        const newPassword = qs('#fp-new', modalRoot).value;
        const confirmPassword = qs('#fp-confirm', modalRoot).value;

        if (newPassword !== confirmPassword) {
          errBox.textContent = 'New passwords do not match.';
          errBox.style.display = 'block';
          return;
        }

        busyButton(submitBtn, true, 'Updating...');
        try {
          await api.changePassword({ currentPassword, newPassword });
          state.user.mustChangePassword = false;
          forcePasswordModalActive = false;
          close();
          toast('Password updated successfully!', 'success');
          handleRoute();
        } catch (err) {
          errBox.textContent = err.message || 'Failed to update password.';
          errBox.style.display = 'block';
        } finally {
          busyButton(submitBtn, false);
        }
      });
    },
    onClose() {
      forcePasswordModalActive = false;
    },
  });
}

function openMyAccountModal() {
  const u = state.user;
  if (!u) return;

  openModal({
    title: 'My Account',
    subtitle: `@${escapeHtml(u.username)} (${escapeHtml(roleLabel(u.role))})`,
    size: 'medium',
    body: `
      <div class="user-mgmt-tabs" style="margin-bottom:16px">
        <button type="button" class="tab-btn active" id="acc-tab-profile">Profile</button>
        <button type="button" class="tab-btn" id="acc-tab-security">Security &amp; Password</button>
        <button type="button" class="tab-btn" id="acc-tab-sessions">Active Sessions</button>
      </div>

      <div id="acc-panel-profile">
        <form id="acc-profile-form" autocomplete="off">
          <div class="form-group">
            <label for="acc-fullname">Full Name</label>
            <input class="form-input" id="acc-fullname" value="${escapeHtml(u.fullName || '')}">
          </div>
          <div class="form-grid">
            <div class="form-group">
              <label for="acc-email">Email</label>
              <input class="form-input" id="acc-email" type="email" value="${escapeHtml(u.email || '')}">
            </div>
            <div class="form-group">
              <label for="acc-phone">Phone</label>
              <input class="form-input" id="acc-phone" value="${escapeHtml(u.phone || '')}">
            </div>
          </div>
          <button type="submit" class="btn btn-primary" id="acc-profile-submit" style="margin-top:8px">Save Profile</button>
        </form>
      </div>

      <div id="acc-panel-security" style="display:none">
        <form id="acc-password-form" autocomplete="off">
          <div id="acc-pass-error" style="display:none;margin-bottom:14px;padding:10px 14px;border-radius:8px;background:rgba(239,68,68,0.15);border:1px solid rgba(239,68,68,0.3);color:#ef4444;font-size:13px"></div>
          <div class="form-group">
            <label for="acc-curr-pass">Current Password *</label>
            <input class="form-input" id="acc-curr-pass" type="password" required>
          </div>
          <div class="form-group">
            <label for="acc-new-pass">New Password *</label>
            <input class="form-input" id="acc-new-pass" type="password" required minlength="8" placeholder="Minimum 8 characters with letters &amp; numbers">
          </div>
          <button type="submit" class="btn btn-primary" id="acc-pass-submit" style="margin-top:8px">Change Password</button>
        </form>
      </div>

      <div id="acc-panel-sessions" style="display:none">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
          <span class="muted" style="font-size:13px">Devices where you are currently signed in:</span>
          <button type="button" class="btn btn-sm btn-danger" id="acc-logout-others-btn">Sign Out Other Devices</button>
        </div>
        <div id="acc-sessions-list">Loading sessions...</div>
      </div>
    `,
    footer: `<button type="button" class="btn btn-secondary" data-modal-close>Close</button>`,
    onMount(modalRoot, close) {
      const pProfile = qs('#acc-panel-profile', modalRoot);
      const pSecurity = qs('#acc-panel-security', modalRoot);
      const pSessions = qs('#acc-panel-sessions', modalRoot);

      const tProfile = qs('#acc-tab-profile', modalRoot);
      const tSecurity = qs('#acc-tab-security', modalRoot);
      const tSessions = qs('#acc-tab-sessions', modalRoot);

      function showTab(panel, activeBtn) {
        pProfile.style.display = 'none';
        pSecurity.style.display = 'none';
        pSessions.style.display = 'none';
        tProfile.classList.remove('active');
        tSecurity.classList.remove('active');
        tSessions.classList.remove('active');

        panel.style.display = 'block';
        activeBtn.classList.add('active');
      }

      tProfile.addEventListener('click', () => showTab(pProfile, tProfile));
      tSecurity.addEventListener('click', () => showTab(pSecurity, tSecurity));
      tSessions.addEventListener('click', () => {
        showTab(pSessions, tSessions);
        loadMySessionsList(qs('#acc-sessions-list', modalRoot));
      });

      // Profile submit
      qs('#acc-profile-form', modalRoot).addEventListener('submit', async (e) => {
        e.preventDefault();
        const submitBtn = qs('#acc-profile-submit', modalRoot);
        busyButton(submitBtn, true, 'Saving...');
        try {
          const updated = await api.updateMe({
            fullName: qs('#acc-fullname', modalRoot).value.trim(),
            email: qs('#acc-email', modalRoot).value.trim() || undefined,
            phone: qs('#acc-phone', modalRoot).value.trim() || undefined,
          });
          state.user = updated;
          toast('Profile updated successfully!', 'success');
          close();
          renderShell();
          handleRoute();
        } catch (err) {
          toast(err.message, 'error');
        } finally {
          busyButton(submitBtn, false);
        }
      });

      // Password submit
      qs('#acc-password-form', modalRoot).addEventListener('submit', async (e) => {
        e.preventDefault();
        const errBox = qs('#acc-pass-error', modalRoot);
        errBox.style.display = 'none';
        const submitBtn = qs('#acc-pass-submit', modalRoot);
        busyButton(submitBtn, true, 'Updating...');
        try {
          await api.changePassword({
            currentPassword: qs('#acc-curr-pass', modalRoot).value,
            newPassword: qs('#acc-new-pass', modalRoot).value,
          });
          toast('Password changed successfully!', 'success');
          close();
        } catch (err) {
          errBox.textContent = err.message || 'Failed to change password.';
          errBox.style.display = 'block';
        } finally {
          busyButton(submitBtn, false);
        }
      });

      // Logout others
      qs('#acc-logout-others-btn', modalRoot).addEventListener('click', async () => {
        await api.logoutOthers();
        toast('All other sessions signed out.', 'success');
        loadMySessionsList(qs('#acc-sessions-list', modalRoot));
      });
    },
  });
}

async function loadMySessionsList(container) {
  try {
    const list = await api.mySessions();
    if (!list.length) {
      container.innerHTML = `<p class="muted" style="font-size:13px">No other active sessions.</p>`;
      return;
    }
    container.innerHTML = `
      <table class="data-table" style="font-size:12px">
        <thead><tr><th>IP</th><th>Device / Browser</th><th>Last Active</th><th style="text-align:right">Action</th></tr></thead>
        <tbody>
          ${list.map((s) => `
            <tr>
              <td>${escapeHtml(s.ip || '—')}</td>
              <td style="max-width:240px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${escapeHtml(s.userAgent || 'Web browser')}</td>
              <td>${new Date(s.lastSeenAt).toLocaleString()}</td>
              <td style="text-align:right">
                <button type="button" class="btn btn-sm btn-secondary my-sess-revoke" data-sid="${s.id}">Revoke</button>
              </td>
            </tr>
          `).join('')}
        </tbody>
      </table>
    `;

    qsa('.my-sess-revoke', container).forEach((btn) => {
      btn.addEventListener('click', async () => {
        await api.revokeMySession(btn.dataset.sid);
        toast('Session revoked.', 'success');
        loadMySessionsList(container);
      });
    });
  } catch (err) {
    container.innerHTML = `<p class="text-danger" style="font-size:12px">${escapeHtml(err.message)}</p>`;
  }
}

async function boot() {
  appRoot = document.getElementById('app');
  if (!appRoot) return;

  applyBranding(state.settings);
  setTimeout(() => document.body.classList.remove('preload'), 50);

  window.addEventListener('hashchange', handleRoute);
  window.addEventListener('venuity:theme', updateThemeIcons);
  window.addEventListener('venuity:settings', updateBrandingDom);
  window.addEventListener('venuity:unauthorized', handleUnauthorized);

  loadPublicBranding();

  if (getAccessToken() || getRefreshToken()) {
    try {
      state.user = await api.me();
    } catch (error) {
      clearTokens();
      state.user = null;
    }
  }

  if (state.user) {
    await loadSettings();
    if (state.user) {
      renderShell();
      handleRoute();
      return;
    }
  }

  renderLogin();
}

boot();
