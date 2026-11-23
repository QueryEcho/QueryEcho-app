export const state = {
    page: "overview",
    data: null,
    txId: null,
    txQueries: [],
    query: null,
    timer: null,
    search: "",
    db: "all",
    environment: "",
    appName: "",
};
export const $ = (id) => document.getElementById(id);
export const fmtCount = (n) => Number(n || 0).toLocaleString("ko-KR");

export function fmtDur(us) {
    us = Number(us || 0);
    return us >= 1e6
        ? (us / 1e6).toFixed(us >= 1e7 ? 1 : 2) + " s"
        : us >= 1e3
          ? (us / 1e3).toFixed(us >= 1e5 ? 0 : 1) + " ms"
          : us + " µs";
}

export function fmtTime(v) {
    if (!v) return "—";
    return new Intl.DateTimeFormat("ko-KR", {
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
        hour12: false,
    }).format(new Date(v));
}

export function shortName(v) {
    if (!v) return "—";
    var p = v.split(".");
    return p.length > 2 ? p.slice(-2).join(".") : v;
}

export function el(tag, cls, text) {
    var n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text !== undefined) n.textContent = text;
    return n;
}

export function apiText(v) {
    return v && v.httpPath
        ? (v.httpMethod || "HTTP") + " " + v.httpPath
        : "No HTTP context";
}

export function apiNode(v) {
    return el(
        "span",
        "api-badge " + String((v && v.httpMethod) || "http").toLowerCase(),
        apiText(v),
    );
}

export function statusNode(status) {
    var s = el(
        "span",
        "status " +
            (status === "COMMIT" || status === true
                ? "ok"
                : status === "ROLLBACK" || status === false
                  ? "fail"
                  : "unknown"),
    );
    s.textContent =
        status === true ? "SUCCESS" : status === false ? "FAILED" : status;
    return s;
}

export function empty(msg) {
    return el("div", "empty", msg);
}

export function matches(v) {
    return (
        !state.search ||
        String(v || "")
            .toLowerCase()
            .indexOf(state.search) >= 0
    );
}

export function toast(msg) {
    var n = $("toast");
    n.textContent = msg;
    n.classList.add("on");
    setTimeout(function () {
        n.classList.remove("on");
    }, 2200);
}

export function table(headers, rows) {
    var t = el("table"),
        thead = el("thead"),
        tr = el("tr"),
        body = el("tbody");
    headers.forEach(function (h) {
        tr.appendChild(el("th", h.cls || "", h.label));
    });
    thead.appendChild(tr);
    t.appendChild(thead);
    rows.forEach(function (row) {
        var r = el("tr", row.className || "");
        if (row.click) {
            r.classList.add("clickable");
            r.tabIndex = 0;
            r.addEventListener("click", row.click);
            r.addEventListener("keydown", function (e) {
                if (e.key === "Enter") row.click();
            });
        }
        row.cells.forEach(function (c) {
            var td = el("td", c.cls || "");
            if (c.node) td.appendChild(c.node);
            else td.textContent = c.text == null ? "—" : c.text;
            r.appendChild(td);
        });
        body.appendChild(r);
    });
    t.appendChild(body);
    return t;
}
