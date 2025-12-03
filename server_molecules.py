import torch
from flask import Flask, request
import traceback
from transformers import AutoTokenizer, AutoModelForMaskedLM, GPT2LMHeadModel, GPT2TokenizerFast

# Load ChemBERTa model
print("Loading ChemBERTa model...")
device = 'cuda' if torch.cuda.is_available() else 'cpu'
print(f"Using device: {device}")

tokenizer = AutoTokenizer.from_pretrained("seyonec/ChemBERTa-zinc-base-v1")
model = AutoModelForMaskedLM.from_pretrained("seyonec/ChemBERTa-zinc-base-v1").to(device)
print("Model loaded successfully")


ppl_tokenizer = GPT2TokenizerFast.from_pretrained("entropy/gpt2_zinc_87m", max_len=40)
ppl_model = GPT2LMHeadModel.from_pretrained('entropy/gpt2_zinc_87m').to(device)

mask_string = "<mask>"

app = Flask(__name__)

def get_mask_distributions(sentence):
    """Get probability distributions for masked positions in the sentence."""
    inputs = tokenizer(sentence, return_tensors="pt").to(device)
    with torch.no_grad():
        outputs = model(**inputs)
        logits = outputs.logits

    mask_token_id = tokenizer.mask_token_id
    mask_positions = (inputs.input_ids == mask_token_id).nonzero(as_tuple=True)[1].tolist()
    mask_positions.sort()


    distributions = {}
    for idx_in_mask_positions, pos in enumerate(mask_positions):
        probs = torch.softmax(logits[0, pos], dim=-1).cpu().tolist()
        distributions[int(pos)-1] = {
            "mask_index": int(pos)-1,
            "tokens": list(range(len(probs))),
            "probs": probs
        }

    return distributions

@app.route('/ping', methods=['GET'])
def ping():
    return 'pong', 200

@app.route('/mlm', methods=['POST'])
def mlm_predict():
    """Predict masked tokens in a molecule SMILES string."""
    try:
        sentence = request.data.decode()
        sentence = sentence.replace("*", mask_string)

        if mask_string not in sentence:
            return {"error": f"Sentence must contain a mask token ({mask_string})"}, 400

        distributions = get_mask_distributions(sentence)
        return distributions, 200
    except Exception as e:
        traceback.print_exc()
        return {"error": str(e)}, 500

@app.route('/mlm_tokenize', methods=['POST'])
def mlm_tokenize():
    """Tokenize a molecule SMILES string."""
    try:
        sentence = request.data.decode()
        tokens = tokenizer.tokenize(sentence)
        token_ids = tokenizer.convert_tokens_to_ids(tokens)
        return {"tokens": tokens, "token_ids": token_ids}, 200
    except Exception as e:
        traceback.print_exc()
        return {"error": str(e)}, 500

@app.route('/mlm_perplexity', methods=['POST'])
def mlm_perplexity():
    """Calculate pseudo-perplexity for a molecule SMILES string."""
    try:
        sentence = request.data.decode()

        # Tokenize
        encoded = tokenizer(sentence, return_tensors="pt")
        input_ids = encoded["input_ids"].to(device)
        attention_mask = encoded["attention_mask"].to(device)

        tokens = tokenizer.convert_ids_to_tokens(input_ids[0].tolist())
        special_ids = set(tokenizer.all_special_ids)
        mask_token_id = tokenizer.mask_token_id

        token_probs = []

        with torch.no_grad():
            seq_len = input_ids.shape[1]
            for pos in range(seq_len):
                orig_id = int(input_ids[0, pos].item())
                if orig_id in special_ids:
                    continue

                # Mask this position
                masked_ids = input_ids.clone()
                masked_ids[0, pos] = mask_token_id

                # Get prediction
                outputs = model(input_ids=masked_ids, attention_mask=attention_mask)
                logits = outputs.logits[0, pos]
                prob = torch.softmax(logits, dim=-1)[orig_id].item()

                token_probs.append({
                    "index": pos,
                    "token": tokens[pos],
                    "prob": prob
                })

        return {"tokens": tokens, "token_probs": token_probs}, 200
    except Exception as e:
        traceback.print_exc()
        return {"error": str(e)}, 500
    

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

if __name__ == '__main__':
    print("Starting Flask server...")
    app.run(host="0.0.0.0", port=5001, threaded=True)