#!/bin/bash
#SBATCH --time=08:00:00
#SBATCH --account=def-pesantg
#SBATCH --cpus-per-task=9
#SBATCH --gpus=h100_10gb:1
#SBATCH --mem=48G



module load java/21.0.1
module load python/3.12.4
source venv/bin/activate
unset JAVA_TOOL_OPTIONS

export TRANSFORMERS_OFFLINE=1
export HF_HUB_OFFLINE=1

python server_mlm.py --port 5007 > server_mlm_mnread_3.log 2>&1 &
SERVER_PID=$!




sleep 30

until curl -s http://localhost:5007/ping | grep -q "pong"; do
    echo "Serveur pas encore prêt... attente..."
    sleep 1
done
echo "Server is ready!"

# Define lists for the last two arguments
SEED_LIST=(1 2 3 5 6 8 9 10 12)
TASK_CONFIG_LIST=("MNREAD_MLM_Config")
SENTENCE_BUILDER_LIST=("perplexitySentenceBuilder")

# Maximum parallel jobs (CPU-bound, adjust based on available CPUs)
MAX_PARALLEL=4

# Counter for parallel jobs
job_count=0

# Loop through combinations
pids=()

ref="INVALID_EASY"

for oracle_top_k in 50; do  ##Modified to complete missing loops (10 25)
    for mask_percent in 0.2 ; do
        for seed in "${SEED_LIST[@]}"; do
            for taskConfig in "${TASK_CONFIG_LIST[@]}"; do
                for sentenceBuilder in "${SENTENCE_BUILDER_LIST[@]}"; do
                    echo "Running experiments with seed: ${seed}, taskConfig: ${taskConfig}, and sentenceBuilder: ${sentenceBuilder}, mask_percent: ${mask_percent}"
                
                    # Run both Java commands in background (they'll queue requests to the Python server)
                    java -cp target/minicpbp-1.0.jar minicpbp.examples.NLP_MLM_v2_AnyStart 1.2 5007 output 50 ${seed} ${taskConfig} ${sentenceBuilder} ${oracle_top_k} ${mask_percent} ${ref} &
                    pids+=($!)
                    
                    job_count=$((job_count + 1))
                    
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