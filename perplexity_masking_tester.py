import numpy as np
import matplotlib.pyplot as plt
import requests

def mask_heatmap_prob(sentence, port=5000):
    """
    Pour une phrase donnée, renvoie un heatmap avec :
    - probabilité de chaque mot
    - probabilité qu'il soit masqué = 1 - probabilité du mot
    """
    words = sentence.split(" ")
    n_words = len(words)

    # ------------------------------------------------------
    # Appel HTTP au serveur MLM pour obtenir les probas
    # ------------------------------------------------------
    try:
        resp = requests.post(f"http://localhost:{port}/mlm_perplexity", data=sentence)
        root = resp.json()
        word_probs_node = root.get("word_probs", [])

        # Liste des probabilités des mots
        word_probs = np.zeros(n_words)
        for wn in word_probs_node:
            idx = wn["word_id"]
            prob = wn["prob"]
            word_probs[idx] = prob
            print(f"Mot: {words[idx]} - Probabilité: {prob:.4f}")

    except Exception as e:
        print("Erreur HTTP ou parsing:", e)
        return None

    # ------------------------------------------------------
    # Construction du heatmap : 1 seule ligne (probabilité d'être masqué)
    # ------------------------------------------------------
    
    
    # Apply power transformation to emphasize lower values
    # gamma < 1 expands low values, gamma > 1 compresses them
    gamma = 0.3  # Adjust between 0.3-0.6 for different effects
    word_probs_transformed = np.power(word_probs, gamma)
    
    heatmap = 1.0 - word_probs_transformed  # Probabilité d'être masqué
    
    for heatmap_value, word in zip(heatmap, words):
        print(f"Mot: {word} - Probabilité d'être masqué (transformée): {heatmap_value:.4f}")

    # ------------------------------------------------------
    # Calcul des largeurs proportionnelles aux mots
    # ------------------------------------------------------
    word_widths = [len(word)/4 for word in words]
    cumulative_widths = np.cumsum([0] + word_widths)
    total_width = cumulative_widths[-1]

    # ------------------------------------------------------
    # Affichage heatmap avec texte overlay
    # ------------------------------------------------------
    fig, ax = plt.subplots(figsize=(0.3 * total_width + 2, 0.5))

    # Utiliser pcolormesh pour des largeurs variables
    for i, word in enumerate(words):
        rect = plt.Rectangle((cumulative_widths[i], 0), word_widths[i], 1, 
                            facecolor=plt.cm.YlGn(heatmap[i]/1.2), 
                            edgecolor='none')
        ax.add_patch(rect)
        
        # Overlay du texte centré dans chaque rectangle
        center_x = cumulative_widths[i] + word_widths[i] / 2
        ax.text(center_x, 0.5, word, ha="center", va="center", 
                color='black', fontsize=10, weight='bold')

    # Configuration des axes
    ax.set_xlim(0, total_width)
    ax.set_ylim(0, 1)
    ax.set_xticks([])
    ax.set_yticks([])
    ax.set_aspect('auto')

    # Retirer les bordures
    for spine in ax.spines.values():
        spine.set_visible(False)

    plt.tight_layout()
    plt.savefig("heatmap.png", dpi=150, bbox_inches='tight')

    return heatmap

# ------------------ Exemple d'utilisation ------------------
sentence = "The family in front of the double doors and into the branch"
heatmap = mask_heatmap_prob(sentence, port=5000)
