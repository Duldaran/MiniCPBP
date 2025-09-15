import json
from pathlib import Path
import torch
from transformers import AutoTokenizer, AutoModelForCausalLM, pipeline
import numpy as np
import time
from tqdm import tqdm

device_gpu = "cuda" if torch.cuda.is_available() else "cpu"
device_cpu = "cpu"

start_time = time.time()    

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

folder_path = Path("Septembre_2025/evaluate_folder/")

for file_path in folder_path.glob("*.json"):
    print("Processing:", file_path)
    
    # Example: load JSON content
    with open(file_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    # --- 1. Load sentences from JSON ---
    sentences = [
        entry["sentence"]
        for entry in data["logs"]
        if not entry["sentence"].strip().endswith("ERROR")
    ]



    # --- 4. Run evaluation ---
    results = []
    for s in tqdm(sentences, desc="Evaluating sentences"):
        llm_score = evaluate_fluency_llm(s)
        ppl_score = calculate_perplexity(s)
        results.append({"sentence": s, "LLM_fluency": llm_score, "perplexity": ppl_score})

    # --- 5. Summary stats ---
    llm_scores = [r["LLM_fluency"] for r in results if r["LLM_fluency"] is not None]
    ppl_scores = [r["perplexity"] for r in results]

    summary = {
        "LLM_avg": float(np.mean(llm_scores)) if llm_scores else None,
        "LLM_min": float(np.min(llm_scores)) if llm_scores else None,
        "LLM_max": float(np.max(llm_scores)) if llm_scores else None,
        "PPL_avg": float(np.mean(ppl_scores)) if ppl_scores else None,
        "PPL_min": float(np.min(ppl_scores)) if ppl_scores else None,
        "PPL_max": float(np.max(ppl_scores)) if ppl_scores else None
    }

    # --- 6. Save results ---
    # Find best sentences based on scores
    best_llm = max(results, key=lambda r: r["LLM_fluency"] if r["LLM_fluency"] is not None else float('-inf'))
    best_ppl = min(results, key=lambda r: r["perplexity"])

    output_data = {
        "results": results,
        "summary": summary,
        "best_LLM_fluency": best_llm,
        "best_perplexity": best_ppl
    }

    with open(f"{folder_path}/evaluation_results_{file_path.stem}.json", "w", encoding="utf-8") as f:
        json.dump(output_data, f, indent=2)

    print("Evaluation done. Summary:")
    print(summary)
    end_time = time.time()
    print(f"Total evaluation time: {end_time - start_time:.2f} seconds")
