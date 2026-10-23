"""`dumpsys gfxinfo <pkg> framestats` çıktısından aşama sürelerini (ms) özetler.

Kullanım: adb shell dumpsys gfxinfo dev.evim.tv framestats | python3 tools/framestats.py
"""
import statistics
import sys

rows, header = [], None
for line in sys.stdin:
    line = line.strip().rstrip(",")
    if line.startswith("Flags,"):
        header = line.split(",")
        continue
    if header and line and line[0].isdigit():
        values = line.split(",")
        if len(values) >= len(header):
            rows.append(dict(zip(header, map(int, values[: len(header)]))))
    elif header and line.startswith("---PROFILEDATA---") and rows:
        header = None

rows = [r for r in rows if r["Flags"] == 0]
if not rows:
    sys.exit("kare verisi yok")

phases = [
    ("girdi+animasyon", "HandleInputStart", "PerformTraversalsStart"),
    ("ölçüm/yerleşim", "PerformTraversalsStart", "DrawStart"),
    ("çizim kaydı (UI)", "DrawStart", "SyncQueued"),
    ("senkron", "SyncStart", "IssueDrawCommandsStart"),
    ("GPU komutları", "IssueDrawCommandsStart", "SwapBuffers"),
    ("swap", "SwapBuffers", "FrameCompleted"),
    ("TOPLAM", "IntendedVsync", "FrameCompleted"),
]
print(f"{len(rows)} kare")
for name, start, end in phases:
    durations = [(r[end] - r[start]) / 1e6 for r in rows if r[end] and r[start]]
    durations.sort()
    p90 = durations[int(len(durations) * 0.9) - 1]
    print(f"  {name:18s} medyan {statistics.median(durations):6.1f}  p90 {p90:6.1f}")
