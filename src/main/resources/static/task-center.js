// TaskCenter: lightweight persistent task UI (bubble + right drawer)
// Storage key: ec_tasks_v1
(function () {
    const STORAGE_KEY = 'ec_tasks_v1';

    function nowIso() {
        return new Date().toISOString();
    }

    function safeParse(json, fallback) {
        try {
            return JSON.parse(json);
        } catch {
            return fallback;
        }
    }

    function clamp(n, a, b) {
        return Math.max(a, Math.min(b, n));
    }

    function loadState() {
        const raw = localStorage.getItem(STORAGE_KEY);
        const state = safeParse(raw, null);
        if (!state || typeof state !== 'object') {
            return { tasks: {}, order: [], drawerOpen: false };
        }
        state.tasks = state.tasks && typeof state.tasks === 'object' ? state.tasks : {};
        state.order = Array.isArray(state.order) ? state.order : [];
        state.drawerOpen = !!state.drawerOpen;
        return state;
    }

    function saveState(state) {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    }

    function findDom() {
        return {
            bubble: document.getElementById('taskBubble'),
            bubbleHostDesktop: document.getElementById('taskBubbleHostDesktop'),
            bubbleHostMobile: document.getElementById('taskBubbleHostMobile'),
            bubblePct: document.getElementById('taskBubblePct'),
            bubbleFill: document.getElementById('taskBubbleFill'),
            drawer: document.getElementById('taskDrawer'),
            backdrop: document.getElementById('taskBackdrop'),
            list: document.getElementById('taskList'),
            clearBtn: document.getElementById('taskClearCompleted')
        };
    }

    function relocateBubble() {
        const dom = findDom();
        if (!dom.bubble) return;
        const isMobile = window.matchMedia && window.matchMedia('(max-width: 768px)').matches;
        const host = isMobile ? dom.bubbleHostMobile : dom.bubbleHostDesktop;
        if (!host) return;
        if (dom.bubble.parentElement !== host) {
            host.appendChild(dom.bubble);
        }
    }

    function computeOverall(state) {
        const tasks = state.order.map(id => state.tasks[id]).filter(Boolean);
        const active = tasks.filter(t => t.state === 'running' || t.state === 'queued');
        if (active.length === 0) {
            const any = tasks.length > 0;
            return { visible: any, percent: any ? 100 : 0, activeCount: 0 };
        }

        let sum = 0;
        active.forEach(t => {
            const p = typeof t.percent === 'number' ? t.percent : 0;
            sum += clamp(p, 0, 100);
        });
        return { visible: true, percent: Math.round(sum / active.length), activeCount: active.length };
    }

    function formatTime(iso) {
        try {
            const d = new Date(iso);
            return d.toLocaleString('ru', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
        } catch {
            return '';
        }
    }

    function render() {
        const dom = findDom();
        if (!dom.bubble || !dom.drawer || !dom.list || !dom.backdrop) return;

        const state = loadState();
        const overall = computeOverall(state);

        dom.bubble.style.display = overall.visible ? 'inline-flex' : 'none';
        if (dom.bubblePct) dom.bubblePct.textContent = overall.visible ? String(overall.percent) + '%' : '';

        // fill (left-to-right)
        if (dom.bubbleFill) {
            const pct = clamp(overall.percent, 0, 100);
            dom.bubbleFill.style.width = pct + '%';
            dom.bubbleFill.style.display = 'block';
        }

        if (state.drawerOpen) {
            dom.drawer.classList.add('open');
            dom.backdrop.classList.remove('hidden');
        } else {
            dom.drawer.classList.remove('open');
            dom.backdrop.classList.add('hidden');
        }

        dom.list.innerHTML = '';
        state.order.forEach(id => {
            const t = state.tasks[id];
            if (!t) return;

            const item = document.createElement('div');
            item.className = 'task-item';

            const top = document.createElement('div');
            top.className = 'task-top';
            top.innerHTML = `
                <div class="task-title">${escapeHtml(t.title || id)}</div>
                <div style="display:flex; gap:8px; align-items:center;">
                    <div class="task-state ${escapeHtml(t.state || 'running')}">${escapeHtml(t.state || '')}</div>
                    <button class="secondary" style="padding:4px 8px; font-size:14px;" title="Close" aria-label="Close">×</button>
                </div>
            `;

            const closeBtn = top.querySelector('button');
            closeBtn.onclick = () => {
                try {
                    window.dispatchEvent(new CustomEvent('taskcenter:close', { detail: { task: t } }));
                } catch {}
                api.remove(id);
            };

            const meta = document.createElement('div');
            meta.className = 'task-meta';
            const pct = (typeof t.percent === 'number') ? clamp(Math.round(t.percent), 0, 100) : null;
            const subtitle = t.subtitle ? String(t.subtitle) : '';
            const ts = t.updatedAt ? formatTime(t.updatedAt) : '';
            meta.textContent = [pct !== null ? (pct + '%') : '', subtitle, ts].filter(Boolean).join(' · ');

            const bar = document.createElement('div');
            bar.className = 'task-bar';
            const fill = document.createElement('div');
            fill.className = 'task-bar-fill';
            fill.style.width = (pct !== null ? pct : 0) + '%';
            bar.appendChild(fill);

            const details = document.createElement('div');
            details.className = 'task-details';
            if (Array.isArray(t.lines) && t.lines.length > 0) {
                details.innerHTML = t.lines.slice(0, 12).map(l => `<div class="task-line">${escapeHtml(l)}</div>`).join('');
            }

            const actions = document.createElement('div');
            actions.className = 'task-controls';
            actions.style.marginTop = '10px';
            if (t.downloadUrl) {
                const dl = document.createElement('button');
                dl.className = 'primary';
                dl.textContent = 'Download';
                dl.onclick = () => {
                    // user gesture: should work on iOS
                    window.location.href = t.downloadUrl;
                };
                actions.appendChild(dl);
            }

            item.appendChild(top);
            item.appendChild(meta);
            if (pct !== null) item.appendChild(bar);
            if (details.innerHTML) item.appendChild(details);
            if (actions.children.length > 0) item.appendChild(actions);

            dom.list.appendChild(item);
        });

        // clear completed
        if (dom.clearBtn) {
            dom.clearBtn.onclick = () => {
                const st = loadState();
                st.order = st.order.filter(id => {
                    const t = st.tasks[id];
                    return t && !(t.state === 'done' || t.state === 'error' || t.state === 'cancelled');
                });
                Object.keys(st.tasks).forEach(id => {
                    const t = st.tasks[id];
                    if (!t) return;
                    if (t.state === 'done' || t.state === 'error' || t.state === 'cancelled') {
                        delete st.tasks[id];
                    }
                });
                saveState(st);
                render();
            };
        }
    }

    function escapeHtml(str) {
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    const api = {
        upsert(task) {
            const state = loadState();
            const id = task.id;
            if (!id) return;
            const existing = state.tasks[id] || {};
            const merged = {
                ...existing,
                ...task,
                updatedAt: nowIso()
            };
            state.tasks[id] = merged;
            if (!state.order.includes(id)) state.order.unshift(id);
            saveState(state);
            render();
        },
        update(id, patch) {
            if (!id) return;
            const state = loadState();
            const existing = state.tasks[id];
            if (!existing) return;
            state.tasks[id] = { ...existing, ...patch, updatedAt: nowIso() };
            saveState(state);
            render();
        },
        remove(id) {
            const state = loadState();
            delete state.tasks[id];
            state.order = state.order.filter(x => x !== id);
            saveState(state);
            render();
        },
        openDrawer() {
            const state = loadState();
            state.drawerOpen = true;
            saveState(state);
            render();
        },
        closeDrawer() {
            const state = loadState();
            state.drawerOpen = false;
            saveState(state);
            render();
        },
        toggleDrawer() {
            const state = loadState();
            state.drawerOpen = !state.drawerOpen;
            saveState(state);
            render();
        },
        list() {
            const state = loadState();
            return state.order.map(id => state.tasks[id]).filter(Boolean);
        },
        _render: render
    };

    window.TaskCenter = api;

    document.addEventListener('DOMContentLoaded', () => {
        const dom = findDom();
        relocateBubble();
        window.addEventListener('resize', () => {
            relocateBubble();
            render();
        });
        if (dom.bubble) {
            dom.bubble.addEventListener('click', () => api.toggleDrawer());
        }
        if (dom.backdrop) {
            dom.backdrop.addEventListener('click', () => api.closeDrawer());
        }
        render();
    });
})();
