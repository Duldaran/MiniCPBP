#!/bin/bash
#SBATCH --time=06:00:00
#SBATCH --account=def-pesantg
#SBATCH --cpus-per-task=9
#SBATCH --gpus=1
#SBATCH --mem=24G

module load java/21.0.1
source venv/bin/activate
export JAVA_TOOL_OPTIONS="-Xmx6g"

python server_molecules.py > server_molecules.log &
SERVER_PID=$!

export TRANSFORMERS_OFFLINE=1
export HF_HUB_OFFLINE=1


sleep 30

until curl -s http://localhost:5001/ping | grep -q "pong"; do
    echo "Serveur pas encore prêt... attente..."
    sleep 1
done
echo "Server is ready!"

# Define lists for the last two arguments
SEED_LIST=(1 3 5 7)
REF_LIST=("gpt" "no_gpt")
TASK_CONFIG_LIST=("v1" "v2" "v2_noBP" "v1_3")
SENTENCE_BUILDER_LIST=("random" "perplexity")

# Maximum parallel jobs (CPU-bound, adjust based on available CPUs)
MAX_PARALLEL=8

# Counter for parallel jobs
job_count=0

pids=()
# Loop through combinations
for oracle_top_k in 10 25 50; do
    for mask_percent in 0.1 0.2 0.3; do
        for seed in "${SEED_LIST[@]}"; do
            for ref in "${REF_LIST[@]}"; do
                for taskConfig in "${TASK_CONFIG_LIST[@]}"; do
                    for sentenceBuilder in "${SENTENCE_BUILDER_LIST[@]}"; do
                        echo "Running experiments with seed: ${seed}, ref: ${ref}, taskConfig: ${taskConfig}, and sentenceBuilder: ${sentenceBuilder}, mask_percent: ${mask_percent}"
                        
                        java -cp target/minicpbp-1.0.jar minicpbp.examples.molecules.TestGenOracle ${taskConfig} 1.2  output ${sentenceBuilder} ${seed} 100 ${ref} ${mask_percent} ${oracle_top_k} &
                        pids+=($!)
                        job_count=$((job_count + 1))
                        
                        # Wait when we reach max parallel jobs
                        if [ $job_count -ge $MAX_PARALLEL ]; then
                            wait -n  # Wait for any one job to finish
                            job_count=$((job_count - 1))
                        fi
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