from transformers import pipeline, AutoModelForCausalLM, AutoTokenizer
import torch
from tqdm import tqdm
import json
from undecorated import undecorated
from types import MethodType
from evaluate import load
perplexity = load("perplexity", module_type="metric")
import math

device='cuda'
model_name = "gpt2-xl"

model = AutoModelForCausalLM.from_pretrained(model_name)
tokenizer = AutoTokenizer.from_pretrained(model_name)

with open('..\src\main\java\minicpbp\examples\data\Sentence\commongen_hard_nohuman.json', 'r') as f:
    data = json.load(f)

treated_data = [(i['instruction'],i['concept_set']) for i in data]




nll_sum = 0.0
n_tokens = 0
results=[]

for problem in tqdm(treated_data):
    sentence = problem[0].replace("\n", " ").replace("\"", "")
    concept_set = problem[1]

    for i in range(len(concept_set)):
        concept_set[i]=concept_set[i][:-2]
    
    inputs = tokenizer(sentence+"# Your Results   - Sentence:", return_tensors="pt")
    with torch.no_grad():
        model_outputs = model.generate(**inputs, max_new_tokens=30, return_dict_in_generate=True, output_scores=True)
        

        generated_tokens_ids = model_outputs.sequences[0]

        sentence_out=tokenizer.decode(generated_tokens_ids).removeprefix(sentence)

    ppl=0

    # Accumulate the total negative log-likelihood and the total number of tokens
    #ppl = perplexity.compute(predictions=sentence_out, model_id='gpt2', add_start_token =False)['perplexities']
    print(ppl)
    results.append({"sentence":sentence_out, "required_words":concept_set, "perplexity":ppl})
    
with open('llm_output.json', 'w') as outfile:
    json.dump(results, outfile, indent=4)
    
