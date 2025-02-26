from flask import Flask, request
from transformers import pipeline, AutoModelForCausalLM, AutoTokenizer
import torch


app = Flask(__name__)

model_name = "gpt2-xl"
#model_name = "gpt2-large"
device='cuda' if torch.cuda.is_available() else 'cpu'
model = AutoModelForCausalLM.from_pretrained(model_name).to(device)
tokenizer = AutoTokenizer.from_pretrained(model_name)
    
def get_predictions(sentence):
    # Encode the sentence using the tokenizer and return the model predictions.
    inputs = tokenizer.encode(sentence, return_tensors="pt").to(device)
    with torch.no_grad():
        outputs = model(inputs)
        predictions = outputs[0]
    return predictions

def get_next_word_probabilities(sentence, top_k=50000):

    # Get the model predictions for the sentence.
    predictions = get_predictions(sentence)
    
    # Get the next token candidates.
    next_token_candidates_tensor = predictions[0, -1, :]
    
    # Get the token probabilities for all candidates.
    all_candidates_probabilities = torch.nn.functional.softmax(
        next_token_candidates_tensor, dim=-1).tolist()
    

    # Return the top k candidates and their probabilities.
    return list(zip(range(0,len(next_token_candidates_tensor)), all_candidates_probabilities))
    

@app.route('/tokenize', methods=['POST'])
def get_tokens():
    return tokenizer.convert_tokens_to_ids(tokenizer.tokenize(request.data.decode()))

@app.route('/')
def testing():

    probabilities = get_next_word_probabilities("<s>Hello",top_k=5)
    return probabilities


@app.route('/token', methods=['POST'])
def next_token():
    raw_probs = get_next_word_probabilities(request.data.decode())
    
    return raw_probs

if __name__ == '__main__':  
    app.run()
