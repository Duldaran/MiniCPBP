from transformers import pipeline, AutoModelForCausalLM, AutoTokenizer
import torch
import json

def get_predictions(sentence):
    # Encode the sentence using the tokenizer and return the model predictions.
    inputs = tokenizer.encode(sentence, return_tensors="pt").to(device)
    with torch.no_grad():
        outputs = model(inputs)
        predictions = outputs[0]
    return predictions

model_name = "mistralai/Mistral-7B-v0.3"
#model_name = "gpt2-xl"
device='cuda' if torch.cuda.is_available() else 'cpu'
model = AutoModelForCausalLM.from_pretrained(model_name).to(device)
tokenizer = AutoTokenizer.from_pretrained(model_name)


# Get the model predictions for the sentence.
predictions = get_predictions("<s>Hello")

# Get the next token candidates.
next_token_candidates_tensor = predictions[0, -1, :]

print(len(next_token_candidates_tensor))

# Get the top k next token candidates.
topk_candidates_indexes = range(0, len(next_token_candidates_tensor)+1)

# Decode the top k candidates back to words.
tokens = \
    [tokenizer.decode([idx], skip_special_tokens=False) for idx in topk_candidates_indexes]#Remove strip when possible

# Return the top k candidates and their probabilities.

with open('tokenizer_dict', 'w', encoding="UTF-8") as tokens_dict:
    for ind,token in enumerate(tokens):
        try:
            tokens_dict.write(str(ind)+"::"+token+"\n")
        except:
            print("Error")
            pass
    


def get_next_word_probabilities(sentence, top_k=50000):

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
        [tokenizer.decode([idx], skip_special_tokens=False).strip() for idx in topk_candidates_indexes]#Remove strip when possible

    # Return the top k candidates and their probabilities.
    return list(zip(topk_candidates_tokens, topk_candidates_probabilities))
    
def altgetpred(sentence):
    inputs = tokenizer(sentence, return_tensors="pt").to(device)

    model_outputs = model.generate(**inputs, max_new_tokens=15, return_dict_in_generate=True, output_scores=True)

    generated_tokens_ids = model_outputs.sequences[0]

    print(tokenizer.decode(generated_tokens_ids).removeprefix(sentence))
    return tokenizer.decode(generated_tokens_ids).removeprefix(sentence)


def testing():

    probabilities = get_next_word_probabilities("<s>Hello",top_k=5)
    return probabilities



def next_token():
    #altgetpred(request.data.decode())
    raw_probs = get_next_word_probabilities(request.data.decode())
    #raw_probs = [('banane', 0.1),('banane', 0.1),('banane', 0.1),('banane', 0.1),('banane', 0.1),('banane', 0.1),('banane', 0.1),('banane', 0.1),('banane', 0.1),('banane', 0.1)]
    
    ##for prob in raw_probs:
    ##    print("Candidat : "+prob[0])
    ##    print("Probabilitées : "+str(prob[1]))

    return raw_probs

