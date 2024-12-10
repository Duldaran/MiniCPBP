from flask import Flask, request
from transformers import pipeline, AutoModelForCausalLM, AutoTokenizer
import torch


app = Flask(__name__)

model_name = "gpt2"

model = AutoModelForCausalLM.from_pretrained(model_name)
tokenizer = AutoTokenizer.from_pretrained(model_name)
    
def get_predictions(sentence):
    # Encode the sentence using the tokenizer and return the model predictions.
    inputs = tokenizer.encode(sentence, return_tensors="pt")
    with torch.no_grad():
        outputs = model(inputs)
        predictions = outputs[0]
    return predictions

def get_next_word_probabilities(sentence, top_k=10000):

    # Get the model predictions for the sentence.
    predictions = get_predictions(sentence)
    

    # Get the next token candidates.
    next_token_candidates_tensor = predictions[0, -1, :]
    
    # Get the top k next token candidates.
    topk_candidates_indexes = torch.topk(
        next_token_candidates_tensor, top_k).indices.tolist()

    
    # Get the token probabilities for all candidates.
    all_candidates_probabilities = torch.nn.functional.softmax(
        next_token_candidates_tensor, dim=-1)
    
    # Filter the token probabilities for the top k candidates.
    topk_candidates_probabilities = \
        all_candidates_probabilities[topk_candidates_indexes].tolist()

    # Decode the top k candidates back to words.
    topk_candidates_tokens = \
        [tokenizer.decode([idx]).strip() for idx in topk_candidates_indexes]

    # Return the top k candidates and their probabilities.
    return list(zip(topk_candidates_tokens, topk_candidates_probabilities))

@app.route('/')
def testing():

    probabilities = get_next_word_probabilities("<s>I am late",top_k=512)
    return probabilities

@app.route('/ngrams', methods=['POST'])
def ngrams():
    pass

@app.route('/token', methods=['POST'])
def next_token():
    print("##################### New Request #####################")
    raw_probs = get_next_word_probabilities(f"{request.data}")

    #for prob in raw_probs:
       # print("Candidat : "+prob[0])
        #print("Probabilitées : "+str(prob[1]))

    return raw_probs

if __name__ == '__main__':  
   app.run()

