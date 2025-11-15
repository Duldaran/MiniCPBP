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

mask_string = "[MASK]" #"<mask>"




#java -Xms2g -Xmx16g  -cp minicpbp-1.0.jar minicpbp.examples.MNREAD


def get_mask_distributions(sentence):
    inputs = mlm_tokenizer(sentence, return_tensors="pt").to(device)
    with torch.no_grad():
        outputs = mlm_model(**inputs)
        logits = outputs.logits

    mask_token_id = mlm_tokenizer.mask_token_id
    mask_positions = (inputs.input_ids == mask_token_id).nonzero(as_tuple=True)[1].tolist()
    mask_positions.sort()

    if sentence.startswith("<s>"):
        sentence = sentence[len("<s>"):].lstrip()
    if sentence.endswith("."):
        sentence = sentence[:-1].rstrip()
    mask_word_positions = [i for i, x in enumerate(sentence.split()) if x == mask_string]
    
    if len(mask_positions) != len(mask_word_positions):
        print(mask_positions, mask_word_positions)
        print(sentence)

    distributions = {}
    for idx_in_mask_positions, pos in enumerate(mask_positions):
        probs = torch.softmax(logits[0, pos], dim=-1).cpu().tolist()
        mask_word_pos = mask_word_positions[idx_in_mask_positions] if idx_in_mask_positions < len(mask_word_positions) else None
        distributions[int(pos)] = {
            "mask_index": int(pos),
            "mask_word_position": mask_word_pos,
            "tokens": list(range(len(probs))),
            "probs": probs
        }

    return distributions


try:


    print("Detecting device...")
    device='cuda' if torch.cuda.is_available() else 'cpu'
    if device == 'cuda':
        print("Using GPU")
        torch.cuda.set_device(0)
    else:
        print("Using CPU")


    print("Loading MLM model...")
    mlm_model_name = "answerdotai/ModernBERT-base" #"roberta-base"
    mlm_model = AutoModelForMaskedLM.from_pretrained(mlm_model_name).to(device)
    mlm_tokenizer = AutoTokenizer.from_pretrained(mlm_model_name)
    print("MLM model ready")
  

    print("Printing current time...")
    print(time.time())
    
    ppl_model_name = "gpt2"  # could also use "EleutherAI/gpt-neo-1.3B"
    ppl_tokenizer = AutoTokenizer.from_pretrained(ppl_model_name)
    ppl_model = AutoModelForCausalLM.from_pretrained(ppl_model_name).to(device)
    

    print("Ready")
except Exception as e:
    print("Error during model/tokenizer/lemmatizer setup:"+str(e), file=sys.stderr)
    traceback.print_exc(file=sys.stderr)
    sys.exit(1)
    


def calculate_perplexity(sentence: str) -> float:
    encodings = ppl_tokenizer(sentence, return_tensors="pt").to(device)
    with torch.no_grad():
        outputs = ppl_model(**encodings, labels=encodings.input_ids)
        loss = outputs.loss
    return torch.exp(loss).item()




@app.route('/perplexity', methods=['POST'])
def perplexity():
    sentence = request.data.decode()
    return {"perplexity": calculate_perplexity(sentence)}




@app.route('/ping', methods=['GET'])
def ping():
    return 'pong', 200

@app.route('/mlm', methods=['POST'])
def mlm_predict():
    try:
        
        sentence = request.data.decode()


        if mask_string not in sentence:
            return {"error": f"Sentence must contain a mask token ({mask_string})"}, 400

        distributions = get_mask_distributions(sentence)
        return distributions, 200
    except Exception as e:
        traceback.print_exc()
        return {"error": str(e)}, 500
    
@app.route('/mlm_tokenize', methods=['POST'])
def mlm_tokenize():
    try:
        sentence = request.data.decode()
        tokens = mlm_tokenizer.tokenize(sentence)
        token_ids = mlm_tokenizer.convert_tokens_to_ids(tokens)
        return {"tokens": tokens, "token_ids": token_ids}, 200
    except Exception as e:
        traceback.print_exc()
        return {"error": str(e)}, 500
    
@app.route('/mlm_perplexity', methods=['POST'])
def mlm_perplexity():
    try:
        sentence = request.data.decode()

        # Tokenize once (keep fast tokenizer encoding for word alignment)
        encoded = mlm_tokenizer(sentence, return_tensors="pt", return_offsets_mapping=True)
        input_ids = encoded["input_ids"].to(device)
        attention_mask = encoded["attention_mask"].to(device)

        tokens = mlm_tokenizer.convert_ids_to_tokens(input_ids[0].tolist())
        special_ids = set(mlm_tokenizer.all_special_ids)
        mask_token_id = mlm_tokenizer.mask_token_id

        # Map tokens to word indices (fast tokenizer)
        enc0 = encoded.encodings[0] if hasattr(encoded, "encodings") and encoded.encodings else None
        word_ids = enc0.word_ids() if enc0 is not None else [None] * input_ids.shape[1]
        offsets = enc0.offsets if enc0 is not None else [(0, 0)] * input_ids.shape[1]

        token_probs = []
        word_products = {}

        with torch.no_grad():
            seq_len = input_ids.shape[1]
            for pos in range(seq_len):
                orig_id = int(input_ids[0, pos].item())
                if orig_id in special_ids:
                    continue

                masked_ids = input_ids.clone()
                masked_ids[0, pos] = mask_token_id

                outputs = mlm_model(input_ids=masked_ids, attention_mask=attention_mask)
                logits = outputs.logits[0, pos]
                prob = torch.softmax(logits, dim=-1)[orig_id].item()

                wid = word_ids[pos]
                token_probs.append({
                    "index": pos,
                    "token": tokens[pos],
                    "prob": prob,
                    "word_id": wid
                })
                if wid is not None:
                    # Product of sub-token probabilities for the word
                    word_products[wid] = word_products.get(wid, 1.0) * max(prob, 1e-12)

        # Extract word texts from offsets
        word_info = {}
        for pos, wid in enumerate(word_ids):
            if wid is None:
                continue
            s, e = offsets[pos]
            if wid not in word_info:
                word_info[wid] = [s, e]
            else:
                word_info[wid][0] = min(word_info[wid][0], s)
                word_info[wid][1] = max(word_info[wid][1], e)

        word_probs = []
        for wid in sorted(word_products.keys()):
            s, e = word_info.get(wid, (0, 0))
            word_text = sentence[s:e] if e > s else ""
            word_probs.append({
                "word_id": wid,
                "word": word_text,
                "prob": word_products[wid]
            })

        return {"tokens": tokens, "token_probs": token_probs, "word_probs": word_probs}, 200
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
