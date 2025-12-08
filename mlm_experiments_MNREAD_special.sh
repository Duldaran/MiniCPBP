#!/bin/bash
#SBATCH --time=01:00:00
#SBATCH --account=def-pesantg
#SBATCH --cpus-per-task=9
#SBATCH --gpus=1
#SBATCH --mem=48G

module load java/21.0.1
source venv/bin/activate
unset JAVA_TOOL_OPTIONS
export TRANSFORMERS_OFFLINE=1
export HF_HUB_OFFLINE=1

(
    python server_mlm.py
) > server_mlm.log 2>&1 &
SERVER_PID=$!

sleep 30

until curl -s http://localhost:5000/ping | grep -q "pong"; do
    echo "Serveur pas encore prêt... attente..."
    sleep 1
done
echo "Server is ready!"

# Failed configurations (seed -> builder)
declare -A FAILED=(
    [175]="perplexitySentenceBuilder"
    [146]="randomSentenceBuilder"
    [131]="perplexitySentenceBuilder"
    [84]="randomSentenceBuilder"
    [63]="perplexitySentenceBuilder"
    [34]="both"
)

TASK_CONFIG="MNREAD_MLM_Config"
oracle_top_k=50
mask_percent=0.2
ref="BONLARRON"
MAX_PARALLEL=8
job_count=0
pids=()

for seed in "${!FAILED[@]}"; do
    builders=${FAILED[$seed]}

    if [[ "$builders" == "both" ]]; then
        BUILDER_LIST=("randomSentenceBuilder" "perplexitySentenceBuilder")
    else
        BUILDER_LIST=("$builders")
    fi

    for sentenceBuilder in "${BUILDER_LIST[@]}"; do
        echo "Rerunning FAILED experiment seed=$seed builder=$sentenceBuilder"

        java -cp target/minicpbp-1.0.jar minicpbp.examples.NLP_MLM_v1_2 \
            1.2 5000 output 100 "$seed" "$TASK_CONFIG" "$sentenceBuilder" \
            "$oracle_top_k" "$mask_percent" "$ref" &

        pids+=($!)
        job_count=$((job_count + 1))

        while [ $job_count -ge $MAX_PARALLEL ]; do
            wait -n
            job_count=$((job_count - 1))
        done
    done
done

for pid in "${pids[@]}"; do
    wait $pid
done

kill $SERVER_PID