#!/bin/bash
#SBATCH --account=def-pesantg
#SBATCH --time=03:00:00
#SBATCH --cpus-per-task=9
#SBATCH --gpus=1
#SBATCH --mem=24G

module load java/21.0.1
source venv/bin/activate
module load python/3.12.4
export JAVA_TOOL_OPTIONS="-Xmx6g"

export TRANSFORMERS_OFFLINE=1
export HF_HUB_OFFLINE=1

python server_molecules.py > server_molecules.log &
SERVER_PID=$!

sleep 30

until curl -s http://localhost:5001/ping | grep -q "pong"; do
    echo "Serveur pas encore prêt... attente..."
    sleep 1
done
echo "Server is ready!"

output_dir="molecules_results"

# Specific configuration: seed 7, ref gpt (geai blanc), cpbp+ (v1_2), perplexity
oracle_top_k=50
mask_percent=0.2
seed=7
ref="gpt"
taskConfig="v1_2"  # cpbp+
sentenceBuilder="perplexity"

echo "Running molecules experiment with seed: ${seed}, ref: ${ref}, taskConfig: ${taskConfig}, sentenceBuilder: ${sentenceBuilder}, mask_percent: ${mask_percent}"

java -cp target/minicpbp-1.0.jar minicpbp.examples.molecules.TestGenOracle ${taskConfig} 1.2 ${output_dir} ${sentenceBuilder} ${seed} 100 ${ref} ${mask_percent} ${oracle_top_k}

cd ${output_dir}
python perplexity_calculator.py --model ../entropy/gpt2_zinc_87m --batch-size 32

kill $SERVER_PID