#!/usr/bin/env python
"""Download the Android build toolchain into .toolchain/downloads (resumable)."""
import concurrent.futures as cf
import os
import sys
import time
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, ".toolchain", "downloads")

ITEMS = [
    ("jdk17.zip", "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.13%2B11/OpenJDK17U-jdk_x64_windows_hotspot_17.0.13_11.zip"),
    ("cmdline-tools.zip", "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"),
    ("gradle-8.7-bin.zip", "https://services.gradle.org/distributions/gradle-8.7-bin.zip"),
]


def fetch(name, url):
    os.makedirs(OUT, exist_ok=True)
    dst = os.path.join(OUT, name)
    part = dst + ".part"
    for attempt in range(1, 7):
        have = os.path.getsize(part) if os.path.exists(part) else 0
        req = urllib.request.Request(url)
        if have:
            req.add_header("Range", "bytes=%d-" % have)
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                mode = "ab" if have and r.status == 206 else "wb"
                if mode == "wb":
                    have = 0
                total = r.headers.get("Content-Length")
                total = int(total) + have if total else None
                t0 = time.time()
                with open(part, mode) as f:
                    while True:
                        chunk = r.read(1 << 20)
                        if not chunk:
                            break
                        f.write(chunk)
                        have += len(chunk)
                        if total:
                            pct = 100.0 * have / total
                            sys.stdout.write("\r%s %5.1f%% %6.1fMB/%6.1fMB" % (name, pct, have / 2**20, total / 2**20))
                            sys.stdout.flush()
            if not total or have >= total:
                if os.path.exists(dst):
                    os.remove(dst)
                os.rename(part, dst)
                print("\n%s done %d bytes in %.0fs" % (name, have, time.time() - t0))
                return name, True, have
        except Exception as e:  # noqa: BLE001
            print("\n%s attempt %d failed: %s" % (name, attempt, e))
            time.sleep(3 * attempt)
    return name, False, os.path.getsize(part) if os.path.exists(part) else 0


def main():
    results = []
    with cf.ThreadPoolExecutor(max_workers=3) as ex:
        for res in ex.map(lambda it: fetch(*it), ITEMS):
            results.append(res)
    print("\n=== summary ===")
    ok = True
    for name, success, size in results:
        print("%-24s %s %d" % (name, "OK" if success else "FAIL", size))
        ok = ok and success
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
