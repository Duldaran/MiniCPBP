import torch
from transformers import AutoTokenizer, AutoModelForCausalLM
import time

print(f"opening")
# Load model and tokenizer
model_name = "meta-llama/Llama3.1-8B"
print(f"Loading {model_name}...")

tokenizer = AutoTokenizer.from_pretrained(model_name)
model = AutoModelForCausalLM.from_pretrained(
    model_name,
    torch_dtype=torch.float16,
    device_map="auto"
)

# Prompt to generate ~15 word sentence
prompt = "Write a short sentence about artificial intelligence:"

# Number of test runs
num_runs = 5

print(f"\nRunning {num_runs} generation tests...\n")

times = []

for i in range(num_runs):
    # Tokenize input
    inputs = tokenizer(prompt, return_tensors="pt").to(model.device)
    
    # Start timing
    start_time = time.time()
    
    # Generate token by token (no batching optimizations)
    outputs = model.generate(
        inputs.input_ids,
        max_new_tokens=20,  # Generate up to 20 tokens to ensure ~15 words
        do_sample=True,     # Greedy decoding for consistency
        temperature=1.0,
        top_p=1.0,
        pad_token_id=tokenizer.eos_token_id
    )
    
    # End timing
    end_time = time.time()
    elapsed = end_time - start_time
    
    # Decode output
    generated_text = tokenizer.decode(outputs[0], skip_special_tokens=True)
    generated_only = generated_text[len(prompt):].strip()
    
    # Count words in generated text
    word_count = len(generated_only.split())
    
    times.append(elapsed)
    
    print(f"Run {i+1}:")
    print(f"  Generated: {generated_only}")
    print(f"  Words: {word_count}")
    print(f"  Time: {elapsed:.3f}s")
    print(f"  Tokens/sec: {(outputs.shape[1] - inputs.input_ids.shape[1]) / elapsed:.2f}")
    print()

# Calculate average
avg_time = sum(times) / len(times)
print(f"Average generation time: {avg_time:.3f}s")
print(f"Min: {min(times):.3f}s, Max: {max(times):.3f}s")