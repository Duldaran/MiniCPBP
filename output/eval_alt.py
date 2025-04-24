from eval_metrics.bleu import Bleu
from eval_metrics.cider import Cider
from eval_metrics.spice import Spice

import spacy
import json
import codecs
import argparse
import os



parser = argparse.ArgumentParser()
parser.add_argument('--result_file', default="", type=str)
args = parser.parse_args()


nlp = spacy.load("en_core_web_sm")

def tokenize(dict):
    for key in dict:
        new_sentence_list = []
        for sentence in dict[key]:
            a = ''
            for token in nlp(sentence):
                a += token.text
                a += ' '
            new_sentence_list.append(a.rstrip())
        dict[key] = new_sentence_list

    return dict


def evaluator(gts, res):
    eval = {}
    # =================================================
    # Set up scorers
    # =================================================
    print('tokenization...')
    # Todo: use Spacy for tokenization
    gts = tokenize(gts)
    res = tokenize(res)

    # =================================================
    # Set up scorers
    # =================================================
    print('setting up scorers...')
    scorers = [
        (Bleu(4), ["Bleu_1", "Bleu_2", "Bleu_3", "Bleu_4"]),
        #(Meteor(), "METEOR"),
        #(Rouge(), "ROUGE_L"),
        (Cider(), "CIDEr"),
        #(Spice(), "SPICE")
    ]

    # =================================================
    # Compute scores
    # =================================================
    for scorer, method in scorers:
        print("computing %s score..." % (scorer.method()))
        score, scores = scorer.compute_score(gts, res)
        if type(method) == list:
            for sc, scs, m in zip(score, scores, method):
                eval[m] = sc 
        else:
            eval[method] = score
    return eval


def load_targets(dataset_file):
    with open(dataset_file, 'r') as fin:
        examples = json.load(fin)
        
    examples_ = []
    for example in examples:
        examples_.append({'concepts': example['concept_set'], 'sentences': example['reference']})
    
    return examples_

targets = load_targets("..\src\main\java\minicpbp\examples\data\Sentence\old_commongen.json")

with open(args.result_file, 'r') as fin:
    results = json.load(fin)

results = [{'sentence': x['sentence'], 'concepts': x['required_words']} for x in results]
targets = targets[1:len(results)+1]

gts = {}
res = {}
for gts_line, res_line in zip(targets, results):
    assert(gts_line['concepts'] == res_line['concepts'])
    key = '#'.join(gts_line['concepts'])
    gts[key] = [x.rstrip('\n') for x in gts_line['sentences']]

    sentence = res_line['sentence']
    sentence.replace('.', ' .')
    sentence.replace(',', ' ,')
    res[key] = [sentence.rstrip('\n')]    

metrics=evaluator(gts, res)

from rouge_score import rouge_scorer
predictions = [x['sentence'] for x in results]
references = [x['sentences'] for x in targets]
scorer = rouge_scorer.RougeScorer(['rouge1', 'rougeL'], use_stemmer=True)

scores = []
for pred, ref in zip(predictions, references):
    rs = [scorer.score(pred, i)['rougeL'].fmeasure for i in ref]
    scores.append(sum(rs)/len(rs))
    
metrics['RougeL'] = sum(scores)/len(scores)
with open("metrics_"+args.result_file, 'w') as stats_file:
            json.dump(metrics, stats_file)




