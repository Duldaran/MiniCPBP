#!/bin/bash
#SBATCH --time=05:00:00
#SBATCH --account=def-pesantg
#SBATCH --cpus-per-task=1
#SBATCH --gpus=nvidia_h100_80gb_hbm3_2g.20gb:1
#SBATCH --mem=24G
#SBATCH --output=outputs/stdout.log
#SBATCH --error=outputs/stderr.log


module load java/21.0.1

source venv/bin/activate
export JAVA_TOOL_OPTIONS="-Xmx6g"
OUTPUT_DIR="test_weigth_result"
mkdir -p "$OUTPUT_DIR"

which python
python --version
python -c "import torch; import transformers; print('Preload done')"

get_random_port() {
    local base_port=5000
    local range=100  # Try ports between 5000 and 5099
    local max_tries=50

    for ((i = 0; i < max_tries; i++)); do
        port=$((base_port + RANDOM % range))
        if ! ss -tuln | grep -q ":$port "; then
            echo "$port"
            return 0
        fi
    done

    echo "No available port found near $base_port" >&2
    return 1
}

PORT=$(get_random_port)

if [ $? -eq 0 ]; then
    echo "Using port $PORT"
    # You can now launch your server with $PORT
else
    echo "Failed to find available port"
    exit 1
fi


python -u server_cleaned.py --port "$PORT" > outputs/flask_combined.log 2>&1 &
SERVER_PID=$!


timeout=300
for ((i=0; i<timeout; i++)); do
    if curl -s "http://localhost:$PORT/ping" >/dev/null; then
        echo "Server ready on port $PORT"
        break
    else
        echo "Waiting for server... ($i)"
        sleep 1
    fi
done


if ! curl -s "http://localhost:$PORT/ping" >/dev/null; then
    echo "Server did not start in time."
    kill $SERVER_PID
    exit 1
fi

values=(0.1 0.4 0.6 0.8 1 1.2 1.5 1.8 2 2.5 3.0 3.7 4.5 5.0)


MAX_JOBS=2

# --- Function to run command with semaphore ---
run_with_semaphore() {
    local cmd="$1"
    local pids_array_name="$2"

    # Run command in background
    eval "$cmd" &
    local pid=$!
    eval "$pids_array_name+=(\$pid)"

    # If max jobs reached, wait for the first to finish
    eval 'local current_pids=( "${'"$pids_array_name"'[@]}" )'
    if (( ${#current_pids[@]} >= MAX_JOBS )); then
        wait "${current_pids[0]}"
        eval "$pids_array_name=(\"\${$pids_array_name[@]:1}\")"
    fi
}


NUM_RUNS=10

# -----------------------------
# Run Sentence_cleaned in parallel
# -----------------------------
#pids=()
#for val in "${values[@]}"; do
#    echo "Running Sentence_cleaned with argument $val"
#    run_with_semaphore "srun --exclusive -N1 -n1 java -cp target/minicpbp-1.0.jar minicpbp.examples.Sentence_cleaned $val $PORT $OUTPUT_DIR $NUM_RUNS" pids
#done

# Wait for remaining Sentence_cleaned
#for pid in "${pids[@]}"; do
#    wait $pid
#done


# -----------------------------
# Run Sentence_old_commongen in parallel
# -----------------------------
pids=()
for val in "${values[@]}"; do
    echo "Running Sentence_old_commongen with argument $val"
    run_with_semaphore "srun --exclusive -N1 -n1 java -cp target/minicpbp-1.0.jar minicpbp.examples.Sentence_old_commongen $val $PORT $OUTPUT_DIR $NUM_RUNS" pids
done

# Wait for remaining Sentence_old_commongen
for pid in "${pids[@]}"; do
    wait $pid
done


# -----------------------------
# Run CollieSent1 in parallel
# -----------------------------
#pids=()
#for val in "${values[@]}"; do
#    echo "Running CollieSent1 with argument $val"
#    run_with_semaphore "srun --exclusive -N1 -n1java -cp target/minicpbp-1.0.jar minicpbp.examples.CollieSent1 $val $PORT $OUTPUT_DIR $NUM_RUNS" pids
#done

# Wait for remaining CollieSent1
#for pid in "${pids[@]}"; do
#    wait $pid
#done

kill $SERVER_PID
