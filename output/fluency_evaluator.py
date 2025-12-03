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

folder_path = Path("Decembre 2025/molecules_results/")

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

def add_score_time(architecture, config, sentenceBuilder,top_k, mask_percentage, score_timestamp_list,seed, ref=None ):
    key = (architecture, config, sentenceBuilder, top_k, mask_percentage, ref, seed)
    if key not in score_time_data:
        score_time_data[key] = []
    score_time_data[key].append(score_timestamp_list)


base_seed = {}

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
    isMolecule = False
    if "MOLECULE" in file_path.stem:
        isMolecule=True
        ref_file = data.get("reference_file", "unknown")
        if "no_gpt" in ref_file:
            ref = "molecules_no-gpt"
        else:
            ref = "molecules_gpt"
        problem = "molecules"
    else:   
        problem = data["config"]
    sentenceBuilder = data.get("sentence_builder", "random")
    if "random" in sentenceBuilder:
        sentenceBuilder = "random"
    elif "perplexity" in sentenceBuilder:
        sentenceBuilder = "perplexity"
    else:
        raise ValueError(f"Unknown sentence builder: {sentenceBuilder}")
    seed = data.get("seed", "unknown")
    top_k = data.get("oracle_top_k", "unknown")
    mask_percentage = data.get("mask_percent", "unknown")
    if isMolecule:
        architecture = data.get("config", "default")
    else :
        architecture = file_path.stem
    if "v1_2" in architecture:
        architecture = "Full Knowledge (new)"
    elif "no_BP" in architecture or "noBP" in architecture:
        architecture = "No Belief Propagation"
    elif "v2" in architecture:
        architecture = "Partial Knowledge"
    elif "v1" in architecture:
        architecture = "Full Knowledge (original)"
    else:
        raise ValueError(f"Config 'name' must contain 'NLM_MLM_v1' or 'NLM_MLM_v2', got: {file_path.stem}")
    
    base_seed_key = (problem,seed, ref if isMolecule else None)
    base_sentence = data.get("base_sentence")
    if base_seed_key not in base_seed:
        base_seed[base_seed_key] = base_sentence.get("perplexity")
    
    best_time_evolution_list = []
    for event in data["best_perplexity_evolution"]:         

        score = event.get("score")
        time_evolution = event.get("time")/100
        # Additional info available
        
        if score is not None and time_evolution is not None:
            try:
                best_time_evolution_list.append((float(score), int(time_evolution)))
            except Exception:
                pass

        # when we've reached the last event, add the whole evolution to score_time_data
        if event is data["best_perplexity_evolution"][-1]:
            add_score_time(architecture, problem, sentenceBuilder, top_k, mask_percentage, best_time_evolution_list, seed, ref if isMolecule else None)
    
    if not sentences:        
        print("No valid sentences found, skipping.")
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
        "PPL_max": float(np.max(ppl_scores)) if ppl_scores else None,
        "PPL_median": float(np.median(ppl_scores)) if ppl_scores else None,
        "PPL_first_quartile": float(np.percentile(ppl_scores, 25)) if ppl_scores else None,
        "PPL_third_quartile": float(np.percentile(ppl_scores, 75)) if ppl_scores else None,
        "total_sentences": len(results), 
        "total_time_seconds": data.get("time", None),
    }
    
    if "MNREAD" in problem:
        valid_count = sum(1 for r in results if r.get("is_valid"))
        summary["valid_sentences"] = valid_count
        summary["valid_percentage"] = (valid_count / len(results)) * 100 if results else 0

    # --- 6. Save results ---
    # Find best sentences based on scores
    #best_llm = max(results, key=lambda r: r["LLM_fluency"] if r["LLM_fluency"] is not None else float('-inf'))
    best_ppl = min(results, key=lambda r: r["perplexity"])

    characteristics = {
        "architecture": architecture,
        "problem": problem,
        "sentenceBuilder": sentenceBuilder,
        "top_k": top_k,
        "number_iterations": data.get("num_iterations", None),
        "mask_percentage": mask_percentage,
        "ref": ref if isMolecule else None,
        "seed": seed,
        "base_sentence": data.get("base_sentence", None),
    }

    output_data = {
        "characteristics": characteristics,
        "summary": summary,
        #"best_LLM_fluency": best_llm,
        "best_perplexity": best_ppl,
        "results": results,
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
        ftxt.write("Sentence, Perplexity\n")
        if isMolecule:
            ftxt.write(f"Reference : {base_sentence['molecule']}, {base_sentence['score']}\n")
        else:
            ftxt.write(f"Reference : {base_sentence['sentence']}, {base_sentence['perplexity']}\n")
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

# First, group by problem_key and architecture
problems = defaultdict(lambda: defaultdict(lambda: defaultdict(list)))
for (arch, problem_key, sentenceBuilder, top_k, mask_percentage, ref, seed), runs in score_time_data.items():
    key = (sentenceBuilder, top_k, mask_percentage, ref, seed)
    problems[problem_key][arch][key].extend(runs)

# Now iterate once per (problem, architecture) combination
for problem_key, arch_data in problems.items():
    for arch, arch_problem_data in arch_data.items():
        # Extract all unique (ref, seed) pairs for this problem+arch
        seed_ref_pairs = sorted(set((ref, seed) for (_, _, _, ref, seed) in arch_problem_data.keys()))
        
        n = len(seed_ref_pairs)
        if n == 0:
            continue
        
        # Create subplot grid
        ncols = min(2, n)
        nrows = math.ceil(n / ncols)
        fig, axes = plt.subplots(nrows, ncols, figsize=(6 * ncols, 4 * nrows), squeeze=False)
        axes_flat = axes.flatten()
        
        any_plotted = False
        
        for idx, (current_ref, current_seed) in enumerate(seed_ref_pairs):
            ax = axes_flat[idx]
            plotted_this_subplot = False
            max_score_subplot = 0  # Track maximum score for THIS subplot only
            
            # Filter data for this specific (ref, seed) combination
            filtered_configs = {
                (sb, tk, mp): runs 
                for (sb, tk, mp, ref, seed), runs in arch_problem_data.items()
                if ref == current_ref and seed == current_seed
            }
            
            # Sort configurations for consistent ordering
            for (sentenceBuilder, top_k, mask_percentage), runs in sorted(filtered_configs.items()):
                if not runs:
                    continue
                
                # Plot first run (or could average multiple runs)
                run = runs[0]
                pts = [(float(s), int(t)) for s, t in run if s is not None and t is not None]
                if not pts:
                    continue
                
                pts.sort(key=lambda x: x[1])
                scores = [p[0] for p in pts]
                times = [p[1] for p in pts]
                t0 = times[0]
                rel_times = [(t - t0) / 60.0 for t in times]
                
                # Track maximum score for this subplot
                max_score_subplot = max(max_score_subplot, max(scores))
                
                label = f"{sentenceBuilder} / k={top_k} / mask={mask_percentage*100:.0f}%"
                linestyle = "-" if sentenceBuilder == "random" else "--"
                # Define color scheme: 3 color groups for top_k, with 3 shades each for mask_percentage
                color_groups = {
                    10: ['#6BAED6', '#1F77B4', '#08306B'], 
                    25: ['#74C476', '#2CA02C', '#005A32'],  
                    50: ['#FDBB84', '#FF7F0E', '#7F2704']  
                }
                mask_percentages = sorted(set(mp for (_, _, mp), _ in filtered_configs.items()))
                top_k_to_color_map = {}
                for tk in sorted(set(tk for (_, tk, _), _ in filtered_configs.items())):
                    if tk not in color_groups:
                        color_groups[tk] = plt.cm.tab10(len(color_groups) % 10)
                    top_k_to_color_map[tk] = color_groups[tk]

                # Get color based on top_k and mask_percentage
                mask_idx = mask_percentages.index(mask_percentage) if mask_percentage in mask_percentages else 0
                color = top_k_to_color_map[top_k][mask_idx % len(top_k_to_color_map[top_k])]
                ax.plot(rel_times, scores, marker="o", label=label, linestyle=linestyle, color=color)
                
                plotted_this_subplot = True
                any_plotted = True
            
            # Add horizontal line for base seed perplexity
            base_seed_key = (problem_key, current_seed, current_ref)
            if base_seed_key in base_seed:
                base_ppl = base_seed[base_seed_key]
                ax.axhline(y=base_ppl, color='red', linestyle=':', linewidth=2, label=f'Base seed PPL: {base_ppl:.2f}')
            
            if plotted_this_subplot:
                ax.set_xlabel("Time (seconds) from first solution")
                ax.set_ylabel("Perplexity Score")
                
                # Apply y-axis limit only if this subplot's max exceeds 300
                if max_score_subplot > 300:
                    ax.set_ylim(0, 300)
                
                title = f"{'Ref: ' + current_ref + ', ' if current_ref else ''}Seed: {current_seed}"
                ax.set_title(title)
                ax.grid(True)
                ax.legend(loc="best", fontsize=8)
            else:
                ax.axis("off")
        
        # Hide unused subplots
        for j in range(n, len(axes_flat)):
            axes_flat[j].axis("off")
        
        if not any_plotted:
            plt.close(fig)
            continue
        
        fig.suptitle(f"Score evolution for problem: {problem_key} | Architecture: {arch}")
        fig.tight_layout(rect=[0, 0, 1, 0.96])
        
        # Save or show the figure here
        plt.savefig(f"{plots_dir}/problem_{problem_key}_arch_{arch}.png")
        # plt.show()
    