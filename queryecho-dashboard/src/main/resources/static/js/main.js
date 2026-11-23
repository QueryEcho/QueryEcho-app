import { loadMetrics } from "./api.js";
import { renderDb } from "./database.js";
import {
    renderHealth,
    renderKpis,
    renderLatency,
    renderOutcome,
} from "./overview.js";
import { renderQueries, renderSlow } from "./queries.js";
import { $, fmtTime, state, toast } from "./shared.js";
import { renderDebuggers, selectTransaction } from "./transactions.js";

function openQuery(q) {
    state.query = q;
    if (q.transactionId) {
        state.page = "trace";
        activatePage();
        selectTransaction(q.transactionId, q);
    } else toast("이 쿼리는 트랜잭션 컨텍스트 없이 실행됐습니다.");
}

function renderAll() {
    if (!state.data) return;
    renderKpis();
    renderOutcome();
    renderLatency();
    renderHealth();
    renderDebuggers();
    renderQueries(openQuery);
    renderSlow(openQuery);
    renderDb();
}

async function load() {
    $("main").classList.add("loading");
    try {
        state.data = await loadMetrics();
        const transactions = state.data.txs;
        if (!transactions.some((t) => t.transactionId === state.txId)) {
            state.txId = transactions.length
                ? transactions[0].transactionId
                : null;
            state.txQueries = [];
            state.query = null;
        }
        renderAll();
        if (state.txId) selectTransaction(state.txId, state.query);
        $("updated").textContent =
            "UPDATED " + fmtTime(new Date().toISOString());
        $("side-dot").classList.remove("off");
        $("side-status").textContent = "Collector connected";
    } catch (error) {
        $("updated").textContent = "COLLECTOR UNREACHABLE";
        $("side-dot").classList.add("off");
        $("side-status").textContent = "Collector unavailable";
        console.error(error);
        toast("수집 API에 연결할 수 없습니다.");
    } finally {
        $("main").classList.remove("loading");
    }
}

var pageCopy = {
    overview: [
        "Overview",
        "Database overview",
        "애플리케이션 트랜잭션과 실제 JDBC 쿼리를 한 흐름에서 관찰합니다.",
    ],
    trace: [
        "Transaction trace",
        "Transaction trace",
        "트랜잭션에서 쿼리로, 쿼리에서 부모 트랜잭션으로 이동하며 원인을 좁힙니다.",
    ],
    queries: [
        "Queries",
        "JDBC queries",
        "SDK 프록시가 관찰한 개별 쿼리 실행과 트랜잭션 연결 상태입니다.",
    ],
    db: [
        "DB server",
        "Database collectors",
        "DBeaver와 CLI를 포함해 DB 서버가 직접 관찰한 실행입니다.",
    ],
    slow: [
        "Slow queries",
        "Slow query analysis",
        "임계값 초과와 반복 실행 패턴을 트랜잭션 문맥으로 추적합니다.",
    ],
};

function activatePage() {
    document.querySelectorAll("[data-page]").forEach(function (n) {
        n.classList.toggle(
            "hidden",
            n.getAttribute("data-page") !== state.page,
        );
    });
    document.querySelectorAll(".nav button[data-view]").forEach(function (n) {
        n.classList.toggle(
            "active",
            n.getAttribute("data-view") === state.page,
        );
    });
    var c = pageCopy[state.page];
    $("crumb").textContent = c[0];
    $("page-title").textContent = c[1];
    $("page-desc").textContent = c[2];
}

function restart() {
    if (state.timer) clearInterval(state.timer);
    var ms = Number($("f-refresh").value);
    if (ms) state.timer = setInterval(load, ms);
}

document.querySelectorAll(".nav button[data-view]").forEach(function (b) {
    b.addEventListener("click", function () {
        state.page = b.getAttribute("data-view");
        activatePage();
    });
});
document.querySelectorAll("[data-source]").forEach(function (b) {
    b.addEventListener("click", function () {
        document.querySelectorAll("[data-source]").forEach(function (x) {
            x.classList.remove("active");
        });
        b.classList.add("active");
        var src = b.getAttribute("data-source");
        state.page =
            src === "db" ? "db" : src === "app" ? "queries" : "overview";
        activatePage();
    });
});
$("global-search").addEventListener("input", function () {
    state.search = this.value.trim().toLowerCase();
    renderAll();
});
$("f-db").addEventListener("change", function () {
    state.db = this.value;
    renderDb();
});
$("f-range").addEventListener("change", load);
$("f-env").addEventListener("change", function () {
    state.environment = this.value.trim();
    load();
});
$("f-app").addEventListener("change", function () {
    state.appName = this.value.trim();
    load();
});
$("f-refresh").addEventListener("change", restart);
$("refresh").addEventListener("click", load);
document.addEventListener("keydown", function (e) {
    if (e.key === "/" && document.activeElement !== $("global-search")) {
        e.preventDefault();
        $("global-search").focus();
    }
});
activatePage();
load();
restart();
