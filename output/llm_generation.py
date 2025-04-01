from transformers import pipeline, AutoModelForCausalLM, AutoTokenizer
import torch
from tqdm import tqdm
import json
from undecorated import undecorated
from types import MethodType
from evaluate import load
perplexity = load("perplexity", module_type="metric")
import math
import gc


def flush():
  gc.collect()
  torch.cuda.empty_cache()
  torch.cuda.reset_peak_memory_stats()

device='cuda' if torch.cuda.is_available() else 'cpu'
model_name = "microsoft/Phi-3.5-mini-instruct"
#model_name ="meta-llama/Llama-3.2-3B"
#model_name ="google/gemma-2-2b"

flush()

model = AutoModelForCausalLM.from_pretrained(model_name, device_map="auto", load_in_8bit=True)
tokenizer = AutoTokenizer.from_pretrained(model_name)

with open('..\src\main\java\minicpbp\examples\data\Sentence\commongen.json', 'r') as f:
#with open('..\src\main\java\minicpbp\examples\data\Sentence\commongen_hard_nohuman.json', 'r') as f:
    data = json.load(f)


treated_data = [(i['instruction'],i['concept_set']) for i in data]




nll_sum = 0.0
n_tokens = 0
results=[]

for problem in tqdm(treated_data):
    sentence = problem[0].replace("\n", " ").replace("\"", "")
    concept_set = problem[1]
    sentence_correction="\n\n# Response:"

    for i in range(len(concept_set)):
        concept_set[i]=concept_set[i][:-2]
    
    inputs = tokenizer(sentence+sentence_correction, return_tensors="pt").to(device)
    with torch.no_grad():
        model_outputs = model.generate(**inputs, max_new_tokens=30, return_dict_in_generate=True, output_scores=True)
        

        generated_tokens_ids = model_outputs.sequences[0]

        sentence_out=tokenizer.decode(generated_tokens_ids).removeprefix(sentence+sentence_correction)

    ppl=0

    # Accumulate the total negative log-likelihood and the total number of tokens
    #ppl = perplexity.compute(predictions=sentence_out, model_id='gpt2', add_start_token =False)['perplexities']
    print(ppl)
    results.append({"sentence":sentence_out, "required_words":concept_set, "perplexity":ppl})
    
with open('llm_output.json', 'w') as outfile:
    json.dump(results, outfile, indent=4)
    
