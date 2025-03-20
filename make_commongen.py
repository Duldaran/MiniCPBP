import csv
import json
import ast

# Open the CSV file
csv_file = 'old_commongen.csv'
json_file = 'old_commongen.json'

# Initialize an empty list to hold the JSON objects
json_array = []

# Read the CSV file
with open(csv_file, mode='r', encoding='utf-8') as file:
    csv_reader = csv.DictReader(file)
    for row in csv_reader:
        # Create a JSON object for each row
        json_object = {
            "concept_set": ast.literal_eval(row["concepts"]),
            "instruction": "",
            "reference": ast.literal_eval(row["references"]),
            "target": row["target"]
        }
        json_array.append(json_object)

# Write the JSON array to a file
with open(json_file, mode='w', encoding='utf-8') as file:
    json.dump(json_array, file, indent=4, ensure_ascii=False)

print(f"JSON file '{json_file}' created successfully.")