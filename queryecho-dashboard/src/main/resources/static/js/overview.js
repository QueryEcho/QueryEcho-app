import { $, el, empty, fmtCount, fmtDur, fmtTime, state } from "./shared.js";

export function renderKpis() {
    var d = state.data,
        s = d.stats,
        pct = s.total ? s.rollbackRate * 100 : 0,
        total = (d.heat.buckets || []).reduce(function (n, b) {
            return n + Number(b.executionCount || 0);
        }, 0);
    $("k-query").textContent = fmtCount(total);
    $("k-tx").textContent = fmtCount(s.total);
    $("k-tx-foot").textContent =
        "commit " +
        fmtCount(s.commitCount) +
        " · rollback " +
        fmtCount(s.rollbackCount);
    $("k-rollback").textContent = s.total ? pct.toFixed(1) + "%" : "—";
    $("k-slow").textContent = fmtCount(d.slow.count);
    $("k-slow-foot").textContent = "threshold " + fmtDur(d.slow.thresholdUs);
    $("k-avg").textContent = s.total ? fmtDur(s.avgDurationUs) : "—";
}

export function renderOutcome() {
    var s = state.data.stats,
        total = Math.max(1, s.total),
        commit = (s.commitCount / total) * 100,
        rollback = ((s.commitCount + s.rollbackCount) / total) * 100,
        host = $("outcome");
    host.textContent = "";
    var donut = el("div", "donut");
    donut.style.setProperty("--commit", commit + "%");
    donut.style.setProperty("--rollback", rollback + "%");
    var center = el("div", "donut-center");
    center.appendChild(el("b", "", fmtCount(s.total)));
    center.appendChild(el("small", "", "TRANSACTIONS"));
    donut.appendChild(center);
    host.appendChild(donut);
    var list = el("div", "outcome-list");
    [
        ["Commit", s.commitCount, "var(--green)"],
        ["Rollback", s.rollbackCount, "var(--red)"],
        ["Unknown", s.unknownCount, "#626a75"],
    ].forEach(function (x) {
        var r = el("div", "outcome-row"),
            i = el("i");
        i.style.setProperty("--c", x[2]);
        r.appendChild(i);
        r.appendChild(el("span", "", x[0]));
        r.appendChild(el("b", "", fmtCount(x[1])));
        list.appendChild(r);
    });
    host.appendChild(list);
}

export function renderLatency() {
    var host = $("latency-chart"),
        buckets = state.data.heat.buckets || [];
    host.textContent = "";
    $("latency-meta").textContent =
        state.data.heat.bucketSeconds + "-second rollup buckets";
    if (!buckets.length) {
        host.appendChild(empty("아직 쿼리 시계열이 없습니다."));
        return;
    }
    var W = 900,
        H = 180,
        m = { l: 48, r: 18, t: 12, b: 25 },
        iw = W - m.l - m.r,
        ih = H - m.t - m.b,
        max = Math.max.apply(
            null,
            buckets.map(function (b) {
                return b.maxDurationUs;
            }),
        );
    max = Math.max(max, 1);
    var ns = "http://www.w3.org/2000/svg";
    function se(tag, a) {
        var n = document.createElementNS(ns, tag);
        Object.keys(a || {}).forEach(function (k) {
            n.setAttribute(k, a[k]);
        });
        return n;
    }
    var svg = se("svg", {
        viewBox: "0 0 " + W + " " + H,
        role: "img",
        "aria-label": "Query latency over time",
    });
    for (var g = 0; g < 4; g++) {
        var gy = m.t + (ih * g) / 3,
            val = max * (1 - g / 3),
            line = se("line", {
                x1: m.l,
                y1: gy,
                x2: W - m.r,
                y2: gy,
                class: "grid-line",
            }),
            tx = se("text", {
                x: m.l - 7,
                y: gy + 3,
                "text-anchor": "end",
                class: "axis-label",
            });
        tx.textContent = fmtDur(val);
        svg.appendChild(line);
        svg.appendChild(tx);
    }
    function x(i) {
        return (
            m.l +
            (buckets.length === 1 ? iw / 2 : (iw * i) / (buckets.length - 1))
        );
    }
    function y(v) {
        return m.t + ih - (v / max) * ih;
    }
    var avg = [],
        peak = [];
    buckets.forEach(function (b, i) {
        avg.push(x(i) + "," + y(b.avgDurationUs));
        peak.push(x(i) + "," + y(b.maxDurationUs));
    });
    svg.appendChild(
        se("polygon", {
            points:
                m.l +
                "," +
                (m.t + ih) +
                " " +
                avg.join(" ") +
                " " +
                (W - m.r) +
                "," +
                (m.t + ih),
            fill: "rgba(87,148,242,.10)",
        }),
    );
    svg.appendChild(
        se("polyline", {
            points: avg.join(" "),
            fill: "none",
            stroke: "#5794f2",
            "stroke-width": "1.5",
        }),
    );
    svg.appendChild(
        se("polyline", {
            points: peak.join(" "),
            fill: "none",
            stroke: "#ff9830",
            "stroke-width": "1.2",
        }),
    );
    var stride = Math.max(1, Math.ceil(buckets.length / 6));
    buckets.forEach(function (b, i) {
        if (i % stride === 0 || i === buckets.length - 1) {
            var tx = se("text", {
                x: x(i),
                y: H - 7,
                "text-anchor": "middle",
                class: "axis-label",
            });
            tx.textContent = fmtTime(b.bucketStart);
            svg.appendChild(tx);
        }
    });
    host.appendChild(svg);
}

export function renderHealth() {
    var h = state.data.health || {},
        captured = Number(h.sdkCapturedTotal || 0),
        stored = Number(h.collectorPersistedTotal || 0),
        dropped = Number(h.sdkDroppedTotal || 0),
        failed =
            Number(h.collectorPersistenceFailedTotal || 0) +
            Number(h.collectorRejectedTotal || 0),
        rate = captured ? (stored / captured) * 100 : null;
    $("h-captured").textContent = fmtCount(captured);
    $("h-stored").textContent = fmtCount(stored);
    $("h-dropped").textContent = fmtCount(dropped);
    $("h-failed").textContent = fmtCount(failed);
    $("h-rate").textContent = rate === null ? "—" : rate.toFixed(1) + "%";
    $("health-meta").textContent =
        "SDK → Collector → PostgreSQL · " +
        (h.instances || []).length +
        " instances";
    var host = $("health-instances");
    host.textContent = "";
    if (!(h.instances || []).length) {
        host.appendChild(
            el(
                "span",
                "muted",
                "아직 SDK 상태 보고가 없습니다. LOCAL 모드는 저장 수만 표시됩니다.",
            ),
        );
        return;
    }
    (h.instances || []).forEach(function (i) {
        var n = el(
                "span",
                "health-instance " + String(i.status || "").toLowerCase(),
            ),
            dot = el("i");
        n.appendChild(dot);
        n.appendChild(
            document.createTextNode(
                i.appName +
                    " / " +
                    i.instanceId +
                    " · " +
                    i.status +
                    " · drop " +
                    fmtCount(i.droppedTotal),
            ),
        );
        host.appendChild(n);
    });
}
