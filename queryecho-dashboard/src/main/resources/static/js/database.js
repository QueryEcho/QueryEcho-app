import {
    $,
    empty,
    fmtCount,
    fmtDur,
    fmtTime,
    matches,
    state,
    table,
} from "./shared.js";

function filteredDb(items) {
    return items.filter(function (q) {
        return (
            (state.db === "all" || q.dbType === state.db) &&
            (matches(q.normalizedSql) ||
                matches(q.clientProgram) ||
                matches(q.dbInstanceId))
        );
    });
}

export function renderDb() {
    var qs = filteredDb(state.data.serverQueries),
        aggs = filteredDb(state.data.serverAggregates),
        executions = aggs.reduce(function (n, q) {
            return n + Number(q.executionCount || 0);
        }, 0);
    $("source-app").textContent =
        fmtCount(state.data.queries.length) + " queries";
    $("source-db").textContent =
        fmtCount(qs.length + executions) + " executions";
    var h = $("server-query-table");
    h.textContent = "";
    if (!qs.length)
        h.appendChild(empty("개별 DB 서버 실행 데이터가 없습니다."));
    else
        h.appendChild(
            table(
                [
                    { label: "observed" },
                    { label: "database" },
                    { label: "duration", cls: "num" },
                    { label: "type" },
                    { label: "normalized SQL" },
                    { label: "client" },
                    { label: "rows", cls: "num" },
                ],
                qs.map(function (q) {
                    return {
                        cells: [
                            { text: fmtTime(q.observedAt), cls: "mono muted" },
                            {
                                text: q.dbType + " / " + q.dbInstanceId,
                                cls: "mono",
                            },
                            { text: fmtDur(q.durationUs), cls: "num" },
                            { text: q.statementType },
                            { text: q.normalizedSql, cls: "sql" },
                            {
                                text:
                                    (q.clientProgram || "unknown") +
                                    " · " +
                                    (q.dbUser || "unknown"),
                                cls: "muted",
                            },
                            {
                                text:
                                    fmtCount(q.rowsExamined) +
                                    " / " +
                                    fmtCount(q.rowsSent),
                                cls: "num",
                            },
                        ],
                    };
                }),
            ),
        );
    var a = $("server-aggregate-table");
    a.textContent = "";
    if (!aggs.length) a.appendChild(empty("집계형 DB 서버 데이터가 없습니다."));
    else
        a.appendChild(
            table(
                [
                    { label: "observed" },
                    { label: "database" },
                    { label: "executions", cls: "num" },
                    { label: "average", cls: "num" },
                    { label: "total", cls: "num" },
                    { label: "rows", cls: "num" },
                    { label: "normalized SQL" },
                ],
                aggs.map(function (q) {
                    return {
                        cells: [
                            { text: fmtTime(q.observedAt), cls: "mono muted" },
                            {
                                text: q.dbType + " / " + q.dbInstanceId,
                                cls: "mono",
                            },
                            { text: fmtCount(q.executionCount), cls: "num" },
                            { text: fmtDur(q.avgDurationUs), cls: "num" },
                            { text: fmtDur(q.totalDurationUs), cls: "num" },
                            { text: fmtCount(q.rowsProcessed), cls: "num" },
                            { text: q.normalizedSql, cls: "sql" },
                        ],
                    };
                }),
            ),
        );
}
