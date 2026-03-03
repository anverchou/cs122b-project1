import re
import sys
import statistics

TS_RE = re.compile(r"\bTS=(\d+)\b")
TJ_RE = re.compile(r"\bTJ=(-?\d+)\b")

def main():
    if len(sys.argv) < 2:
        print("Usage: python3 log_processing.py <path_to_log_file>")
        sys.exit(1)

    path = sys.argv[1]
    ts_vals = []
    tj_vals = []

    with open(path, "r", encoding="utf-8", errors="ignore") as f:
        for line in f:
            m1 = TS_RE.search(line)
            m2 = TJ_RE.search(line)
            if not m1 or not m2:
                continue
            ts = int(m1.group(1))
            tj = int(m2.group(1))
            if ts <= 0 or tj < 0:
                continue
            ts_vals.append(ts)
            tj_vals.append(tj)

    n = len(ts_vals)
    if n == 0:
        print("No valid samples found.")
        sys.exit(1)

    avg_ts = sum(ts_vals) / n
    avg_tj = sum(tj_vals) / n
    med_ts = statistics.median(ts_vals)
    med_tj = statistics.median(tj_vals)

    print(f"samples={n}")
    print(f"TS_avg_ns={avg_ts:.2f}  TS_avg_ms={avg_ts/1e6:.4f}")
    print(f"TJ_avg_ns={avg_tj:.2f}  TJ_avg_ms={avg_tj/1e6:.4f}")
    print(f"TS_median_ms={med_ts/1e6:.4f}  TJ_median_ms={med_tj/1e6:.4f}")
    print(f"TS_min_ms={min(ts_vals)/1e6:.4f}  TS_max_ms={max(ts_vals)/1e6:.4f}")
    print(f"TJ_min_ms={min(tj_vals)/1e6:.4f}  TJ_max_ms={max(tj_vals)/1e6:.4f}")

if __name__ == "__main__":
    main()