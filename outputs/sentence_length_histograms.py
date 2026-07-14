#!/usr/bin/env python3
"""Create sentence-length histograms from result JSON files in outputs/."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Iterable, List

import matplotlib.pyplot as plt


def count_words(sentence: str) -> int:
    """Count words by splitting on spaces and ignoring empty pieces."""
    return len([part for part in sentence.split(" ") if part])


def iter_valid_json_files(directory: Path) -> Iterable[Path]:
    """Yield JSON files, excluding any file whose name contains backup or error."""
    for file_path in sorted(directory.glob("*.json")):
        lower_name = file_path.name.lower()
        if "backup" in lower_name or "error" in lower_name:
            continue
        yield file_path


def collect_lengths(directory: Path) -> tuple[List[int], List[int], int]:
    """Collect sentence lengths for base_sentence and logs from valid JSON files."""
    base_lengths: List[int] = []
    log_lengths: List[int] = []
    used_files = 0

    for file_path in iter_valid_json_files(directory):
        try:
            with file_path.open("r", encoding="utf-8") as handle:
                data = json.load(handle)
        except (OSError, json.JSONDecodeError):
            continue

        if not isinstance(data, dict):
            continue

        base_sentence = data.get("base_sentence")
        if isinstance(base_sentence, dict):
            sentence = base_sentence.get("sentence")
            if isinstance(sentence, str):
                base_lengths.append(count_words(sentence))

        logs = data.get("logs")
        if isinstance(logs, list):
            for entry in logs:
                if not isinstance(entry, dict):
                    continue
                sentence = entry.get("sentence")
                if isinstance(sentence, str):
                    log_lengths.append(count_words(sentence))

        used_files += 1

    return base_lengths, log_lengths, used_files


def build_histogram_bins(lengths: List[int]) -> List[int]:
    """Use one integer bin per sentence length value."""
    if not lengths:
        return [0, 1]
    maximum = max(lengths)
    return list(range(0, maximum + 2))


def main() -> None:
    parser = argparse.ArgumentParser(
        description=(
            "Build two histograms of sentence lengths from JSON files in outputs/: "
            "base_sentence lengths and logs sentence lengths."
        )
    )
    parser.add_argument(
        "--directory",
        type=Path,
        default=Path(__file__).resolve().parent,
        help="Directory containing JSON result files (default: outputs folder).",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path(__file__).resolve().parent / "sentence_length_histograms.png",
        help="Path for the saved histogram image.",
    )
    args = parser.parse_args()

    base_lengths, log_lengths, used_files = collect_lengths(args.directory)
    bins = build_histogram_bins(base_lengths + log_lengths)

    fig, axes = plt.subplots(1, 2, figsize=(14, 5), constrained_layout=True)

    axes[0].hist(base_lengths, bins=bins, edgecolor="black", alpha=0.8)
    axes[0].set_title("Base Sentence Lengths")
    axes[0].set_xlabel("Number of words")
    axes[0].set_ylabel("Count")

    axes[1].hist(log_lengths, bins=bins, edgecolor="black", alpha=0.8, color="tab:orange")
    axes[1].set_title("Generated Sentence Lengths")
    axes[1].set_xlabel("Number of words")
    axes[1].set_ylabel("Count")

    fig.suptitle(
        f"Sentence Length Histograms when using NeSyLNS+"
        ,
        fontsize=12,
    )

    args.output.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(args.output, dpi=150)
    print(f"Saved histogram image to: {args.output}")
    print(f"Files used: {used_files}")
    print(f"Base sentences counted: {len(base_lengths)}")
    print(f"Log sentences counted: {len(log_lengths)}")


if __name__ == "__main__":
    main()
