import { $, state } from "./shared.js";

const API = "/api/v1/metrics";

function getJson(url) {
    return fetch(url, { headers: { Accept: "application/json" } }).then(
        function (r) {
            if (!r.ok) throw new Error(r.status + " " + url);
            return r.json();
        },
    );
}

function metricParams() {
    var to = new Date(),
        from = new Date(to.getTime() - Number($("f-range").value)),
        p = new URLSearchParams();
    p.set("from", from.toISOString());
    p.set("to", to.toISOString());
    if (state.environment) p.set("environment", state.environment);
    if (state.appName) p.set("appName", state.appName);
    return p;
}

function healthParams() {
    var p = new URLSearchParams();
    if (state.environment) p.set("environment", state.environment);
    if (state.appName) p.set("appName", state.appName);
    return p;
}

function url(path, params, extra) {
    var p = new URLSearchParams(params);
    Object.keys(extra || {}).forEach(function (k) {
        p.set(k, extra[k]);
    });
    return API + path + "?" + p.toString();
}

export async function loadMetrics() {
    const p = metricParams();
    const hp = healthParams();
    const [
        stats,
        slow,
        heat,
        queries,
        txs,
        serverQueries,
        serverAggregates,
        health,
        txSeries,
    ] = await Promise.all([
        getJson(url("/transactions/stats", p)),
        getJson(url("/queries/slow", p, { limit: 100 })),
        getJson(url("/series/queries", p)),
        getJson(url("/queries", p, { limit: 500 })),
        getJson(url("/transactions", p, { limit: 100 })),
        getJson(API + "/server-queries?limit=200"),
        getJson(API + "/server-query-aggregates?limit=200"),
        getJson(url("/collection-health", hp)),
        getJson(url("/series/transactions", p)),
    ]);
    return {
        stats,
        slow,
        heat,
        queries,
        txs,
        serverQueries,
        serverAggregates,
        health,
        txSeries,
    };
}

export function getTransactionQueries(id) {
    return getJson(
        API + "/transactions/" + encodeURIComponent(id) + "/queries",
    );
}
