import {
    $,
    apiNode,
    empty,
    fmtDur,
    fmtTime,
    matches,
    state,
    statusNode,
    table,
} from "./shared.js";

function queryRows(items, openQuery) {
    return items
        .filter(function (q) {
            return (
                matches(q.normalizedSql) ||
                matches(q.threadName) ||
                matches(q.transactionId) ||
                matches(q.httpPath) ||
                matches(q.handlerName)
            );
        })
        .map(function (q) {
            return {
                click: function () {
                    openQuery(q);
                },
                cells: [
                    { text: fmtTime(q.executedAt), cls: "mono muted" },
                    { node: apiNode(q) },
                    { node: statusNode(q.succeeded) },
                    { text: fmtDur(q.durationUs), cls: "num" },
                    { text: q.normalizedSql, cls: "sql" },
                    { text: q.datasourceName, cls: "mono muted" },
                    {
                        text: q.transactionId ? "linked" : "standalone",
                        cls: "mono muted",
                    },
                ],
            };
        });
}

export function renderQueries(openQuery) {
    var host = $("query-table"),
        rows = queryRows(state.data.queries, openQuery);
    host.textContent = "";
    $("query-count").textContent = rows.length + " rows";
    if (!rows.length) host.appendChild(empty("조건에 맞는 쿼리가 없습니다."));
    else
        host.appendChild(
            table(
                [
                    { label: "time" },
                    { label: "API" },
                    { label: "result" },
                    { label: "duration", cls: "num" },
                    { label: "normalized SQL" },
                    { label: "datasource" },
                    { label: "transaction" },
                ],
                rows,
            ),
        );
}

export function renderSlow(openQuery) {
    var host = $("slow-table"),
        items = state.data.slow.items.filter(function (q) {
            return (
                matches(q.normalizedSql) ||
                matches(q.threadName) ||
                matches(q.httpPath)
            );
        });
    host.textContent = "";
    $("slow-meta").textContent =
        "threshold " +
        fmtDur(state.data.slow.thresholdUs) +
        " · " +
        items.length +
        " rows";
    if (!items.length)
        host.appendChild(empty("임계값을 넘은 쿼리가 없습니다."));
    else
        host.appendChild(
            table(
                [
                    { label: "time" },
                    { label: "API" },
                    { label: "duration", cls: "num" },
                    { label: "normalized SQL" },
                    { label: "thread" },
                    { label: "transaction" },
                ],
                items.map(function (q) {
                    return {
                        click: function () {
                            openQuery(q);
                        },
                        cells: [
                            { text: fmtTime(q.executedAt), cls: "mono muted" },
                            { node: apiNode(q) },
                            { text: fmtDur(q.durationUs), cls: "num" },
                            { text: q.normalizedSql, cls: "sql" },
                            { text: q.threadName, cls: "mono muted" },
                            {
                                text: q.transactionId
                                    ? "open trace"
                                    : "standalone",
                                cls: "mono muted",
                            },
                        ],
                    };
                }),
            ),
        );
    var rep = $("repeat-table"),
        by = {};
    state.data.queries.forEach(function (q) {
        var k = q.threadName + "|" + q.normalizedSql;
        if (!by[k] || q.recentRepeatCount > by[k].recentRepeatCount) by[k] = q;
    });
    var rows = Object.keys(by)
        .map(function (k) {
            return by[k];
        })
        .filter(function (q) {
            return (
                q.recentRepeatCount >= 5 &&
                (matches(q.normalizedSql) ||
                    matches(q.threadName) ||
                    matches(q.httpPath))
            );
        })
        .sort(function (a, b) {
            return b.recentRepeatCount - a.recentRepeatCount;
        });
    rep.textContent = "";
    if (!rows.length)
        rep.appendChild(empty("반복 패턴이 감지되지 않았습니다."));
    else
        rep.appendChild(
            table(
                [
                    { label: "API" },
                    { label: "repeat", cls: "num" },
                    { label: "normalized SQL" },
                    { label: "thread" },
                    { label: "last seen" },
                ],
                rows.map(function (q) {
                    return {
                        click: function () {
                            openQuery(q);
                        },
                        cells: [
                            { node: apiNode(q) },
                            { text: q.recentRepeatCount + "x", cls: "num" },
                            { text: q.normalizedSql, cls: "sql" },
                            { text: q.threadName, cls: "mono muted" },
                            { text: fmtTime(q.executedAt), cls: "mono muted" },
                        ],
                    };
                }),
            ),
        );
}
