#!/bin/bash
#SBATCH --time=03:00:00
#SBATCH --account=def-pesantg
#SBATCH --cpus-per-task=1
#SBATCH --gpus=1
#SBATCH --mem=24G
#SBATCH --output=outputs/stdout.log
#SBATCH --error=outputs/stderr.log

source venv/bin/activate

module load java/21.0.1
export JAVA_TOOL_OPTIONS="-Xmx6g"
OUTPUT_DIR="test_weigth_result"
mkdir -p "$OUTPUT_DIR"
PORT=5000

python -u server_cleaned.py  > outputs/flask_stdout.log 2> outputs/flask_stderr.log &
SERVER_PID=$!


timeout=30
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

values=(0.1 0.4 0.6 0.8 1 1.2 1.5 1.8 2 2.5 3.0 3.7 4.5)


MAX_JOBS=4

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

# -----------------------------
# Run Sentence_cleaned in parallel
# -----------------------------
pids=()
for val in "${values[@]}"; do
    echo "Running Sentence_cleaned with argument $val"
    run_with_semaphore "java -cp target/minicpbp-1.0.jar minicpbp.examples.Sentence_cleaned $val $PORT $OUTPUT_DIR 10" pids
done

# Wait for remaining Sentence_cleaned
for pid in "${pids[@]}"; do
    wait $pid
done

# -----------------------------
# Run CollieSent1 in parallel
# -----------------------------
pids=()
for val in "${values[@]}"; do
    echo "Running CollieSent1 with argument $val"
    run_with_semaphore "java -cp target/minicpbp-1.0.jar minicpbp.examples.CollieSent1 $val $PORT $OUTPUT_DIR 10" pids
done

# Wait for remaining CollieSent1
for pid in "${pids[@]}"; do
    wait $pid
done

kill $SERVER_PID
