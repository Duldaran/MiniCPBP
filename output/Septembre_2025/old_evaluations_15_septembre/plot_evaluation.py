import os
import json

import matplotlib.pyplot as plt

directory = os.path.dirname(__file__)
json_files = [f for f in os.listdir(directory) if f.endswith('.json')]

all_fluencies = []
all_perplexities = []
labels = []

all_fluencies.append([])
all_fluencies.append([])
all_perplexities.append([])
all_perplexities.append([])
labels.append("MLM_best_perplexity")
labels.append("MLM_best_fluency")

for json_file in json_files:
    print(f"Processing file: {json_file}")
    if "evaluation_results" not in json_file:
        continue
    file_path = os.path.join(directory, json_file)
    with open(file_path, 'r', encoding='utf-8') as f:
        data = json.load(f)
        if "MLM" not in json_file:
            fluencies = []
            perplexities = []
            for item in data['results']:
                if 'LLM_fluency' in item and 'perplexity' in item:
                    fluencies.append(item['LLM_fluency'])
                    perplexities.append(item['perplexity'])

            if fluencies and perplexities:
                all_fluencies.append(fluencies)
                all_perplexities.append(perplexities)
                avg_fluency = sum(fluencies) / len(fluencies)
                if avg_fluency < 10:
                    labels.append("no llm")
                elif avg_fluency > 90:
                    labels.append("no cpbp")
                else:
                    labels.append("llm+cpbp")
        else:
            # For MLM files, collect best_LLM_fluency and best_perplexity
            if 'best_LLM_fluency' in data and 'best_perplexity' in data:
                all_fluencies[1].append(data['best_LLM_fluency']['LLM_fluency'])
                all_fluencies[0].append(data['best_perplexity']['LLM_fluency'])
                all_perplexities[1].append(data['best_LLM_fluency']['perplexity'])
                all_perplexities[0].append(data['best_perplexity']['perplexity'])

if all_fluencies and all_perplexities:
    # Perplexity boxplot with logarithmic y-axis
    plt.figure(figsize=(10, 6))
    plt.boxplot(all_perplexities, labels=labels)
    plt.yscale('log')
    plt.title('Perplexity')
    plt.ylabel('Perplexity (log scale)')
    plt.xticks(rotation=45)
    plt.tight_layout()
    plt.savefig(os.path.join(directory, 'Perplexity.png'))
    plt.close()

    # Fluency boxplot (linear scale, but you can set log if needed)
    plt.figure(figsize=(10, 6))
    plt.boxplot(all_fluencies, labels=labels)
    # Uncomment the next line to use log scale for fluency as well
    # plt.yscale('log')
    plt.title('Fluency')
    plt.ylabel('Fluency')
    plt.xticks(rotation=45)
    plt.tight_layout()
    plt.savefig(os.path.join(directory, 'Fluency.png'))
    plt.close()
    
    all_fluencies = all_fluencies[2:]
    all_perplexities = all_perplexities[2:]
    labels = labels[2:]
    
    
    plt.figure(figsize=(10, 6))
    plt.boxplot(all_perplexities, labels=labels)
    plt.yscale('log')
    plt.title('Perplexity')
    plt.ylabel('Perplexity (log scale)')
    plt.xticks(rotation=45)
    plt.tight_layout()
    plt.savefig(os.path.join(directory, 'Partial_Perplexity.png'))
    plt.close()

    # Fluency boxplot (linear scale, but you can set log if needed)
    plt.figure(figsize=(10, 6))
    plt.boxplot(all_fluencies, labels=labels)
    # Uncomment the next line to use log scale for fluency as well
    # plt.yscale('log')
    plt.title('Fluency')
    plt.ylabel('Fluency')
    plt.xticks(rotation=45)
    plt.tight_layout()
    plt.savefig(os.path.join(directory, 'Partial_Fluency.png'))
    plt.close()