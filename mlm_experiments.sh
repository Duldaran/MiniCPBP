#!/bin/bash
#SBATCH --time=08:00:00
#SBATCH --account=def-pesantg
#SBATCH --cpus-per-task=9
#SBATCH --gpus=1
#SBATCH --mem=36G



module load java/21.0.1
source venv/bin/activate
export JAVA_TOOL_OPTIONS="-Xmx6g"

(
    python server_mlm.py
) > server_mlm.log 2>&1 &
SERVER_PID=$!

export TRANSFORMERS_OFFLINE=1
export HF_HUB_OFFLINE=1


sleep 30

until curl -s http://localhost:5000/ping | grep -q "pong"; do
    echo "Serveur pas encore prêt... attente..."
    sleep 1
done
echo "Server is ready!"

# Define lists for the last two arguments
SEED_LIST=(4 14 24)
#SEED_LIST=(20 50 100 150 200)
TASK_CONFIG_LIST=("MNREAD_MLM_Config" "CollieSent2_MLM_Config" "CollieSent3_MLM_Config")
SENTENCE_BUILDER_LIST=("randomSentenceBuilder" "perplexitySentenceBuilder")

# Maximum parallel jobs (CPU-bound, adjust based on available CPUs)
MAX_PARALLEL=8

# Counter for parallel jobs
job_count=0

# Loop through combinations
pids=()
for oracle_top_k in 10 25 50; do  ##Modified to complete missing loops (10 25)
    for mask_percent in 0.1 0.2 0.3; do
        for seed in "${SEED_LIST[@]}"; do
            for taskConfig in "${TASK_CONFIG_LIST[@]}"; do
                for sentenceBuilder in "${SENTENCE_BUILDER_LIST[@]}"; do
                    echo "Running experiments with seed: ${seed}, taskConfig: ${taskConfig}, and sentenceBuilder: ${sentenceBuilder}, mask_percent: ${mask_percent}"
                
                    # Run both Java commands in background (they'll queue requests to the Python server)
                    java -cp target/minicpbp-1.0.jar minicpbp.examples.NLP_MLM_v1 1.2 5000 output 100 ${seed} ${taskConfig} ${sentenceBuilder} ${oracle_top_k} ${mask_percent} &
                    pids+=($!)
                    java -cp target/minicpbp-1.0.jar minicpbp.examples.NLP_MLM_v2 1.2 5000 output 100 ${seed} ${taskConfig} ${sentenceBuilder} ${oracle_top_k} ${mask_percent} &
                    pids+=($!)
                    java -cp target/minicpbp-1.0.jar minicpbp.examples.NLP_MLM_noBP 1.2 5000 output 100 ${seed} ${taskConfig} ${sentenceBuilder} ${oracle_top_k} ${mask_percent} &
                    pids+=($!)
                    java -cp target/minicpbp-1.0.jar minicpbp.examples.NLP_MLM_v1_2 1.2 5000 output 100 ${seed} ${taskConfig} ${sentenceBuilder} ${oracle_top_k} ${mask_percent} &
                    pids+=($!)
                    job_count=$((job_count + 4))
                    
                    # Wait when we reach max parallel jobs
                    while [ $job_count -ge $MAX_PARALLEL ]; do
                        wait -n
                        job_count=$((job_count - 1))
                    done
                done
            done
        done
    done
done

# Wait for all remaining jobs to complete
for pid in "${pids[@]}"; do
    wait $pid
done

kill $SERVER_PID