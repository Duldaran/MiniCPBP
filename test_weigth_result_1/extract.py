import json
import os

def extract_sentences_from_json(file_path):
    with open(file_path, 'r', encoding='utf-8') as f:
        data = json.load(f)

    sentences = []

    if isinstance(data, list):
        for item in data:
            if "sentence" in item:
                sentences.append(item["sentence"])

    elif isinstance(data, dict):
        for key, value in data.items():
            if isinstance(value, list):
                for item in value:
                    if isinstance(item, dict) and "sentence" in item:
                        sentences.append(item["sentence"])

    return sentences

def save_sentences_to_txt(grouped_sentences, output_path):
    with open(output_path, 'w', encoding='utf-8') as f:
        for filename, sentences in grouped_sentences.items():
            f.write(f"=== {filename} ===\n")
            for sentence in sentences:
                f.write(sentence.strip() + "\n")
            f.write("\n")  # Ligne vide entre fichiers

def main():
    directory_path = "./test_weigth_result_1"
    output_txt = "sentences_grouped.txt"

    grouped_sentences = group_sentences_by_file(directory_path)
    save_sentences_to_txt(grouped_sentences, output_txt)
    print(f"Fichier texte créé : {output_txt}")

def group_sentences_by_file(directory):
    results = {}
    for filename in os.listdir(directory):
        if filename.endswith(".json"):
            file_path = os.path.join(directory, filename)
            sentences = extract_sentences_from_json(file_path)
            results[filename] = sentences
    return results

if __name__ == "__main__":
    main()
