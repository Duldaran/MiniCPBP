#!/bin/bash
#SBATCH --time=03:00:00
#SBATCH --account=def-pesantg
#SBATCH --cpus-per-task=1
#SBATCH --gpus=h100_20gb:1
#SBATCH --mem=48G
#SBATCH --job-name=NLP_v5_MNREAD_Perturbed
#SBATCH --output=nlp_v5_mnread_perturbed_%j.log

module load java/21.0.1
module load python/3.12.4
source venv/bin/activate
unset JAVA_TOOL_OPTIONS
export TRANSFORMERS_OFFLINE=1
export HF_HUB_OFFLINE=1
export HF_HOME=$SCRATCH/huggingface
export HF_HUB_CACHE=$HF_HOME/hub
export TRANSFORMERS_CACHE=$HF_HOME/hub

# Start server_v5 on port 8105
python server_v5.py --port 8105 > server_v5_mnread_perturbed.log 2>&1 &
SERVER_PID=$!

sleep 30

# Wait for server to be ready
MAX_SERVER_WAIT=180
WAITED=0
until curl -s http://localhost:8105/token -X POST -d "test" > /dev/null 2>&1; do
    if ! kill -0 $SERVER_PID 2>/dev/null; then
        echo "Server process crashed before it became ready."
        exit 1
    fi
    if [ $WAITED -ge $MAX_SERVER_WAIT ]; then
        echo "Server readiness timeout after ${MAX_SERVER_WAIT}s."
        kill $SERVER_PID 2>/dev/null
        exit 1
    fi
    echo "Server not ready yet... waiting..."
    sleep 1
    WAITED=$((WAITED + 1))
done
echo "Server is ready!"

# Perturbation: relax line-size constraints by 5%
java -cp target/minicpbp-1.0.jar minicpbp.examples.NLP_v5 1.0 8105 output 100 zephyr MNREADRelaxedConfig 100 0.10

# Kill the server
kill $SERVER_PID
