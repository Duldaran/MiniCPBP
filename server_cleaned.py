import os

from sympy import im
print("Importing server...")
os.environ['PYTHONVERBOSE'] = '1'
try:
    print("Importing sys...")
    import sys
    print("Configuring stdout...")
    sys.stdout.reconfigure(line_buffering=True)
    print("Configuring stderr...")
    sys.stderr.reconfigure(line_buffering=True)
    
    print("Importing time...")
    import time
    print("Importing json...")
    import json
    print("Importing traceback...")
    import traceback
    print("Importing Flask and request...")
    from flask import Flask, request
    print("Importing torch...")
    start_time = time.time()
    import torch
    print("Done importing torch in", time.time() - start_time, "seconds")
    print("Importing AutoModelForCausalLM, AutoTokenizer...")
    os.environ['TRANSFORMERS_OFFLINE'] = '1'  # Skip online model checks
    os.environ['HF_HUB_DISABLE_TELEMETRY'] = '1'  # Disable telemetry
    start_time = time.time()
    from transformers import  AutoModelForCausalLM, AutoModelForMaskedLM, AutoTokenizer
    print("Done importing transformers in", time.time() - start_time, "seconds")
    print("Importing WordNetLemmatizer...")
    from nltk.stem import WordNetLemmatizer
    print("Importing wordnet corpus...")
    from nltk.corpus import wordnet
    print("Importing gc...")
    import gc
    print("Importing argparse...")
    import argparse
    print("Imports done.")
except Exception as e:
    print("Import error:", file=sys.stderr)
    traceback.print_exc(file=sys.stderr)

parser = argparse.ArgumentParser(description="Flask server for token prediction")
parser.add_argument('--port', type=int, default=5000, help='Port to run the server on')
args = parser.parse_args()
app = Flask(__name__)

gc.collect()

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

#java -Xms2g -Xmx16g  -cp minicpbp-1.0.jar minicpbp.examples.MNREAD


def get_mask_distributions(sentence):
    inputs = mlm_tokenizer(sentence, return_tensors="pt").to(device)
    with torch.no_grad():
        outputs = mlm_model(**inputs)
        logits = outputs.logits

    mask_positions = (inputs.input_ids == mlm_tokenizer.mask_token_id)[0].nonzero(as_tuple=True)[0]

    distributions = {}
    for pos in mask_positions:
        probs = torch.softmax(logits[0, pos], dim=-1).cpu().tolist()
        distributions[int(pos)] = {
            "mask_index": int(pos),
            "tokens": [mlm_tokenizer.decode([i]) for i in range(len(probs))],
            "probs": probs
        }
    return distributions


try:
    print("Setting model_name...")
    #model_name = "meta-llama/Llama-3.2-3B"
    #model_name = "../Ctrl-G/ctrlg/gpt2-large_common-gen"
    model_name ="stabilityai/stablelm-zephyr-3b"

    print("Detecting device...")
    device='cuda' if torch.cuda.is_available() else 'cpu'
    if device == 'cuda':
        print("Using GPU")
        torch.cuda.set_device(0)
    else:
        print("Using CPU")

    #print("Loading model...")
    #model = AutoModelForCausalLM.from_pretrained(model_name, device_map="auto")
    print("Loading model with local_files_only=True...")
    model = AutoModelForCausalLM.from_pretrained(model_name, device_map="auto", local_files_only=True)

    print("Loading MLM model...")
    mlm_model_name = "distilbert-base-uncased"
    mlm_model = AutoModelForMaskedLM.from_pretrained(mlm_model_name).to(device)
    mlm_tokenizer = AutoTokenizer.from_pretrained(mlm_model_name)
    print("MLM model ready")
    
    print("Loading tokenizer with local_files_only=True...")
    tokenizer = AutoTokenizer.from_pretrained(model_name, local_files_only=True)

    print("Getting next_token_candidates_tensor...")
    next_token_candidates_tensor = get_predictions("<s>Hello")[0, -1, :]

    print("Printing length of next_token_candidates_tensor...")
    print(len(next_token_candidates_tensor))

    print("Printing current time...")
    print(time.time())

    print("Generating all_tokens...")
    all_tokens = [tokenizer.decode([idx], skip_special_tokens=False) for idx in range(0, len(next_token_candidates_tensor)+1)]

    print("Printing current time...")
    print(time.time())

    print("Generating all_lemmes_nouns...")
    all_lemmes_nouns = [WordNetLemmatizer().lemmatize(token.strip().lower()) for token in all_tokens]

    print("Printing current time...")
    print(time.time())

    print("Generating all_lemmes_verbs...")
    all_lemmes_verbs = [WordNetLemmatizer().lemmatize(token.strip().lower(),"v") for token in all_tokens]

    print("Printing current time...")
    print(time.time())

    print("Generating all_lemmes_adjectives...")
    all_lemmes_adjectives = [WordNetLemmatizer().lemmatize(token.strip().lower(),"a") for token in all_tokens]

    print("Printing current time...")
    print(time.time())

    print("Generating all_lemmes_adverbs...")
    all_lemmes_adverbs = [WordNetLemmatizer().lemmatize(token.strip().lower(),"r") for token in all_tokens]

    print("Printing current time...")
    print(time.time())

    print("Generating all_lemmes_satellites...")
    all_lemmes_satellites = [WordNetLemmatizer().lemmatize(token.strip().lower(),"s") for token in all_tokens]

    print("Printing current time...")
    print(time.time())

    print("Ready")
except Exception as e:
    print("Error during model/tokenizer/lemmatizer setup:"+str(e), file=sys.stderr)
    traceback.print_exc(file=sys.stderr)
    sys.exit(1)



@app.route('/tokenize', methods=['POST'])
def get_tokens():
    tokens = tokenizer.convert_tokens_to_ids(tokenizer.tokenize(request.data.decode()[1:]))
    soft_constaint_flage=request.data.decode()[0]
    if len(tokens) > 1:return [-1]+tokens
    elif len(tokens) == 1: 
        if soft_constaint_flage == '1':
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
        else:
            return [-3] + tokens
    else: return [-4]

@app.route('/')
def testing():

    probabilities = get_next_word_probabilities("<s>Hello")
    return probabilities


@app.route('/token', methods=['POST'])
def next_token():
    raw_probs = get_next_word_probabilities(request.data.decode())

    return json.dumps(raw_probs)

@app.route('/ping', methods=['GET'])
def ping():
    return 'pong', 200

@app.route('/mlm', methods=['POST'])
def mlm_predict():
    try:
        sentence = request.data.decode()
        if "[MASK]" not in sentence:
            return {"error": "Sentence must contain [MASK] token"}, 400

        distributions = get_mask_distributions(sentence)
        return distributions, 200
    except Exception as e:
        traceback.print_exc()
        return {"error": str(e)}, 500

if __name__ == '__main__':
    print("Starting server...")
    try:
        app.run(host="0.0.0.0", port=args.port)
    except Exception as e:
        exc_type = type(e).__name__
        print(f"Server crashed with exception type: {exc_type}", file=sys.stderr)
        traceback.print_exc(file=sys.stderr)  # Optional: Full stack trace
        sys.exit(1)
