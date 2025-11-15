import argparse
import json
import sys
from pathlib import Path

def extract_sentences(data):
    logs = data.get("logs", [])
    if not isinstance(logs, list):
        return []

    out = []
    for node in logs:
        if not isinstance(node, dict):
            continue
        sentence = node.get("sentence")
        if not isinstance(sentence, str):
            continue
        s = sentence.rstrip()
        s1 = s.replace('.', '')
        s1 = s1.replace('<PAD>', '')
        if not s1.endswith("ERROR"):
            out.append(s1)
        
    return out


def main():
    print("Starting sentence extraction...")
    parser = argparse.ArgumentParser(
        description="Extract sentences from JSON['logs'] where sentence does not end with 'ERROR'."
    )
    parser.add_argument("json_path", help="Path to input JSON file.")
    parser.add_argument(
        "-o",
        "--output",
        dest="output_path",
        help="Path to output txt file (default: <json_stem>_sentences.txt in same directory).",
    )
    args = parser.parse_args()

    in_path = Path(args.json_path)
    if not in_path.is_file():
        print(f"Input JSON not found: {in_path}", file=sys.stderr)
        sys.exit(1)

    out_path = Path(args.output_path) if args.output_path else in_path.parent / f"{in_path.stem}_sentences.txt"

    try:
        with in_path.open("r", encoding="utf-8") as f:
            data = json.load(f)
    except json.JSONDecodeError as e:
        print(f"Invalid JSON: {e}", file=sys.stderr)
        sys.exit(1)
    except OSError as e:
        print(f"Failed to read input: {e}", file=sys.stderr)
        sys.exit(1)

    sentences = extract_sentences(data)

    try:
        with out_path.open("w", encoding="utf-8") as f:
            f.write("\n".join(sentences))
    except OSError as e:
        print(f"Failed to write output: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()