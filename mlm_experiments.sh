#!/bin/bash
#SBATCH --time=03:00:00
#SBATCH --account=def-pesantg
#SBATCH --cpus-per-task=1
#SBATCH --gpus=1
#SBATCH --mem=24G

module load java/21.0.1
export JAVA_TOOL_OPTIONS="-Xmx6g"

python server_mlm.py &
SERVER_PID=$!

sleep 30

# Define lists for the last two arguments
TASK_CONFIG_LIST=("MNREAD_MLM_Config" "CollieSent1_MLM_Config" "CollieSent2_MLM_Config" "CollieSent3_MLM_Config" "CollieSent4_MLM_Config")
SENTENCE_BUILDER_LIST=("randomSentenceBuilder" "perplexitySentenceBuilder")

# Loop through combinations of arg5 and arg6
for taskConfig in "${ARG5_LIST[@]}"; do
    for sentenceBuilder in "${ARG6_LIST[@]}"; do
        java -cp target/minicpbp-1.0.jar minicpbp.examples.NLP_MLM_v1 ${1.2} ${5000} ${output} ${1000} ${taskConfig} ${sentenceBuilder}
        java -cp target/minicpbp-1.0.jar minicpbp.examples.NLP_MLM_v2 ${1.2} ${5000} ${output} ${1000} ${taskConfig} ${sentenceBuilder}
    done
done

kill $SERVER_PID