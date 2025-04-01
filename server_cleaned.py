from flask import Flask, request
from transformers import pipeline, AutoModelForCausalLM, AutoTokenizer
import torch
from nltk.stem import WordNetLemmatizer
import nltk
nltk.download('wordnet')
import time
import gc


app = Flask(__name__)

gc.collect()
torch.cuda.empty_cache()
torch.cuda.reset_peak_memory_stats()

model_name = "meta-llama/Llama-3.2-3B"
#model_name = "gpt2-xl"
device='cuda' if torch.cuda.is_available() else 'cpu'
model = AutoModelForCausalLM.from_pretrained(model_name, device_map="auto")
tokenizer = AutoTokenizer.from_pretrained(model_name)
    
def get_predictions(sentence):
    # Encode the sentence using the tokenizer and return the model predictions.
    inputs = tokenizer.encode(sentence, return_tensors="pt").to(device)
    with torch.no_grad():
        outputs = model(inputs)
        predictions = outputs[0]
    return predictions

def get_next_word_probabilities(sentence):

    # Get the model predictions for the sentence.
    predictions = get_predictions(sentence)
    
    # Get the next token candidates.
    next_token_candidates_tensor = predictions[0, -1, :]
    
    # Get the token probabilities for all candidates.
    all_candidates_probabilities = torch.nn.functional.softmax(
        next_token_candidates_tensor, dim=-1).tolist()
    

    # Return the top k candidates and their probabilities.
    return list(zip(range(0,len(next_token_candidates_tensor)), all_candidates_probabilities))

#Works well for noun but not for others (*Would need to know grammatical class of the word to get the right lemmatization)

next_token_candidates_tensor = get_predictions("<s>Hello")[0, -1, :]
print(len(next_token_candidates_tensor))
print(time.time())
all_tokens = [tokenizer.decode([idx], skip_special_tokens=False) for idx in range(0, len(next_token_candidates_tensor)+1)]
print(time.time())
all_lemmes_nouns = [WordNetLemmatizer().lemmatize(token.strip().lower()) for token in all_tokens]
print(time.time())
all_lemmes_verbs = [WordNetLemmatizer().lemmatize(token.strip().lower(),"v") for token in all_tokens]
print(time.time())
all_lemmes_adjectives = [WordNetLemmatizer().lemmatize(token.strip().lower(),"a") for token in all_tokens]
print(time.time())
all_lemmes_adverbs = [WordNetLemmatizer().lemmatize(token.strip().lower(),"r") for token in all_tokens]
print(time.time())
all_lemmes_satellites = [WordNetLemmatizer().lemmatize(token.strip().lower(),"s") for token in all_tokens]
print(time.time())

'''
with open('lemme_dict', 'w', encoding="UTF-8") as tokens_dict:
    for ind,token in enumerate(all_lemmes):
        try:
            tokens_dict.write(str(ind)+"::"+token+"\n")
        except:
            print("Error")
            pass
'''

@app.route('/tokenize', methods=['POST'])
def get_tokens():
    tokens = tokenizer.convert_tokens_to_ids(tokenizer.tokenize(request.data.decode()))
    if len(tokens) > 1:return [-1]+tokens
    elif len(tokens) == 1: 
        similar_tokens=set()
        lemme_token= WordNetLemmatizer().lemmatize(tokenizer.decode(tokens).strip().lower())
        for index,lemme in enumerate(all_lemmes_nouns):
            if lemme == lemme_token:
                similar_tokens.add(index)
        lemme_token= WordNetLemmatizer().lemmatize(tokenizer.decode(tokens).strip().lower(),"v")
        for index,lemme in enumerate(all_lemmes_verbs):
            if lemme == lemme_token :
                similar_tokens.add(index)
        lemme_token= WordNetLemmatizer().lemmatize(tokenizer.decode(tokens).strip().lower(),"a")
        for index,lemme in enumerate(all_lemmes_adjectives):
            if lemme == lemme_token:
                similar_tokens.add(index)
        lemme_token= WordNetLemmatizer().lemmatize(tokenizer.decode(tokens).strip().lower(),"r")
        for index,lemme in enumerate(all_lemmes_adverbs):
            if lemme == lemme_token:
                similar_tokens.add(index)
        lemme_token= WordNetLemmatizer().lemmatize(tokenizer.decode(tokens).strip().lower(),"s")
        for index,lemme in enumerate(all_lemmes_satellites):
            if lemme == lemme_token:
                similar_tokens.add(index)
        return [-2]+list(similar_tokens)
    else: return [-3]

@app.route('/')
def testing():

    probabilities = get_next_word_probabilities("<s>Hello")
    return probabilities


@app.route('/token', methods=['POST'])
def next_token():
    raw_probs = get_next_word_probabilities(request.data.decode())
    
    return raw_probs

if __name__ == '__main__':  
    app.run()
