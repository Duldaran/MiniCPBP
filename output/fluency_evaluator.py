import json
import os
from pathlib import Path
import torch
from transformers import AutoTokenizer, AutoModelForCausalLM, pipeline
import numpy as np
import time
from tqdm import tqdm
import argparse
import math
from collections import defaultdict

device_gpu = "cuda" if torch.cuda.is_available() else "cpu"
device_cpu = "cpu"

start_time = time.time()    


path = os.path.join("..","src", "main", "java", "minicpbp", "examples", "data", "MNREAD", "TimesCost_modified.json")

folder_path = Path("Novembre_2025/experimental_suite_1/")

# Read and parse the file
with open(path, "r", encoding="utf-8") as f:
    char_cost = json.load(f)

'''
# --- 2. LLM evaluator (open-source) ---
eval_model_name = "mistralai/Mistral-7B-Instruct-v0.3"  # or "meta-llama/Meta-Llama-3-8B-Instruct"
eval_pipe = pipeline(
    "text-generation",
    model=eval_model_name,
    torch_dtype=torch.float16,
    device=0 if device_gpu == "cuda" else -1
)

def evaluate_fluency_llm(sentence: str) -> int:
    prompt = f"""
You are a linguistic evaluator. 
Evaluate the following sentence only for *fluency in English* (grammar, naturalness, clarity). 
Ignore content relevance.

Output only a number between 0 and 100.

Sentence: "{sentence}"
"""
    outputs = eval_pipe(prompt, max_new_tokens=10, do_sample=False)
    text = outputs[0]["generated_text"].replace(prompt, "").strip()
    # Extract number
    digits = "".join(ch for ch in text if ch.isdigit())
    return int(digits) if digits.isdigit() else None
'''

# --- 3. Perplexity model ---
ppl_model_name = "gpt2"  # could also use "EleutherAI/gpt-neo-1.3B"
ppl_tokenizer = AutoTokenizer.from_pretrained(ppl_model_name)
ppl_model = AutoModelForCausalLM.from_pretrained(ppl_model_name).to(device_cpu)

def calculate_perplexity(sentence: str) -> float:
    encodings = ppl_tokenizer(sentence, return_tensors="pt").to(device_cpu)
    with torch.no_grad():
        outputs = ppl_model(**encodings, labels=encodings.input_ids)
        loss = outputs.loss
    return torch.exp(loss).item()

def can_greedy_split(sentence: str, char_cost: dict) -> bool:
    """
    Greedy split of sentence into 3 lines.
    Each line must be adjustable (via flexible space cost) to exactly `target`.
    """
    space_min = 410
    space_max = 640
    target = 15896
    words = sentence.split()
    line_count = 0
    char_sum = 0  # sum of non-space char costs
    spaces = 0    # number of spaces in current line
    is_valid = True

    def line_can_reach(char_sum, spaces):
        # Compute min and max possible cost of this line
        min_cost = char_sum + spaces * space_min
        max_cost = char_sum + spaces * space_max
        return min_cost <= target <= max_cost

    for i, word in enumerate(words):
        # Add this word's characters
        word_cost = sum(char_cost.get(c, 0) for c in word)
        new_char_sum = char_sum + word_cost
        new_spaces = spaces + (1 if spaces > 0 or char_sum > 0 else 0)

        # Check if adding this word keeps the line possibly valid
        min_cost = new_char_sum + new_spaces * space_min
        if min_cost > target:  # too heavy already
            # finalize previous line
            if not line_can_reach(char_sum, spaces):
                is_valid = False
            line_count += 1
            char_sum = word_cost
            spaces = 0
        else:
            char_sum = new_char_sum
            spaces = new_spaces

    # finalize last line
    if not line_can_reach(char_sum, spaces):
        is_valid = False
    
    if not is_valid:
        is_valid = True
        for i, word in enumerate(reversed(words)):
            # Add this word's characters
            word_cost = sum(char_cost.get(c, 0) for c in word)
            new_char_sum = char_sum + word_cost
            new_spaces = spaces + (1 if spaces > 0 or char_sum > 0 else 0)

            # Check if adding this word keeps the line possibly valid
            min_cost = new_char_sum + new_spaces * space_min
            if min_cost > target:  # too heavy already
                # finalize previous line
                if not line_can_reach(char_sum, spaces):
                    is_valid = False
                line_count += 1
                char_sum = word_cost
                spaces = 0
            else:
                char_sum = new_char_sum
                spaces = new_spaces
                
        if not line_can_reach(char_sum, spaces):
            is_valid = False

    return is_valid



# Data structure to store multiple lists of (score, time) pairs for each combination
score_time_data = {}

def add_score_time(architecture, config, sentenceBuilder, score_timestamp_list):
    key = (architecture, config, sentenceBuilder)
    if key not in score_time_data:
        score_time_data[key] = []
    score_time_data[key].append(score_timestamp_list)

for file_path in folder_path.glob("*.json"):
    if "evaluation_results_" in file_path.stem or "_error" in file_path.stem:
        continue  # skip already processed files
    print("Processing:", file_path)
    
    # Example: load JSON content
    with open(file_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    # --- 1. Load sentences from JSON ---
    sentences = [
        entry
        for entry in data["logs"]
        if not entry["sentence"].strip().endswith("ERROR")
    ]

    problem = data["config"]
    sentenceBuilder = data.get("sentence_builder", "randomSentenceBuilder")
    name = file_path.stem
    if "NLP_MLM_v1" in name:
        architecture = "NLP_MLM_v1"
    elif "NLP_MLM_v2" in name:
        architecture = "NLP_MLM_v2"
    else:
        raise ValueError(f"Config 'name' must contain 'NLM_MLM_v1' or 'NLM_MLM_v2', got: {file_path.stem}")
    
    
    best_time_evolution_list = []
    for event in data["best_perplexity_evolution"]:         

        score = event.get("first")
        time_evolution = event.get("second")
        if score is not None and time_evolution is not None:
            try:
                best_time_evolution_list.append((float(score), int(time_evolution)))
            except Exception:
                pass

        # when we've reached the last event, add the whole evolution to score_time_data
        if event is data["best_perplexity_evolution"][-1]:
            add_score_time(architecture, problem, sentenceBuilder, best_time_evolution_list)
    
    if not sentences:        
        print("No valid sentences found, skipping.")
        continue
        continue
    
    # --- 4. Run evaluation ---
    results = []
    for sent in tqdm(sentences, desc="Evaluating sentences"):
        s=sent["sentence"].strip()
        #llm_score = evaluate_fluency_llm(s)
        if sent.get("perplexity") is None:
            ppl_score = calculate_perplexity(s)
        else:
            ppl_score = sent["perplexity"]
        if "MNREAD" in problem:
            results.append({"sentence": s,  "perplexity": ppl_score, "is_valid": can_greedy_split(s, char_cost)})
        else:
            results.append({"sentence": s, "perplexity": ppl_score})

    # --- 5. Summary stats ---
    #llm_scores = [r["LLM_fluency"] for r in results if r["LLM_fluency"] is not None]
    ppl_scores = [r["perplexity"] for r in results]
    
    results.sort(key=lambda r: r["perplexity"])
    
    

    summary = {
        #"LLM_avg": float(np.mean(llm_scores)) if llm_scores else None,
        #"LLM_min": float(np.min(llm_scores)) if llm_scores else None,
        #"LLM_max": float(np.max(llm_scores)) if llm_scores else None,
        "PPL_avg": float(np.mean(ppl_scores)) if ppl_scores else None,
        "PPL_min": float(np.min(ppl_scores)) if ppl_scores else None,
        "PPL_max": float(np.max(ppl_scores)) if ppl_scores else None
    }
    
    if "MNREAD" in problem:
        valid_count = sum(1 for r in results if r.get("is_valid"))
        summary["valid_sentences"] = valid_count
        summary["valid_percentage"] = (valid_count / len(results)) * 100 if results else 0

    # --- 6. Save results ---
    # Find best sentences based on scores
    #best_llm = max(results, key=lambda r: r["LLM_fluency"] if r["LLM_fluency"] is not None else float('-inf'))
    best_ppl = min(results, key=lambda r: r["perplexity"])

    output_data = {
        "results": results,
        "summary": summary,
        #"best_LLM_fluency": best_llm,
        "best_perplexity": best_ppl
    }

    # append the last part of the original filename (after the last underscore)
    suffix = file_path.stem.rsplit("_", 1)[-1]
    safe_suffix = "".join(c if c.isalnum() or c in "._-" else "_" for c in suffix)
    out_path = Path(folder_path) / f"evaluation_results_{architecture}_{problem}_{sentenceBuilder}_{safe_suffix}.json"
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(output_data, f, indent=2)
        
    # Write valid (or no is_valid) sentences to a text file: one line per sentence with perplexity
    txt_output_path = Path(folder_path) / f"valid_sentences_{architecture}_{problem}_{sentenceBuilder}_{safe_suffix}.txt"
    with open(txt_output_path, "w", encoding="utf-8") as ftxt:
        last_sentence = None
        for r in results:
            if not r.get("is_valid", True):
                continue
            if last_sentence is not None and r["sentence"] == last_sentence:
                continue
            ftxt.write(f"{r['sentence']}, {r['perplexity']}\n")
            last_sentence = r["sentence"]

    print("Evaluation done. Summary:")
    print(summary)
    end_time = time.time()
    print(f"Total evaluation time: {end_time - start_time:.2f} seconds")
    
import matplotlib.pyplot as plt

plots_dir = Path(folder_path) / "plots"
plots_dir.mkdir(parents=True, exist_ok=True)

# Group score_time_data by problem (config)
grouped = defaultdict(list)
for (arch, problem_key, sentenceBuilder), runs in score_time_data.items():
    grouped[problem_key].append(((arch, sentenceBuilder), runs))

for problem_key, combos in grouped.items():
    if not combos:
        continue

    n = len(combos)
    ncols = min(2, n)
    nrows = math.ceil(n / ncols)
    fig, axes = plt.subplots(nrows, ncols, figsize=(6 * ncols, 4 * nrows), squeeze=False)
    axes_flat = axes.flatten()

    any_plotted = False
    # sort combos for stable ordering
    for idx, ((arch, sentenceBuilder), runs) in enumerate(sorted(combos, key=lambda x: (x[0][0], x[0][1]))):
        ax = axes_flat[idx]
        plotted_this_subplot = False
        for run_idx, run in enumerate(runs):
            # run is expected to be a list of (score, time) tuples
            pts = [(float(s), int(t)) for s, t in run if s is not None and t is not None]
            if not pts:
                continue
            pts.sort(key=lambda x: x[1])  # sort by time
            scores = [p[0] for p in pts]
            times = [p[1] for p in pts]
            t0 = times[0]
            rel_times = [(t - t0) / 60.0 for t in times]
            ax.plot(rel_times, scores, marker="o", label=f"run {run_idx + 1}")
            plotted_this_subplot = True
            any_plotted = True

        if plotted_this_subplot:
            ax.set_xlabel("Time (seconds, relative)")
            ax.set_ylabel("Score")
            ax.set_title(f"{arch} / {sentenceBuilder}")
            ax.grid(True)
            ax.legend(loc="best")
        else:
            ax.axis("off")

    # hide any unused axes
    for j in range(n, len(axes_flat)):
        axes_flat[j].axis("off")

    if not any_plotted:
        plt.close(fig)
        continue

    fig.suptitle(f"Score evolution for problem: {problem_key}")
    fig.tight_layout(rect=[0, 0, 1, 0.96])

    fname = f"{problem_key}_combined.png"
    safe_fname = "".join(c if c.isalnum() or c in "._-" else "_" for c in fname)
    fig.savefig(plots_dir / safe_fname)
    plt.close(fig)
