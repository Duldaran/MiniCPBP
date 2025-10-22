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
parser.add_argument('--model', '-M', choices=['zephyr', 'llama'], default='zephyr',
                    help="Select model family to load: 'zephyr' (stabilityai/stablelm-zephyr-3b) or 'llama' (meta-llama/Llama-3.2-3B)")
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
    NEW_WORD=False
    
    while not NEW_WORD:
        # Get the model predictions for the sentence.
        predictions = get_predictions(sentence)
        
        # Get the next token candidates.
        next_token_candidates_tensor = predictions[0, -1, :]
        
        # Get the token probabilities for all candidates.
        all_candidates_probabilities = torch.nn.functional.softmax(
            next_token_candidates_tensor, dim=-1).tolist()
        
        top_token = tokenizer.decode([next_token_candidates_tensor.argmax().item()], skip_special_tokens=False)
        if top_token.startswith(" ") or top_token == ".":
            NEW_WORD = True
        else:
            sentence += top_token

        
    
    # Get the last added word in the sentence (could be multiple tokens)
    # Find the last word by splitting the sentence
    sentence_words = sentence.strip().split()
    # Return a JSON object with probabilities and last word
    return {
        "prob": list(zip(range(0, len(next_token_candidates_tensor)), all_candidates_probabilities)),
        "sentence": sentence,
    }

#java -Xms2g -Xmx16g  -cp minicpbp-1.0.jar minicpbp.examples.MNREAD





try:
    print("Setting model_name...")
    if args.model == 'zephyr':
        model_name = "stabilityai/stablelm-zephyr-3b"
    elif args.model == 'llama':
        model_name = "meta-llama/Llama-3.2-3B"
    else:
        raise ValueError(f"Unknown model choice: {args.model}")

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

    return raw_probs

@app.route('/ping', methods=['GET'])
def ping():
    return 'pong', 200



if __name__ == '__main__':
    print("Starting server...")
    try:
        app.run(host="0.0.0.0", port=args.port)
    except Exception as e:
        exc_type = type(e).__name__
        print(f"Server crashed with exception type: {exc_type}", file=sys.stderr)
        traceback.print_exc(file=sys.stderr)  # Optional: Full stack trace
        sys.exit(1)
