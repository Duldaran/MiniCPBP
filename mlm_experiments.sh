#!/bin/bash
#SBATCH --time=03:00:00
#SBATCH --account=def-pesantg
#SBATCH --cpus-per-task=9
#SBATCH --gpus=1
#SBATCH --mem=24G

module load java/21.0.1
export JAVA_TOOL_OPTIONS="-Xmx6g"

python server_mlm.py &
SERVER_PID=$!

sleep 30

# Define lists for the last two arguments
SEED_LIST=(4 14 24)
TASK_CONFIG_LIST=("MNREAD_MLM_Config" "CollieSent1_MLM_Config" "CollieSent2_MLM_Config" "CollieSent3_MLM_Config" "CollieSent4_MLM_Config")
SENTENCE_BUILDER_LIST=("randomSentenceBuilder" "perplexitySentenceBuilder")

# Maximum parallel jobs (CPU-bound, adjust based on available CPUs)
MAX_PARALLEL=8

# Counter for parallel jobs
job_count=0

# Loop through combinations
for seed in "${SEED_LIST[@]}"; do
    for taskConfig in "${TASK_CONFIG_LIST[@]}"; do
        for sentenceBuilder in "${SENTENCE_BUILDER_LIST[@]}"; do
            echo "Running experiments with seed: ${seed}, taskConfig: ${taskConfig}, and sentenceBuilder: ${sentenceBuilder}"
            
            # Run both Java commands in background (they'll queue requests to the Python server)
            java -cp target/minicpbp-1.0.jar minicpbp.examples.NLP_MLM_v1 1.2 5000 output 500 ${seed} ${taskConfig} ${sentenceBuilder} &
            java -cp target/minicpbp-1.0.jar minicpbp.examples.NLP_MLM_v2 1.2 5000 output 500 ${seed} ${taskConfig} ${sentenceBuilder} &
            
            job_count=$((job_count + 2))
            
            # Wait when we reach max parallel jobs
            if [ $job_count -ge $MAX_PARALLEL ]; then
                wait -n  # Wait for any one job to finish
                job_count=$((job_count - 1))
            fi
        done
    done
done

# Wait for all remaining jobs to complete
wait

kill $SERVER_PID