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
  toast,
} from './ui.js';
import { mount as mountDashboard } from './pages/dashboard.js';
import { mount as mountCustomers } from './pages/customers.js';
import { mount as mountRecharge } from './pages/recharge.js';
import { mount as mountCheckin } from './pages/checkin.js';
import { mount as mountAnalytics } from './pages/analytics.js';
import { mount as mountSettings } from './pages/settings.js';

const NAV_ITEMS = [
  { path: '/', icon: 'bx-home', label: 'Home' },
  { path: '/dashboard', icon: 'bx-grid-alt', label: 'Dashboard' },
  { path: '/customers', icon: 'bx-user', label: 'Customers' },
  { path: '/recharge', icon: 'bx-wallet', label: 'Recharge' },
  { path: '/scan', icon: 'bx-qr-scan', label: 'Check-in' },
  { path: '/analytics', icon: 'bx-bar-chart-alt-2', label: 'Analytics' },
  { path: '/settings', icon: 'bx-cog', label: 'Settings' },
];

const HOME_CARDS = [
  { path: '/dashboard', cls: 'card-dashboard', icon: 'bx-grid-alt', label: 'Dashboard' },
  { path: '/customers', cls: 'card-customers', icon: 'bx-user', label: 'Customers' },
  { path: '/recharge', cls: 'card-recharge', icon: 'bx-wallet', label: 'Recharge' },
  { path: '/scan', cls: 'card-scan', icon: 'bx-qr-scan', label: 'Check-in' },
  { path: '/analytics', cls: 'card-analytics', icon: 'bx-bar-chart-alt-2', label: 'Analytics' },
  { path: '/settings', cls: 'card-settings', icon: 'bx-cog', label: 'Settings' },
];

const ROUTES = {
  '/': { title: 'Home' },
  '/dashboard': { title: 'Dashboard', mount: mountDashboard },
  '/customers': { title: 'Customers & QR Codes', mount: mountCustomers },
  '/recharge': { title: 'Recharge Balance', mount: mountRecharge },
  '/scan': { title: 'Scanner Check-in', mount: mountCheckin },
  '/analytics': { title: 'Business Statistics', mount: mountAnalytics },
  '/settings': { title: 'Settings', mount: mountSettings },
};

let appRoot = null;
let pageCleanups = [];
let shellCleanups = [];
let healthTimer = null;
let sidebarCollapsed = localStorage.getItem('desktop_sidebar_state') === 'closed';
let mobileSidebarOpen = false;

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
  destroyPage();
  const cleanups = shellCleanups;
  shellCleanups = [];
  cleanups.forEach((cleanup) => {
    try {
      cleanup();
    } catch (error) {
      console.error(error);
    }
  });
  const shell = appRoot ? appRoot.querySelector('.app-container') : null;
  if (shell) shell.remove();
  mobileSidebarOpen = false;
}

function closeMobileSidebar() {
  if (!mobileSidebarOpen) return;
  mobileSidebarOpen = false;
  const sidebar = qs('#sidebar');
  const overlay = qs('#sidebar-overlay');
  const closeButton = qs('#sidebar-close');
  if (sidebar) sidebar.classList.remove('mobile-open');
  if (overlay) overlay.classList.remove('active');
  if (closeButton) closeButton.style.display = 'none';
}

function setConnection(online) {
  const pill = qs('#connection-pill');
  if (!pill) return;
  pill.classList.toggle('offline', !online);
  pill.title = online ? 'Server reachable' : 'Server unreachable';
  const text = qs('#connection-text', pill);
  if (text) text.textContent = online ? 'Online' : 'Offline';
}

async function checkHealth() {
  try {
    await api.health();
    setConnection(true);
  } catch (error) {
    setConnection(false);
  }
}

function startHealthPolling() {
  stopHealthPolling();
  checkHealth();
  healthTimer = setInterval(checkHealth, 30000);
}

function updateActiveNav(path) {
  if (!appRoot) return;
  appRoot.querySelectorAll('[data-route]').forEach((link) => {
    link.classList.toggle('active', link.dataset.route === path);
  });
}

function updateThemeIcons() {
  const nextIcon = state.theme === 'dark' ? 'bx-sun' : 'bx-moon';
  const title = state.theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode';
  const topbarButton = qs('#theme-toggle');
  if (topbarButton) {
    topbarButton.innerHTML = icon(nextIcon);
    topbarButton.title = title;
  }
  const homeButton = qs('#home-theme');
  if (homeButton) {
    homeButton.innerHTML = icon(nextIcon);
    homeButton.title = title;
  }
}

function updateBrandingDom() {
  const settings = state.settings;
  const logo = settings.logoUrl || '/logo.png';

  const sidebarName = qs('#shell-name');
  if (sidebarName) sidebarName.textContent = settings.businessName;

  const sidebarLogo = qs('#shell-logo');
  if (sidebarLogo) {
    sidebarLogo.style.display = '';
    sidebarLogo.src = logo;
  }

  const loginTitle = qs('#login-title');
  if (loginTitle) loginTitle.textContent = settings.businessName;

  const loginLogo = qs('#login-logo');
  if (loginLogo) {
    loginLogo.style.display = '';
    loginLogo.src = logo;
  }

  const homeTitle = qs('#home-title span');
  if (homeTitle) homeTitle.textContent = settings.businessName;

  const homeLogo = qs('#home-logo');
  if (homeLogo) {
    homeLogo.style.display = '';
    homeLogo.src = logo;
  }
}

async function loadPublicBranding() {
  try {
    const branding = await api.branding();
    if (!branding) return;
    applySettings({
      businessName: branding.businessName || state.settings.businessName,
      primaryColor: branding.primaryColor || state.settings.primaryColor,
      primaryHoverColor: branding.primaryHoverColor || state.settings.primaryHoverColor,
      logoUrl: branding.logoUrl || state.settings.logoUrl,
      appTitle: branding.appTitle || state.settings.appTitle,
    });
  } catch (error) {
    /* Cached branding is used when the public endpoint is unavailable. */
  }
}

async function loadSettings() {
  try {
    const settings = await api.settings();
    applySettings(settings);
  } catch (error) {
    if (error.status !== 401) toast('Using cached business settings.', 'info');
  }
}

function renderLogin() {
  state.user = null;
  teardownShell();

  const settings = state.settings;
  const logo = settings.logoUrl || '/logo.png';

  appRoot.innerHTML = `
    <div class="login-page">
      <div class="login-bg-orb login-bg-orb-1"></div>
      <div class="login-bg-orb login-bg-orb-2"></div>
      <div class="login-bg-orb login-bg-orb-3"></div>
      <div class="login-card">
        <div class="login-header">
          <div class="login-logo-wrapper">
            <img class="login-logo" id="login-logo" src="${escapeHtml(logo)}"
                 alt="${escapeHtml(settings.businessName)} logo">
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
                 role="button" tabindex="0" aria-label="Show password"></i>
            </div>
          </div>
          <button type="submit" class="login-submit-btn" id="login-submit" disabled>
            ${icon('bx-log-in')}<span>Sign In</span>
          </button>
        </form>
        <p class="login-footer-text">Contact your administrator to create an account.</p>
      </div>
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

  [username, password].forEach((input) => {
    input.addEventListener('input', () => {
      hideError();
      updateSubmit();
    });
  });

  function togglePassword() {
    const show = password.type === 'password';
    password.type = show ? 'text' : 'password';
    toggle.className = `bx ${show ? 'bx-hide' : 'bx-show'} login-toggle-password`;
    toggle.setAttribute('aria-label', show ? 'Hide password' : 'Show password');
  }

  toggle.addEventListener('click', togglePassword);
  toggle.addEventListener('keydown', (event) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      togglePassword();
    }
  });

  updateSubmit();
  username.focus();
}

function renderShell() {
  teardownShell();

  const user = state.user || {};
  const settings = state.settings;
  const logo = settings.logoUrl || '/logo.png';
  const displayName = user.username || 'User';
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
          ${NAV_ITEMS.map(
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
            <span class="user-chip" title="${escapeHtml(`${displayName} (${role})`)}">
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
      ${user.username ? `<p class="welcome-line">Welcome, <strong>${escapeHtml(user.username)}</strong></p>` : ''}
      <div class="home-grid">
        ${HOME_CARDS.map(
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

  const path = currentPath();
  const route = ROUTES[path];
  if (!route) {
    location.hash = '#/';
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
