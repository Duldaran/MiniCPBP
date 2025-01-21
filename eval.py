import json
import numpy as np
from transformers import pipeline, AutoModelForCausalLM, AutoTokenizer
import math
import torch

model_name = "gpt2"

model = AutoModelForCausalLM.from_pretrained(model_name)
tokenizer = AutoTokenizer.from_pretrained(model_name)

def score(sentence, tokenizer, model):
    tokenize_input = tokenizer.tokenize(sentence)
    tensor_input = torch.tensor([tokenizer.convert_tokens_to_ids(tokenize_input)])
    out=model(tensor_input, labels=tensor_input)
    return math.exp(out.loss.item())

# Load LLM results from JSON file
with open('llm_output.json', 'r') as llm_file:
    llm_results = json.load(llm_file)

# Load model results from JSON file
with open('model_results.json', 'r') as model_file:
    model_results = json.load(model_file)


unmatched_llm_results = []
matched_results = []
for llm_result in llm_results:
    matched=False
    for model_result in model_results:
        if llm_result['required_words'] == model_result['required_words']:
            matched_results.append((llm_result, model_result))
            matched=True

    if matched==False:
        unmatched_llm_results.append(llm_result)
        
def verify_required_words(results):
    verified_results = []
    for result in results:
        sentence = result['sentence']
        required_words = result['required_words']
        if all(word in sentence for word in required_words):
            verified_results.append(result)
    return verified_results

verified_llm_results = verify_required_words(llm_results)
verified_model_results = verify_required_words(model_results)


def calculate_statistics(results):
    perplexities = [result for result in results if result['perplexity'] != 'Infinity']
    count_infinity = sum(1 for result in results if result['perplexity'] == 'Infinity')
    
    perplexities = [score(result['sentence'],tokenizer,model) for result in results]
    
    if perplexities:
        quartiles = np.percentile(perplexities, [0,25, 50, 75,100])
        average = np.mean(perplexities)
    else:
        quartiles = [None,None, None, None, None]
        average = None

    return {
        'quartiles': quartiles,
        'average': average,
        'count_infinity': count_infinity
    }

llm_stats = calculate_statistics(llm_results)
model_stats = calculate_statistics(model_results)

print("LLM Results Statistics:", llm_stats)
print("Model Results Statistics:", model_stats)
print("Unmatched LLM Results:", unmatched_llm_results)
print("Number of unmatched LLM Results:", len(unmatched_llm_results))
print("Number of matched LLM Results:", len(matched_results))
print("Number of verified LLM Results:", len(verified_llm_results))
print("Number of verified Model Results:", len(verified_model_results))
