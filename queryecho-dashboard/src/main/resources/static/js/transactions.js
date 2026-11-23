import { getTransactionQueries } from "./api.js";
import {
    $,
    apiNode,
    apiText,
    el,
    empty,
    fmtDur,
    fmtTime,
    matches,
    shortName,
    state,
    statusNode,
    toast,
} from "./shared.js";

function filteredTransactions() {
    return state.data.txs.filter(function (t) {
        return (
            matches(t.transactionName) ||
            matches(t.failureReason) ||
            matches(t.transactionId) ||
            matches(t.httpPath) ||
            matches(t.handlerName)
        );
    });
}

function txFor(id) {
    return state.data.txs.find(function (t) {
        return t.transactionId === id;
    });
}

function makeDebugger() {
    var panel = el("div", "panel debug-grid"),
        left = el("div", "debug-list"),
        right = el("div", "debug-detail"),
        lh = el("div", "panel-head");
    lh.appendChild(el("span", "source app-source", "APP_PROXY"));
    lh.appendChild(el("h2", "", "API → Transaction → SQL"));
    lh.appendChild(
        el("span", "meta right", filteredTransactions().length + " traces"),
    );
    left.appendChild(lh);
    var tool = el("div", "sub-toolbar");
    tool.appendChild(
        el(
            "span",
            "",
            "API 요청에서 실행된 트랜잭션과 JDBC 쿼리를 시간순으로 추적합니다.",
        ),
    );
    left.appendChild(tool);
    var list = el("div", "trace-list"),
        txs = filteredTransactions();
    if (!txs.length) list.appendChild(empty("수집된 트랜잭션이 없습니다."));
    txs.forEach(function (t) {
        var row = el(
            "button",
            "trace-row" + (t.transactionId === state.txId ? " active" : ""),
        );
        row.type = "button";
        row.appendChild(el("span", "trace-time", fmtTime(t.executedAt)));
        var nm = el("span", "trace-name");
        nm.appendChild(
            el("b", "", t.httpPath ? apiText(t) : shortName(t.transactionName)),
        );
        nm.appendChild(
            el("small", "", shortName(t.transactionName) + " · " + t.appName),
        );
        row.appendChild(nm);
        var dur = el("span", "trace-duration");
        dur.appendChild(el("span", "", fmtDur(t.durationUs)));
        dur.appendChild(document.createElement("br"));
        dur.appendChild(statusNode(t.status));
        row.appendChild(dur);
        row.addEventListener("click", function () {
            selectTransaction(t.transactionId);
        });
        list.appendChild(row);
    });
    left.appendChild(list);
    renderTraceDetail(right, txFor(state.txId));
    panel.appendChild(left);
    panel.appendChild(right);
    return panel;
}

function renderTraceDetail(host, tx) {
    host.textContent = "";
    var h = el("div", "panel-head");
    h.appendChild(el("h2", "", "Trace detail"));
    h.appendChild(
        el("span", "meta", tx ? tx.transactionId : "Select a transaction"),
    );
    host.appendChild(h);
    if (!tx) {
        host.appendChild(
            empty("왼쪽 트랜잭션을 선택하면 연관 쿼리가 표시됩니다."),
        );
        return;
    }
    var sum = el("div", "trace-summary"),
        top = el("div", "trace-summary-top"),
        title = el("div");
    title.appendChild(statusNode(tx.status));
    if (tx.httpPath) title.appendChild(el("div", "api-call", apiText(tx)));
    title.appendChild(el("h3", "", tx.transactionName));
    top.appendChild(title);
    top.appendChild(el("div", "duration", fmtDur(tx.durationUs)));
    sum.appendChild(top);
    var meta = el("div", "meta-row");
    [
        ["handler", tx.handlerName],
        ["request", tx.requestId],
        ["app", tx.appName],
        ["environment", tx.environment],
        ["instance", tx.instanceId],
        ["thread", tx.threadName],
        ["completed", fmtTime(tx.executedAt)],
    ].forEach(function (x) {
        var s = el("span");
        s.appendChild(el("b", "", x[0] + " "));
        s.appendChild(document.createTextNode(x[1] || "—"));
        meta.appendChild(s);
    });
    sum.appendChild(meta);
    if (tx.failureReason)
        sum.appendChild(el("div", "failure", tx.failureReason));
    host.appendChild(sum);
    var wf = el("div", "waterfall"),
        wh = el("div", "waterfall-head");
    wh.appendChild(el("span", "", "JDBC statement"));
    wh.appendChild(el("span", "", "duration"));
    wh.appendChild(el("span", "", "transaction timeline"));
    wf.appendChild(wh);
    if (!state.txQueries.length)
        wf.appendChild(empty("이 트랜잭션에 연결된 쿼리가 없습니다."));
    else {
        var txEnd = new Date(tx.executedAt).getTime(),
            txMs = Math.max(1, tx.durationUs / 1000),
            txStart = txEnd - txMs;
        state.txQueries.forEach(function (q) {
            var row = el(
                "div",
                "span-row" +
                    (q.slow ? " slow" : "") +
                    (!q.succeeded ? " failed" : ""),
            );
            row.appendChild(el("span", "span-name", q.normalizedSql));
            row.appendChild(el("span", "span-dur", fmtDur(q.durationUs)));
            var track = el("span", "span-track"),
                bar = el("i", "span-bar"),
                qEnd = new Date(q.executedAt).getTime(),
                qStart = qEnd - q.durationUs / 1000,
                left = Math.max(
                    0,
                    Math.min(99, ((qStart - txStart) / txMs) * 100),
                ),
                width = Math.max(
                    0.8,
                    Math.min(100 - left, (q.durationUs / 1000 / txMs) * 100),
                );
            bar.style.left = left + "%";
            bar.style.width = width + "%";
            track.appendChild(bar);
            row.appendChild(track);
            row.addEventListener("click", function () {
                state.query = q;
                renderDebuggers();
            });
            wf.appendChild(row);
        });
    }
    host.appendChild(wf);
    var q =
        state.query && state.query.transactionId === tx.transactionId
            ? state.query
            : state.txQueries[0];
    if (q) {
        state.query = q;
        var ins = el("div", "query-inspector");
        ins.appendChild(el("div", "inspector-label", "Selected SQL"));
        if (q.httpPath) ins.appendChild(apiNode(q));
        ins.appendChild(el("pre", "sql-block", q.normalizedSql));
        var im = el("div", "inspector-meta");
        [
            ["handler", q.handlerName],
            ["request", q.requestId],
            ["duration", fmtDur(q.durationUs)],
            ["datasource", q.datasourceName],
            ["params", q.paramCount],
            ["thread", q.threadName],
            ["event", q.eventId],
        ].forEach(function (x) {
            var s = el("span");
            s.appendChild(el("b", "", x[0] + " "));
            s.appendChild(document.createTextNode(x[1] == null ? "—" : x[1]));
            im.appendChild(s);
        });
        ins.appendChild(im);
        host.appendChild(ins);
    }
}

export function renderDebuggers() {
    ["overview-debug", "trace-debug"].forEach(function (id) {
        var h = $(id);
        h.textContent = "";
        h.appendChild(makeDebugger());
    });
}

export function selectTransaction(id, preferred) {
    state.txId = id;
    state.query = preferred || null;
    renderDebuggers();
    getTransactionQueries(id)
        .then(function (q) {
            if (state.txId !== id) return;
            state.txQueries = q;
            state.query = preferred || q[0] || null;
            renderDebuggers();
        })
        .catch(function () {
            toast("트랜잭션 쿼리를 불러오지 못했습니다.");
        });
}
