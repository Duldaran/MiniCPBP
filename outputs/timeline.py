"""
timeline_gantt.py

Reads the same result JSON files consumed by fluency_evaluator.py and turns the
per-run "timelogs" entries into Gantt-style timeline charts showing where the
wall-clock time of each run actually went (init, sentence building, model
building, neural (MLM) inference, CP setup, CP search/solve, iteration
overhead, ...).

For every JSON file it produces:
  - plots/timelines/<file_stem>_timeline.png
        A single detailed timeline for that run, one colored segment per
        phase, iteration boundaries marked with dashed vertical lines.
  - plots/timelines/<file_stem>_phase_breakdown.png
        A single stacked bar summarizing total time spent per phase for
        that run (a quick "where did the time go" summary).

It also groups runs by problem and produces a combined comparison chart:
  - plots/timelines/problem_<problem>_gantt_combined.png
        One timeline row per run (architecture | sentenceBuilder | seed),
        all aligned to their own start time, so you can compare where
        different configurations spend their time.

Usage:
    python timeline_gantt.py --input_dir output/ [--skip file1.json file2.json]
"""

import argparse
import json
from collections import defaultdict
from pathlib import Path

import matplotlib.pyplot as plt

# ---------------------------------------------------------------------------
# Phase colour palette
# ---------------------------------------------------------------------------
PHASE_COLORS = {
    "Init": "#B0B0B0",                    # gray
    "Sentence Building": "#F4A261",       # orange
    "Model Building": "#E9C46A",          # yellow
    "Neural Inference (MLM)": "#A8DADC",  # light blue
    "CP Setup": "#CDB4DB",                # light purple
    "CP Search / Solve": "#2A9D8F",       # teal green
    "Iteration Overhead": "#6C757D",      # dark gray
    "Finalization": "#ADB5BD",            # pale gray
    "Other": "#E63946",                   # red - flags an unmapped transition
}

# Maps a (from_event, to_event) pair, in the order they appear in "timelogs",
# to a named phase. Extend this if new event names show up in the logs.
EVENT_TRANSITIONS = {
    ("Iteration Start", "Sentence built"): "Sentence Building",
    ("Sentence built", "Model built"): "Model Building",
    ("Model built", "MLM response received"): "Neural Inference (MLM)",
    ("MLM response received", "Solve Start"): "CP Setup",
    ("Solve Start", "Solution Found"): "CP Search / Solve",
    ("Solution Found", "Iteration Start"): "Iteration Overhead",
}

# Preferred legend / stacking order
PHASE_ORDER = [
    "Init",
    "Sentence Building",
    "Model Building",
    "Neural Inference (MLM)",
    "CP Setup",
    "CP Search / Solve",
    "Iteration Overhead",
    "Finalization",
    "Other",
]


def build_segments(timelogs, total_time_seconds=None):
    """Turn a list of {"event", "iteration", "timestamp"} dicts (timestamps in
    ms) into a chronological list of {"start", "end", "phase", "iteration"}
    segments (start/end in seconds)."""

    events = sorted(timelogs, key=lambda e: (e["timestamp"], e.get("iteration", 0)))
    segments = []
    if not events:
        return segments

    first_ts = events[0]["timestamp"] / 1000.0
    if first_ts > 0:
        segments.append({
            "start": 0.0,
            "end": first_ts,
            "phase": "Init",
            "iteration": events[0].get("iteration"),
        })

    for prev, cur in zip(events, events[1:]):
        start = prev["timestamp"] / 1000.0
        end = cur["timestamp"] / 1000.0
        if end <= start:
            continue
        phase = EVENT_TRANSITIONS.get((prev["event"], cur["event"]), "Other")
        segments.append({
            "start": start,
            "end": end,
            "phase": phase,
            "iteration": prev.get("iteration"),
        })

    if total_time_seconds is not None:
        last_end = segments[-1]["end"]
        if total_time_seconds > last_end:
            segments.append({
                "start": last_end,
                "end": total_time_seconds,
                "phase": "Finalization",
                "iteration": events[-1].get("iteration"),
            })

    return segments


def iteration_boundaries(segments):
    """Timestamps where a new iteration starts (for vertical marker lines)."""
    boundaries = []
    seen_iters = set()
    for seg in segments:
        it = seg["iteration"]
        if it is not None and it not in seen_iters:
            seen_iters.add(it)
            boundaries.append(seg["start"])
    return boundaries


def _add_segments_to_axis(ax, segments, y_pos, height, used_labels):
    for seg in segments:
        color = PHASE_COLORS.get(seg["phase"], PHASE_COLORS["Other"])
        label = seg["phase"] if seg["phase"] not in used_labels else None
        ax.broken_barh(
            [(seg["start"], seg["end"] - seg["start"])],
            (y_pos, height),
            facecolors=color,
            edgecolor="white",
            linewidth=0.3,
            label=label,
        )
        used_labels.add(seg["phase"])


def _legend(ax, ncol=4):
    handles, labels = ax.get_legend_handles_labels()
    # keep a stable, readable order
    order = {p: i for i, p in enumerate(PHASE_ORDER)}
    pairs = sorted(zip(labels, handles), key=lambda hl: order.get(hl[0], 999))
    if pairs:
        labels, handles = zip(*pairs)
        ax.legend(
            handles, labels,
            loc="upper center", bbox_to_anchor=(0.5, -0.25),
            ncol=min(ncol, len(labels)), fontsize=8, frameon=False,
        )


def plot_single_gantt(segments, title, out_path):
    fig, ax = plt.subplots(figsize=(12, 2.4))
    used_labels = set()
    _add_segments_to_axis(ax, segments, y_pos=0, height=1, used_labels=used_labels)

    for b in iteration_boundaries(segments):
        ax.axvline(b, color="black", linewidth=0.6, alpha=0.3, linestyle="--")

    ax.set_ylim(0, 1)
    ax.set_yticks([])
    ax.set_xlim(0, max(seg["end"] for seg in segments) * 1.02)
    ax.set_xlabel("Time (seconds)")
    ax.set_title(title, fontsize=11)
    _legend(ax)
    fig.tight_layout()
    fig.savefig(out_path, dpi=150, bbox_inches="tight")
    plt.close(fig)


def plot_phase_breakdown(segments, title, out_path):
    totals = defaultdict(float)
    for seg in segments:
        totals[seg["phase"]] += seg["end"] - seg["start"]

    phases = [p for p in PHASE_ORDER if p in totals]
    fig, ax = plt.subplots(figsize=(10, 1.8))
    left = 0.0
    used_labels = set()
    for phase in phases:
        width = totals[phase]
        color = PHASE_COLORS.get(phase, PHASE_COLORS["Other"])
        ax.barh(0, width, left=left, color=color, edgecolor="white",
                label=phase if phase not in used_labels else None)
        used_labels.add(phase)
        if width / sum(totals.values()) > 0.04:
            ax.text(left + width / 2, 0, f"{width:.0f}s", ha="center", va="center",
                    fontsize=8, color="black")
        left += width

    ax.set_xlim(0, left * 1.0)
    ax.set_yticks([])
    ax.set_xlabel("Time (seconds)")
    ax.set_title(title, fontsize=11)
    _legend(ax)
    fig.tight_layout()
    fig.savefig(out_path, dpi=150, bbox_inches="tight")
    plt.close(fig)


def plot_combined_gantt(run_segments, title, out_path):
    """run_segments: list of (row_label, segments) tuples, one per run."""
    n = len(run_segments)
    fig, ax = plt.subplots(figsize=(13, 0.6 * n + 1.5))
    used_labels = set()
    max_end = 0.0

    row_height = 0.8
    for i, (label, segments) in enumerate(run_segments):
        y = i
        _add_segments_to_axis(ax, segments, y_pos=y, height=row_height, used_labels=used_labels)
        if segments:
            max_end = max(max_end, max(seg["end"] for seg in segments))

    ax.set_yticks([i + row_height / 2 for i in range(n)])
    ax.set_yticklabels([label for label, _ in run_segments], fontsize=8)
    ax.set_ylim(0, n)
    ax.invert_yaxis()
    ax.set_xlim(0, max_end * 1.02 if max_end else 1)
    ax.set_xlabel("Time since run start (seconds)")
    ax.set_title(title, fontsize=12)
    _legend(ax, ncol=4)
    fig.tight_layout()
    fig.savefig(out_path, dpi=150, bbox_inches="tight")
    plt.close(fig)


# ---------------------------------------------------------------------------
# Metadata extraction (mirrors fluency_evaluator.py's logic, kept minimal so
# this script can run standalone)
# ---------------------------------------------------------------------------
def extract_metadata(data, file_stem):
    is_molecule = "MOLECULE" in file_stem
    if is_molecule:
        architecture = data.get("config", "default")
        problem = "molecules"
    else:
        problem = data.get("config", "unknown")
        if "CPBP_PLUS" in file_stem:
            architecture = "CPBP+"
        elif "CP" in file_stem and "CPBP" not in file_stem:
            architecture = "CP"
        elif "CPBP" in file_stem or "v2_AnyStart" in file_stem:
            architecture = "CPBP"
        else:
            architecture = file_stem

    sentence_builder = data.get("sentence_builder", "random")
    if "random" in sentence_builder:
        sentence_builder = "Random Masking"
    elif "perplexity" in sentence_builder:
        sentence_builder = "Perplexity Masking"

    seed = data.get("seed", "unknown")
    return architecture, problem, sentence_builder, seed


def main():
    parser = argparse.ArgumentParser(
        description="Generate Gantt-style timeline charts from result JSON files' timelogs.")
    parser.add_argument("--input_dir", type=str, default="output/",
                         help="Folder containing the JSON result files (default: output/)")
    parser.add_argument("--skip", type=str, nargs="*", default=[],
                         help="Space-separated list of filenames to skip")
    args = parser.parse_args()

    folder_path = Path(args.input_dir)
    skipped_files = set(args.skip)

    plots_dir = folder_path / "plots" / "timelines"
    plots_dir.mkdir(parents=True, exist_ok=True)

    # problem_key -> list of (row_label, segments)
    combined_by_problem = defaultdict(list)
    
    print(f"Processing JSON files in {folder_path} (skipping {len(skipped_files)} files)...")

    n_processed = 0
    for file_path in folder_path.glob("*.json"):
        if "evaluation_results_" in file_path.stem or "_error" in file_path.stem or "backup" in file_path.stem:
            continue
        if file_path.name in skipped_files:
            continue

        with open(file_path, "r", encoding="utf-8") as f:
            data = json.load(f)

        timelogs = data.get("timelogs")
        if not timelogs:
            continue

        total_time = data.get("time", None)
        segments = build_segments(timelogs, total_time_seconds=total_time)
        if not segments:
            continue

        architecture, problem, sentence_builder, seed = extract_metadata(data, file_path.stem)

        run_title = f"{file_path.stem}  ({architecture} | {sentence_builder} | seed {seed})"
        plot_single_gantt(segments, run_title, plots_dir / f"{file_path.stem}_timeline.png")
        plot_phase_breakdown(segments, run_title, plots_dir / f"{file_path.stem}_phase_breakdown.png")

        row_label = f"{architecture} | {sentence_builder} | seed {seed}"
        combined_by_problem[problem].append((row_label, segments))

        n_processed += 1
        print(f"Processed timelogs for {file_path.name}")

    for problem, run_segments in combined_by_problem.items():
        run_segments.sort(key=lambda r: r[0])
        plot_combined_gantt(
            run_segments,
            title=f"Where the time went — problem: {problem}",
            out_path=plots_dir / f"problem_{problem}_gantt_combined.png",
        )

    print(f"\nDone. {n_processed} run(s) with timelogs processed.")
    print(f"Timeline charts written to {plots_dir}")


if __name__ == "__main__":
    main()