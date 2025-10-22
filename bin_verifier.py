import json
import os


def can_greedy_split(sentence: str, char_cost: dict) -> bool:
    """
    Greedy split of sentence into 3 lines.
    Each line must be adjustable (via flexible space cost) to exactly `target`.
    Also prints each line with its costs.
    """
    space_min = 410
    space_max = 640
    target = 15896

    words = sentence.split()
    line_count = 0
    char_sum = 0
    spaces = 0
    is_valid = True

    current_words = []
    lines = []  # store (line_text, char_sum, spaces)

    def line_can_reach(char_sum, spaces):
        min_cost = char_sum + spaces * space_min
        max_cost = char_sum + spaces * space_max
        return min_cost <= target <= max_cost

    for i, word in enumerate(words):
        word_cost = sum(char_cost.get(c, 0) for c in word)
        new_char_sum = char_sum + word_cost
        new_spaces = spaces + (1 if spaces > 0 or char_sum > 0 else 0)

        min_cost = new_char_sum + new_spaces * space_min
        if min_cost > target:  # can't add this word
            # finalize current line
            if not line_can_reach(char_sum, spaces):
                is_valid = False
            lines.append((" ".join(current_words), char_sum, spaces))
            line_count += 1

            # start new line
            current_words = [word]
            char_sum = word_cost
            spaces = 0
        else:
            char_sum = new_char_sum
            spaces = new_spaces
            current_words.append(word)

    # finalize last line
    if not line_can_reach(char_sum, spaces):
        is_valid = False
    lines.append((" ".join(current_words), char_sum, spaces))
    line_count += 1

    # --- Print lines with costs ---
    for idx, (text, csum, sp) in enumerate(lines, start=1):
        min_cost = csum + sp * space_min
        max_cost = csum + sp * space_max
        print(f"Line {idx}: '{text}'")
        print(f"  Char-only cost = {csum}")
        print(f"  With spaces: min = {min_cost}, max = {max_cost}\n")
        
    line_count = 0
    char_sum = 0
    spaces = 0
    is_valid = True

    current_words = []
    lines = []  # store (line_text, char_sum, spaces)

    for i, word in enumerate(words):
        word_cost = sum(char_cost.get(c, 0) for c in word)
        new_char_sum = char_sum + word_cost
        new_spaces = spaces + (1 if spaces > 0 or char_sum > 0 else 0)

        min_cost = new_char_sum + new_spaces * space_min
        if char_sum + spaces * space_min > target:  # can't add this word
            # finalize current line
            if not line_can_reach(char_sum, spaces):
                is_valid = False
            lines.append((" ".join(current_words), char_sum, spaces))
            line_count += 1

            # start new line
            current_words = [word]
            char_sum = word_cost
            spaces = 0
        else:
            char_sum = new_char_sum
            spaces = new_spaces
            current_words.append(word)

    # finalize last line
    if not line_can_reach(char_sum, spaces):
        is_valid = False
    lines.append((" ".join(current_words), char_sum, spaces))
    line_count += 1

    # --- Print lines with costs ---
    for idx, (text, csum, sp) in enumerate(lines, start=1):
        min_cost = csum + sp * space_min
        max_cost = csum + sp * space_max
        print(f"Line {idx}: '{text}'")
        print(f"  Char-only cost = {csum}")
        print(f"  With spaces: min = {min_cost}, max = {max_cost}\n")


    return is_valid and line_count == 3

path = os.path.join("src", "main", "java", "minicpbp", "examples", "data", "MNREAD", "TimesCost_modified.json")

# Read and parse the file
with open(path, "r", encoding="utf-8") as f:
    char_cost = json.load(f)
    
print(can_greedy_split("It making me wonder if I am going to be able to do it right", char_cost))