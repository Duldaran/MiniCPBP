from nltk.translate.bleu_score import sentence_bleu, SmoothingFunction
from nltk.tokenize import word_tokenize
import sys
import numpy as np

def tokenize_v7(molecule):
    """
    Tokenize a molecule string (likely SMILES notation).
    
    Args:
        molecule: String representation of molecule
        
    Returns:
        List of tokens
    """
    molecule_chars = list(molecule)
    tokens = []
    i = 0
    
    while i < len(molecule_chars):
        # Handle % followed by two characters
        if molecule_chars[i] == '%':
            tokens.append(f"%{molecule_chars[i+1]}{molecule_chars[i+2]}")
            i += 3
            continue
        
        # Handle Cl (Chlorine)
        elif i < len(molecule_chars) - 1 and molecule_chars[i] == 'C' and molecule_chars[i+1] == 'l':
            tokens.append("Cl")
            i += 2
            continue
        
        # Handle Br (Bromine)
        elif i < len(molecule_chars) - 1 and molecule_chars[i] == 'B' and molecule_chars[i+1] == 'r':
            tokens.append("Br")
            i += 2
            continue
        
        # Handle H3
        elif i < len(molecule_chars) - 1 and molecule_chars[i] == 'H' and molecule_chars[i+1] == '3':
            tokens.append("H3")
            i += 2
            continue
        
        # Single character token
        tokens.append(molecule_chars[i])
        i += 1
    
    return tokens

def self_bleu(sentences, isMolecule=False):
    smooth = SmoothingFunction().method1
    scores = []
    
    for i, hypothesis in enumerate(sentences):
        if isMolecule:
            hypothesis_tokens = tokenize_v7(hypothesis)
            references = [tokenize_v7(s) for j, s in enumerate(sentences) if i != j]
        else:
            references = [s.split() for j, s in enumerate(sentences) if i != j]
            hypothesis_tokens = hypothesis.split()
        score = sentence_bleu(references, hypothesis_tokens, 
                             smoothing_function=smooth)
        scores.append(score)
    
    return np.mean(scores)  # Lower = more diverse

def main(file_path, indexes):
    with open(file_path, 'r', encoding='utf-8') as f:
        sentences = [line.strip().split(',')[0] for line in f if line.strip()]
    
    sentences = [sentences[i] for i in indexes]
    
    if len(sentences[0].split()) == 1:
        isMolecule = True
    else:
        isMolecule = False
        
    score = self_bleu(sentences, isMolecule)
    print(f"Self-BLEU Score: {score:.4f}")
    

if __name__ == "__main__":
    file_path = input("Enter text file path: ")
    indexes = input("Enter sentence indexes to compare (comma-separated, e.g., 0,1,2): ")
    
    try:
        indexes = [int(i.strip()) for i in indexes.split(",")]
        main(file_path, indexes)
    except ValueError:
        print("Invalid input. Please enter comma-separated integers.")