/**
 * Shared client state and formatting helpers.
 */

export const DEFAULT_SETTINGS = {
  businessName: 'PlayArea Manager',
  currencySymbol: '\u20b9',
  checkinFee: '100.00',
  primaryColor: '#6366f1',
  primaryHoverColor: '#4f46e5',
  logoUrl: '/logo.png',
  appTitle: 'Venuity',
  businessTimezone: 'Asia/Kolkata',
};

export const state = {
  user: null,
  settings: loadCachedSettings(),
  theme: getInitialTheme(),
};

function loadCachedSettings() {
  try {
    const cached = JSON.parse(localStorage.getItem('venuity_settings') || '{}');
    return normalizeSettings(cached);
  } catch (error) {
    return { ...DEFAULT_SETTINGS };
  }
}

export function normalizeSettings(raw) {
  const result = { ...DEFAULT_SETTINGS };
  if (!raw || typeof raw !== 'object') return result;
  for (const key of Object.keys(DEFAULT_SETTINGS)) {
    const value = raw[key];
    if (value !== undefined && value !== null && value !== '') {
      result[key] = typeof value === 'string' ? value : String(value);
    }
  }
  return result;
}

export function cacheSettings(settings) {
  const normalized = normalizeSettings(settings);
  localStorage.setItem('venuity_settings', JSON.stringify(normalized));
  return normalized;
}

export function updateSettings(settings) {
  state.settings = normalizeSettings({ ...state.settings, ...settings });
  cacheSettings(state.settings);
  applyBranding(state.settings);
  document.dispatchEvent(new CustomEvent('venuity:settings', { detail: state.settings }));
  return state.settings;
}

export function applyBranding(settings = state.settings) {
  const root = document.documentElement;
  if (settings.primaryColor) root.style.setProperty('--primary-color', settings.primaryColor);
  if (settings.primaryHoverColor) root.style.setProperty('--primary-hover', settings.primaryHoverColor);

  const title = settings.appTitle || settings.businessName || 'Venuity';
  document.title = title;

  const icon = document.querySelector('link[rel="icon"][type="image/png"]');
  if (icon && settings.logoUrl) icon.setAttribute('href', settings.logoUrl);
}

export function getInitialTheme() {
  return localStorage.getItem('theme') === 'light' ? 'light' : 'dark';
}

export function applyTheme(theme) {
  state.theme = theme === 'light' ? 'light' : 'dark';
  if (state.theme === 'dark') document.documentElement.setAttribute('data-theme', 'dark');
  else document.documentElement.removeAttribute('data-theme');
  localStorage.setItem('theme', state.theme);
  document.dispatchEvent(new CustomEvent('venuity:theme', { detail: state.theme }));
}

export function toggleTheme() {
  applyTheme(state.theme === 'dark' ? 'light' : 'dark');
  return state.theme;
}

export function formatMoney(value) {
  const symbol = state.settings.currencySymbol || '';
  const number = Number(value);
  if (!Number.isFinite(number)) return `${symbol}0.00`;
  return `${symbol}${number.toLocaleString(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`;
}

export function formatDateTime(value) {
  if (!value) return '\u2014';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  try {
    return new Intl.DateTimeFormat(undefined, {
      dateStyle: 'medium',
      timeStyle: 'short',
      timeZone: state.settings.businessTimezone || undefined,
    }).format(date);
  } catch (error) {
    return date.toLocaleString();
  }
}

export function formatDate(value) {
  if (!value) return '\u2014';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  try {
    return new Intl.DateTimeFormat(undefined, {
      dateStyle: 'medium',
      timeZone: state.settings.businessTimezone || undefined,
    }).format(date);
  } catch (error) {
    return date.toLocaleDateString();
  }
}

export function formatTime(value) {
  if (!value) return '\u2014';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  try {
    return new Intl.DateTimeFormat(undefined, {
      timeStyle: 'short',
      timeZone: state.settings.businessTimezone || undefined,
    }).format(date);
  } catch (error) {
    return date.toLocaleTimeString();
  }
}

export function todayISO() {
  return toISODate(new Date());
}

export function toISODate(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function addDaysISO(value, days) {
  const date = parseISODate(value) || new Date();
  date.setDate(date.getDate() + days);
  return toISODate(date);
}

export function parseISODate(value) {
  if (!value) return null;
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(value));
  if (!match) return null;
  const date = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]), 12);
  return Number.isNaN(date.getTime()) ? null : date;
}

export function formatDateShort(value) {
  const date = parseISODate(value) || new Date(value);
  if (!date || Number.isNaN(date.getTime())) return String(value || '');
  return new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric' }).format(date);
}

export function formatDateRange(from, to) {
  const start = parseISODate(from);
  const end = parseISODate(to);
  if (!start || !end) return '';
  const sameDay = from === to;
  try {
    if (sameDay) {
      return new Intl.DateTimeFormat(undefined, {
        weekday: 'short',
        year: 'numeric',
        month: 'short',
        day: 'numeric',
      }).format(start);
    }
    const startText = new Intl.DateTimeFormat(undefined, {
      month: 'short',
      day: 'numeric',
    }).format(start);
    const endText = new Intl.DateTimeFormat(undefined, {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    }).format(end);
    return `${startText} \u2013 ${endText}`;
  } catch (error) {
    return `${from} \u2013 ${to}`;
  }
}

export function hourLabel(hour) {
  if (hour === 0) return '12a';
  if (hour === 12) return '12p';
  return hour < 12 ? `${hour}a` : `${hour - 12}p`;
}

export function initials(name) {
  const parts = String(name || '')
    .trim()
    .split(/\s+/)
    .filter(Boolean);
  if (parts.length === 0) return '?';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase();
}

export function roleLabel(role) {
  if (role === 'ADMIN') return 'Administrator';
  if (role === 'STAFF') return 'Staff';
  return role || 'User';
}

export function transactionMeta(type) {
  const normalized = String(type || '').toUpperCase();
  switch (normalized) {
    case 'CHECKIN':
      return {
        label: 'Check-in',
        badge: 'badge-checkin',
        credit: false,
        icon: 'bx-up-arrow-alt',
      };
    case 'TOPUP':
    case 'OPENING_BALANCE':
      return {
        label: 'Recharge',
        badge: 'badge-recharge',
        credit: true,
        icon: 'bx-down-arrow-alt',
      };
    case 'REFUND':
      return {
        label: 'Refund',
        badge: 'badge-refund',
        credit: true,
        icon: 'bx-down-arrow-alt',
      };
    case 'ADJUSTMENT':
      return {
        label: 'Adjustment',
        badge: 'badge-adjustment',
        credit: true,
        icon: 'bx-transfer',
      };
    default:
      return {
        label: normalized ? normalized.toLowerCase() : 'Transaction',
        badge: 'badge-adjustment',
        credit: true,
        icon: 'bx-transfer',
      };
  }
}
