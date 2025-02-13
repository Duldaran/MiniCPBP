from flask import Flask, request
from transformers import pipeline, AutoModelForCausalLM, AutoTokenizer
import torch


app = Flask(__name__)

#model_name = "gpt2"
model_name = "gpt2-xl"
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
    
def altgetpred(sentence):
    inputs = tokenizer(sentence, return_tensors="pt").to(device)

    model_outputs = model.generate(**inputs, max_new_tokens=15, return_dict_in_generate=True, output_scores=True)

    generated_tokens_ids = model_outputs.sequences[0]

    print(tokenizer.decode(generated_tokens_ids).removeprefix(sentence))
    return tokenizer.decode(generated_tokens_ids).removeprefix(sentence)

@app.route('/altpred', methods=['POST'])
def altpred():
    return altgetpred(request.data.decode())

@app.route('/tokenize', methods=['POST'])
def get_tokens():
    return tokenizer.convert_tokens_to_ids(tokenizer.tokenize(request.data.decode()))

@app.route('/')
def testing():

    probabilities = get_next_word_probabilities("<s>Hello",top_k=5)
    return probabilities

@app.route('/ngrams', methods=['POST'])
def ngrams():
    pass

@app.route('/token', methods=['POST'])
def next_token():
    #altgetpred(request.data.decode())
    raw_probs = get_next_word_probabilities(request.data.decode())
    #raw_probs = [('banane', 0.1),('banane', 0.1),('banane', 0.1),('banane', 0.1),('banane', 0.1),('banane', 0.1),('banane', 0.1),('banane', 0.1),('banane', 0.1),('banane', 0.1)]
    
    ##for prob in raw_probs:
    ##    print("Candidat : "+prob[0])
    ##    print("Probabilitées : "+str(prob[1]))

    return raw_probs

if __name__ == '__main__':  
    app.run()
