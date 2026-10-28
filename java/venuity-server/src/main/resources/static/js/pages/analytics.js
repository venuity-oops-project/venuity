import { api } from '../api.js';
import {
  addDaysISO,
  formatDateRange,
  formatDateShort,
  formatMoney,
  hourLabel,
  parseISODate,
  state,
  todayISO,
} from '../store.js';
import {
  emptyState,
  escapeHtml,
  icon,
  pageHeader,
  qs,
  qsa,
  toast,
} from '../ui.js';

function hexToRgba(hex, alpha) {
  const match = /^#([0-9a-f]{6})$/i.exec(String(hex || '').trim());
  if (!match) return `rgba(99, 102, 241, ${alpha})`;
  const value = match[1];
  const red = parseInt(value.slice(0, 2), 16);
  const green = parseInt(value.slice(2, 4), 16);
  const blue = parseInt(value.slice(4, 6), 16);
  return `rgba(${red}, ${green}, ${blue}, ${alpha})`;
}

function daysBetween(from, to) {
  const start = parseISODate(from);
  const end = parseISODate(to);
  if (!start || !end) return 1;
  return Math.max(1, Math.round((end.getTime() - start.getTime()) / 86400000) + 1);
}

function monthStartISO() {
  const date = new Date();
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-01`;
}

export function mount(root, { addCleanup } = {}) {
  const storageKeys = {
    interval: 'venuity_analytics_interval',
    chart: 'venuity_analytics_chart',
    metric: 'venuity_analytics_metric',
    from: 'venuity_analytics_from',
    to: 'venuity_analytics_to',
  };

  let interval = localStorage.getItem(storageKeys.interval) === 'hourly' ? 'hourly' : 'daily';
  let chartType = localStorage.getItem(storageKeys.chart) === 'bar' ? 'bar' : 'line';
  let metric = localStorage.getItem(storageKeys.metric) === 'checkins' ? 'checkins' : 'revenue';
  let from = localStorage.getItem(storageKeys.from) || addDaysISO(todayISO(), -29);
  let to = localStorage.getItem(storageKeys.to) || todayISO();

  if (!parseISODate(from)) from = addDaysISO(todayISO(), -29);
  if (!parseISODate(to)) to = todayISO();
  if (from > to) {
    const swap = from;
    from = to;
    to = swap;
  }

  let chart = null;
  let loadSeq = 0;
  let lastLabels = [];
  let lastValues = [];

  root.innerHTML = `
    ${pageHeader('Analytics', 'Understand visits and revenue over time.')}
    <div class="analytics-header">
      <button type="button" class="btn mobile-menu-btn" id="analytics-menu">
        ${icon('bx-slider-alt')} Chart options
      </button>
      <div class="analytics-toggles-wrapper" id="analytics-toggles">
        <div class="toggle-container ${metric === 'revenue' ? 'right' : ''}" data-toggle="metric">
          <div class="toggle-bg"></div>
          <button type="button" class="toggle-btn ${metric === 'checkins' ? 'active' : ''}" data-value="checkins">
            ${icon('bx-group')} Check-ins
          </button>
          <button type="button" class="toggle-btn ${metric === 'revenue' ? 'active' : ''}" data-value="revenue">
            ${icon('bx-coin-stack')} Revenue
          </button>
        </div>
        <div class="toggle-container ${chartType === 'line' ? 'right' : ''}" data-toggle="chart">
          <div class="toggle-bg"></div>
          <button type="button" class="toggle-btn ${chartType === 'bar' ? 'active' : ''}" data-value="bar">
            ${icon('bx-bar-chart')} Bar
          </button>
          <button type="button" class="toggle-btn ${chartType === 'line' ? 'active' : ''}" data-value="line">
            ${icon('bx-line-chart')} Line
          </button>
        </div>
        <div class="toggle-container ${interval === 'daily' ? 'right' : ''}" data-toggle="interval">
          <div class="toggle-bg"></div>
          <button type="button" class="toggle-btn ${interval === 'hourly' ? 'active' : ''}" data-value="hourly">Hourly</button>
          <button type="button" class="toggle-btn ${interval === 'daily' ? 'active' : ''}" data-value="daily">Daily</button>
        </div>
      </div>
    </div>
    <div class="summary-strip" id="analytics-summary">
      <div class="summary-tile"><div class="skeleton-row" style="height:38px;margin:0"></div></div>
      <div class="summary-tile"><div class="skeleton-row" style="height:38px;margin:0"></div></div>
      <div class="summary-tile"><div class="skeleton-row" style="height:38px;margin:0"></div></div>
    </div>
    <div class="glass-card analytics-card">
      <div class="chart-header">
        <h2 class="chart-title" id="chart-title">Check-in revenue</h2>
        <div class="date-controls">
          <button type="button" class="date-nav-btn" id="date-prev" title="Previous range">
            ${icon('bx-chevron-left')}
          </button>
          <span class="date-display" id="date-display">${escapeHtml(formatDateRange(from, to))}</span>
          <button type="button" class="date-nav-btn" id="date-next" title="Next range">
            ${icon('bx-chevron-right')}
          </button>
        </div>
        <div style="display:flex;gap:8px;flex-wrap:wrap;align-items:center">
          <select class="date-input" id="range-preset" aria-label="Date range">
            <option value="today">Today</option>
            <option value="7d">Last 7 days</option>
            <option value="30d">Last 30 days</option>
            <option value="month">This month</option>
            <option value="custom">Custom</option>
          </select>
          <input type="date" class="date-input" id="date-from" value="${from}" aria-label="From date">
          <input type="date" class="date-input" id="date-to" value="${to}" aria-label="To date">
        </div>
      </div>
      <div class="chart-container" id="chart-container">
        <canvas id="analytics-chart"></canvas>
        <div class="chart-empty" id="chart-empty" hidden></div>
      </div>
    </div>
  `;

  const toggles = qs('#analytics-toggles', root);
  const summaryElement = qs('#analytics-summary', root);
  const chartTitle = qs('#chart-title', root);
  const dateDisplay = qs('#date-display', root);
  const dateFrom = qs('#date-from', root);
  const dateTo = qs('#date-to', root);
  const preset = qs('#range-preset', root);
  const canvas = qs('#analytics-chart', root);
  const chartContainer = qs('#chart-container', root);
  const chartEmpty = qs('#chart-empty', root);
  const menuButton = qs('#analytics-menu', root);

  function syncToggles() {
    qsa('.toggle-container', root).forEach((container) => {
      const kind = container.dataset.toggle;
      const current = kind === 'metric' ? metric : kind === 'chart' ? chartType : interval;
      const buttons = qsa('.toggle-btn', container);
      const index = buttons.findIndex((button) => button.dataset.value === current);
      buttons.forEach((button, buttonIndex) => {
        button.classList.toggle('active', buttonIndex === index);
      });
      container.classList.toggle('right', index === 1);
    });
  }

  function updateTitle() {
    chartTitle.textContent = metric === 'revenue' ? 'Check-in revenue' : 'Number of check-ins';
  }

  function detectPreset() {
    const today = todayISO();
    const span = daysBetween(from, to);
    if (from === today && to === today) return 'today';
    if (to === today && span === 7) return '7d';
    if (to === today && span === 30) return '30d';
    if (from === monthStartISO() && to === today) return 'month';
    return 'custom';
  }

  function syncPreset() {
    preset.value = detectPreset();
  }

  function setDateDisplay() {
    dateDisplay.textContent = formatDateRange(from, to);
  }

  function persistRange() {
    localStorage.setItem(storageKeys.from, from);
    localStorage.setItem(storageKeys.to, to);
  }

  function applyRange(nextFrom, nextTo, { reload = true } = {}) {
    let start = parseISODate(nextFrom) ? nextFrom : from;
    let end = parseISODate(nextTo) ? nextTo : to;
    if (start > end) {
      const swap = start;
      start = end;
      end = swap;
    }
    from = start;
    to = end;
    dateFrom.value = from;
    dateTo.value = to;
    setDateDisplay();
    syncPreset();
    persistRange();
    if (reload) load();
  }

  function shiftRange(direction) {
    const span = daysBetween(from, to);
    const nextFrom = addDaysISO(from, direction * span);
    const nextTo = addDaysISO(to, direction * span);
    applyRange(nextFrom, nextTo);
  }

  function showMessage(html) {
    chartEmpty.hidden = false;
    chartEmpty.innerHTML = html;
  }

  function hideMessage() {
    chartEmpty.hidden = true;
    chartEmpty.innerHTML = '';
  }

  function destroyChart() {
    if (chart) {
      chart.destroy();
      chart = null;
    }
    const fallback = chartContainer.querySelector('.chart-fallback');
    if (fallback) fallback.remove();
  }

  function renderSummary(summary) {
    summaryElement.innerHTML = `
      <div class="summary-tile">
        <div class="label">Revenue</div>
        <div class="value">${formatMoney(summary.totalRevenue)}</div>
      </div>
      <div class="summary-tile">
        <div class="label">Check-ins</div>
        <div class="value">${Number(summary.checkIns) || 0}</div>
      </div>
      <div class="summary-tile">
        <div class="label">Avg check-ins / day</div>
        <div class="value">${(Number(summary.averageCheckInsPerDay) || 0).toFixed(1)}</div>
      </div>
    `;
  }

  function renderChart(labels, values, hasData) {
    destroyChart();
    lastLabels = labels;
    lastValues = values;

    if (!hasData) {
      canvas.style.display = 'none';
      showMessage(
        emptyState(
          'bx-bar-chart-alt-2',
          'No activity in this range',
          'Try a different date range or record a check-in first.',
        ),
      );
      return;
    }

    hideMessage();
    canvas.style.display = 'block';

    if (typeof window.Chart !== 'function') {
      canvas.style.display = 'none';
      const maximum = Math.max(...values, 1);
      const fallback = document.createElement('div');
      fallback.className = 'chart-fallback';
      fallback.innerHTML = values
        .map((value) => {
          const height = Math.max(2, Math.round((value / maximum) * 100));
          return `<div class="bar" style="height:${height}%" title="${escapeHtml(String(value))}"></div>`;
        })
        .join('');
      chartContainer.insertBefore(fallback, chartEmpty);
      return;
    }

    const primary =
      getComputedStyle(document.documentElement).getPropertyValue('--primary-color').trim() ||
      '#6366f1';
    const textColor =
      getComputedStyle(document.documentElement).getPropertyValue('--text-light').trim() ||
      '#64748b';
    const isDark = state.theme === 'dark';
    const gridColor = isDark ? 'rgba(255,255,255,0.06)' : 'rgba(15,23,42,0.06)';
    const fillColor = hexToRgba(primary, chartType === 'line' ? 0.12 : 0.62);

    chart = new window.Chart(canvas.getContext('2d'), {
      type: chartType,
      data: {
        labels,
        datasets: [
          {
            label: metric === 'revenue' ? 'Revenue' : 'Check-ins',
            data: values,
            borderColor: primary,
            backgroundColor: fillColor,
            hoverBackgroundColor: hexToRgba(primary, 0.82),
            fill: chartType === 'line',
            tension: 0.35,
            borderWidth: chartType === 'line' ? 3 : 0,
            pointRadius: chartType === 'line' ? 3 : 0,
            pointHoverRadius: 5,
            pointBackgroundColor: primary,
            borderRadius: chartType === 'bar' ? 6 : 0,
            maxBarThickness: 38,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        animation: { duration: 500 },
        plugins: {
          legend: { display: false },
          tooltip: {
            backgroundColor: 'rgba(15, 23, 42, 0.92)',
            padding: 12,
            cornerRadius: 8,
            displayColors: false,
            callbacks: {
              label(context) {
                return metric === 'revenue'
                  ? formatMoney(context.parsed.y)
                  : `${context.parsed.y} check-ins`;
              },
            },
          },
        },
        scales: {
          x: {
            grid: { display: false },
            ticks: {
              color: textColor,
              maxRotation: 0,
              autoSkip: true,
              maxTicksLimit: 12,
            },
          },
          y: {
            beginAtZero: true,
            grid: { color: gridColor },
            ticks: {
              color: textColor,
              precision: 0,
              callback(value) {
                return metric === 'revenue' ? formatMoney(value) : value;
              },
            },
          },
        },
      },
    });
  }

  async function load() {
    const seq = ++loadSeq;
    updateTitle();
    setDateDisplay();
    showMessage(`
      <div class="empty-state">
        <span class="spinner"></span>
        <p>Loading analytics...</p>
      </div>
    `);

    try {
      const [summary, series] = await Promise.all([
        api.analyticsSummary(from, to),
        interval === 'daily' ? api.analyticsDaily(from, to) : api.analyticsHourly(from, to),
      ]);
      if (seq !== loadSeq) return;

      renderSummary(summary);
      const points = series.points || [];
      const labels =
        interval === 'daily'
          ? points.map((point) => formatDateShort(point.date))
          : points.map((point) => hourLabel(point.hour));
      const values = points.map((point) =>
        metric === 'revenue' ? Number(point.revenue) || 0 : Number(point.visits) || 0,
      );
      const hasData = values.some((value) => value > 0);
      renderChart(labels, values, hasData);
      if (summary.from || summary.to) {
        dateDisplay.textContent = formatDateRange(summary.from || from, summary.to || to);
      }
    } catch (error) {
      if (seq !== loadSeq) return;
      destroyChart();
      canvas.style.display = 'none';
      summaryElement.innerHTML = '';
      showMessage(emptyState('bx-error-circle', 'Could not load analytics', error.message));
      toast(error.message, 'error');
    }
  }

  toggles.addEventListener('click', (event) => {
    const button = event.target.closest('.toggle-btn[data-value]');
    if (!button) return;
    const container = button.closest('[data-toggle]');
    if (!container) return;
    const kind = container.dataset.toggle;
    const value = button.dataset.value;

    if (kind === 'metric') {
      if (metric === value) return;
      metric = value;
      localStorage.setItem(storageKeys.metric, metric);
    } else if (kind === 'chart') {
      if (chartType === value) return;
      chartType = value;
      localStorage.setItem(storageKeys.chart, chartType);
    } else {
      if (interval === value) return;
      interval = value;
      localStorage.setItem(storageKeys.interval, interval);
    }
    syncToggles();
    updateTitle();
    load();
  });

  preset.addEventListener('change', () => {
    const today = todayISO();
    switch (preset.value) {
      case 'today':
        applyRange(today, today);
        break;
      case '7d':
        applyRange(addDaysISO(today, -6), today);
        break;
      case '30d':
        applyRange(addDaysISO(today, -29), today);
        break;
      case 'month':
        applyRange(monthStartISO(), today);
        break;
      default:
        break;
    }
  });

  dateFrom.addEventListener('change', () => applyRange(dateFrom.value, to));
  dateTo.addEventListener('change', () => applyRange(from, dateTo.value));
  qs('#date-prev', root).addEventListener('click', () => shiftRange(-1));
  qs('#date-next', root).addEventListener('click', () => shiftRange(1));

  menuButton.addEventListener('click', () => {
    toggles.classList.toggle('show');
  });

  const onTheme = () => {
    if (lastValues.some((value) => value > 0)) {
      renderChart(lastLabels, lastValues, true);
    }
  };
  document.addEventListener('venuity:theme', onTheme);

  syncToggles();
  syncPreset();
  updateTitle();
  setDateDisplay();
  load();

  addCleanup?.(() => {
    loadSeq += 1;
    destroyChart();
    document.removeEventListener('venuity:theme', onTheme);
  });
}
