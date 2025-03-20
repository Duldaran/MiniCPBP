import json
import numpy as np
from transformers import pipeline, AutoModelForCausalLM, AutoTokenizer
import math
import torch
import os
from rouge_score import rouge_scorer
from nltk.translate.bleu_score import sentence_bleu
from nltk.translate.meteor_score import meteor_score
from nltk.stem import WordNetLemmatizer


def score(sentence, tokenizer, model):
    tokenize_input = tokenizer.tokenize(default_instruction+sentence)
    tensor_input = torch.tensor([tokenizer.convert_tokens_to_ids(tokenize_input)])
    out=model(tensor_input, labels=tensor_input)
    return math.exp(out.loss.item())

        
def verify_required_words(results):
    
    verified_results = []
    lemmatizer = WordNetLemmatizer()

    for result in results:
        sentence = result['sentence']
        required_words = result['required_words']
        sentence_tokens = sentence.split()

        # Generate lemmas for different parts of speech
        sentence_lemmas = {
            "n": [lemmatizer.lemmatize(token.lower(), pos="n") for token in sentence_tokens],
            "v": [lemmatizer.lemmatize(token.lower(), pos="v") for token in sentence_tokens],
            "a": [lemmatizer.lemmatize(token.lower(), pos="a") for token in sentence_tokens],
            "r": [lemmatizer.lemmatize(token.lower(), pos="r") for token in sentence_tokens],
            "s": [lemmatizer.lemmatize(token.lower(), pos="s") for token in sentence_tokens],
        }

        # Check if each required word appears in at least one lemma set
        if all(
            any(lemmatizer.lemmatize(word.strip().lower(), pos=pos) in sentence_lemmas[pos] for pos in sentence_lemmas)
            for word in required_words
        ):
            verified_results.append(result)
        else:
            print(f"Required words {required_words} not in sentence {sentence}")
    
    return verified_results



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

def calculate_metrics(reference_sentences, generated_sentences):
    metrics = {
        'rouge': [],
        'bleu': [],
        'cider': [],
        'spice': []
    }


    # Calculate METEOR
    meteor_scores = []
    for refs, gen in zip(reference_sentences, generated_sentences):
        meteor_scores.append(meteor_score([ref.split(' ') for ref in refs], gen.split(' ')))
    metrics['meteor'] = meteor_scores

    for refs, gen in zip(reference_sentences, generated_sentences):
        # Calculate ROUGE
        scorer = rouge_scorer.RougeScorer(['rouge1', 'rouge2', 'rougeL'], use_stemmer=True)
        rouge_scores = scorer.score(refs, gen)
        metrics['rouge'].append({key: value.fmeasure for key, value in rouge_scores.items()})

        # Calculate BLEU
        bleu_score = sentence_bleu([ref.split(' ') for ref in refs], gen.split(' '))
        metrics['bleu'].append(bleu_score)


    metrics['cider'] = cider_score
    metrics['spice'] = spice_score

    return metrics



#default_instruction = "# Instruction\n\nGiven several concepts (i.e., nouns or verbs), write a short and simple sentence that contains *all* the required words.\nThe sentence should describe a common scene in daily life, and the concepts should be used in a natural way.\n\n# Examples\n\n## Example 1\n- Concepts: \"dog(noun), frisbee(noun), catch(verb), throw(verb)\"\n- Sentence: The dog catches the frisbee when the boy throws it into the air.\n\n## Example 2\n- Concepts: \"apple(noun), place(verb), tree(noun), pick(verb)\"\n- Sentence: A girl picks some apples from a tree and places them into her basket.\n\n# Your Task \n\n- Concepts: \"catch(verb), dog(noun), frisbee(noun), throw(verb)\"\n- Sentence: # Your Results   - Sentence:"
default_instruction=""
# Dynamically load JSON files from the output directory
output_dir = "output"
json_files = [f for f in os.listdir(output_dir) if f.endswith('.json')]

# Separate model and LLM output files
model_files = [f for f in json_files if f.startswith('model_results')]
llm_files = [f for f in json_files if f.startswith('llm_output')]

# Evaluate every pair of model and LLM output files
for model_file in model_files:
    for llm_file in llm_files:
        print(f"Evaluating pair: Model File = {model_file}, LLM File = {llm_file}")
        # Determine the model name based on the file names
        if "gpt" in model_file.lower():
            model_name = "gpt2-xl"
        elif "llama" in model_file.lower():
            model_name = "meta-llama/Llama-3.2-3B"
        else:
            print(f"Skipping pair: Model File = {model_file}, LLM File = {llm_file} (Unknown model type)")
            continue


        llm_name_model = next((name for name in ["gpt", "llama"] if name in model_file.lower()), None)
        llm_name_llm = next((name for name in ["gpt", "llama"] if name in llm_file.lower()), None)

        if llm_name_model != llm_name_llm:
            print(f"Skipping pair: Model File = {model_file}, LLM File = {llm_file} (LLM names do not match)")
            continue

        # Check if one file is on a hard dataset and the other is not
        is_hard_model = "hard" in model_file.lower()
        is_hard_llm = "hard" in llm_file.lower()

        if is_hard_model != is_hard_llm:
            print(f"Skipping pair: Model File = {model_file}, LLM File = {llm_file} (Dataset difficulty does not match)")
            continue
        
        
        old_commongen = "old" in model_file.lower()


        
        # Load LLM results
        with open(os.path.join(output_dir, llm_file), 'r') as llm_file_obj:
            llm_results = json.load(llm_file_obj)

        # Load model results
        with open(os.path.join(output_dir, model_file), 'r') as model_file_obj:
            model_results = json.load(model_file_obj)
            

        model = AutoModelForCausalLM.from_pretrained(model_name)
        tokenizer = AutoTokenizer.from_pretrained(model_name)



        verified_llm_results = verify_required_words(llm_results)

        verified_model_results = verify_required_words(model_results)

        # Calculate statistics
        llm_stats = calculate_statistics(llm_results)
        model_stats = calculate_statistics(model_results)

        # Save stats for the current pair
        # Ensure model and LLM files match based on their naming convention
        if model_file.split('_')[2] != llm_file.split('_')[2]:
            continue

        stats = {}
        if not old_commongen:
            stats['llm_stats'] = llm_stats
        stats['model_stats'] = model_stats
        stats['llm_results'] = len(llm_results)
        stats['model_results'] = len(model_results)
        stats['verified_llm_results'] = len(verified_llm_results)
        stats['verified_model_results'] = len(verified_model_results)

        if old_commongen:
            # Load reference sentences from old_commongen.json
            with open('old_commongen.json', 'r') as ref_file:
                reference_data = json.load(ref_file)
            reference_sentences = [item['sentence'] for item in reference_data]

            # Extract generated sentences
            generated_sentences = [result['sentence'] for result in model_results]

            # Calculate metrics
            metrics = calculate_metrics(reference_sentences[1:len(generated_sentences) + 1], generated_sentences)

            # Add metrics to stats
            stats['metrics'] = metrics

        stats_filename = f"stats_{model_file.split('.')[0]}_{llm_file.split('.')[0]}.json"
        with open(os.path.join(output_dir, stats_filename), 'w') as stats_file:
            json.dump(stats, stats_file)

        print(f"Stats saved to {stats_filename}")
        