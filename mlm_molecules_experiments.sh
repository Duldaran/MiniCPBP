#!/bin/bash
#SBATCH --time=03:00:00
#SBATCH --account=def-pesantg
#SBATCH --cpus-per-task=9
#SBATCH --gpus=1
#SBATCH --mem=24G

module load java/21.0.1
export JAVA_TOOL_OPTIONS="-Xmx6g"

python server_molecules.py &
SERVER_PID=$!

sleep 30

# Define lists for the last two arguments
SEED_LIST=(1 2 3 4 5 6 7)
REF_LIST=("gpt" "no_gpt")
TASK_CONFIG_LIST=("v1" "v2")
SENTENCE_BUILDER_LIST=("random" "perplexity")

# Maximum parallel jobs (CPU-bound, adjust based on available CPUs)
MAX_PARALLEL=4

# Counter for parallel jobs
job_count=0

# Loop through combinations
for seed in "${SEED_LIST[@]}"; do
    for ref in "${REF_LIST[@]}"; do
        for taskConfig in "${TASK_CONFIG_LIST[@]}"; do
            for sentenceBuilder in "${SENTENCE_BUILDER_LIST[@]}"; do
                echo "Running experiments with seed: ${seed}, ref: ${ref}, taskConfig: ${taskConfig}, and sentenceBuilder: ${sentenceBuilder}"
                
                java -cp target/minicpbp-1.0.jar minicpbp.examples.molecules.TestGenOracle ${taskConfig} 1.2  output ${sentenceBuilder} ${seed} 50 ${ref}  &
                
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

# Wait for all remaining jobs to complete
wait

kill $SERVER_PID