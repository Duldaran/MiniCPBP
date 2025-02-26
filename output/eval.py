import json
import numpy as np
from transformers import pipeline, AutoModelForCausalLM, AutoTokenizer
import math
import torch
import os

model_name = "gpt2-large"

eval=False
results_name='model_results_gpt_large.json'

model = AutoModelForCausalLM.from_pretrained(model_name)
tokenizer = AutoTokenizer.from_pretrained(model_name)

#default_instruction = "# Instruction\n\nGiven several concepts (i.e., nouns or verbs), write a short and simple sentence that contains *all* the required words.\nThe sentence should describe a common scene in daily life, and the concepts should be used in a natural way.\n\n# Examples\n\n## Example 1\n- Concepts: \"dog(noun), frisbee(noun), catch(verb), throw(verb)\"\n- Sentence: The dog catches the frisbee when the boy throws it into the air.\n\n## Example 2\n- Concepts: \"apple(noun), place(verb), tree(noun), pick(verb)\"\n- Sentence: A girl picks some apples from a tree and places them into her basket.\n\n# Your Task \n\n- Concepts: \"catch(verb), dog(noun), frisbee(noun), throw(verb)\"\n- Sentence: # Your Results   - Sentence:"
default_instruction=""

def score(sentence, tokenizer, model):
    tokenize_input = tokenizer.tokenize(default_instruction+sentence)
    tensor_input = torch.tensor([tokenizer.convert_tokens_to_ids(tokenize_input)])
    out=model(tensor_input, labels=tensor_input)
    print(math.exp(out.loss.item()))
    return math.exp(out.loss.item())

# Load LLM results from JSON file
with open('llm_output_gpt_large.json', 'r') as llm_file:
    llm_results = json.load(llm_file)

# Load model results from JSON file
with open(results_name, 'r') as model_file:
    model_results = json.load(model_file)


unmatched_llm_results = []
matched_results = []
llm_results = llm_results[:len(model_results)]
for llm_result in llm_results:
    matched=False
    for model_result in model_results:
        if llm_result['required_words'] == model_result['required_words']:
            matched_results.append((llm_result, model_result))
            matched=True

    if matched==False:
        unmatched_llm_results.append(llm_result)
        
def verify_required_words(results, print=False):
    verified_results = []
    for result in results:
        sentence = result['sentence']
        required_words = result['required_words']
        if all(word.strip().upper() in sentence.upper() for word in required_words):
            verified_results.append(result)
        elif print:
            print(f"Required words {required_words} not in sentence {sentence}")
    return verified_results

verified_llm_results = verify_required_words(llm_results)
verified_model_results = verify_required_words(model_results, True)


def calculate_statistics(results):
    perplexities = [result for result in results if result['perplexity'] != 'Infinity']
    count_infinity = sum(1 for result in results if result['perplexity'] == 'Infinity')
    
    lengths = [len(result['sentence'].split(' ')) for result in results]
    average_length = sum(lengths)/len(lengths)
    
    short_sentences=[]
    
    for result in results:
        if len(result['sentence'].split(' ')) < 15:
            short_sentences.append(result['sentence'])
    
    perplexities = [score(result['sentence'],tokenizer,model) for result in results]
    print(perplexities)

    if perplexities:
        quartiles = np.percentile(perplexities, [0,25, 50, 75,100]).tolist()
        average = np.mean(perplexities)
    else:
        quartiles = [None,None, None, None, None]
        average = None

    return {
        'quartiles': quartiles,
        'average': average,
        'count_infinity': count_infinity,
        'average_length': average_length,
        'short_sentences': short_sentences
    }

def compare_sets(instruction_set, result_set):
    matched=True
    for instruction in instruction_set:
        if instruction[:-2] not in result_set:
            matched=False
            break
    return matched


llm_stats = calculate_statistics(llm_results)
model_stats = calculate_statistics(model_results)

if unmatched_llm_results and eval:
    with open('../src/main/java/minicpbp/examples/data/Sentence/commongen_hard_nohuman.json', "r") as f:
        data = json.load(f)
    unmatched_instructions = []
    for instruction in data:
        for concepts in unmatched_llm_results:
            if compare_sets(instruction['concept_set'],concepts['required_words']):
                unmatched_instructions.append(instruction)
                unmatched_llm_results.remove(concepts)
                break
    
    with open('unmatched_instructions.json', 'w') as unmatched_llm_file:
        json.dump(unmatched_instructions, unmatched_llm_file)

else:
    stats={}
    stats['llm_stats']=llm_stats
    stats['model_stats']=model_stats
    stats['unmatched_llm_results']=len(unmatched_llm_results)
    stats['matched_results']=len(matched_results)
    stats['verified_llm_results']=len(verified_llm_results)
    stats['verified_model_results']=len(verified_model_results)
    with open('stats_'+results_name, 'w') as stats_file:
        json.dump(stats, stats_file)

