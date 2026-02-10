#!/bin/bash
#SBATCH --time=02:00:00
#SBATCH --account=def-pesantg
#SBATCH --cpus-per-task=5
#SBATCH --gpus=1
#SBATCH --mem=48G

module load java/21.0.1
module load python/3.12.4
source venv/bin/activate
unset JAVA_TOOL_OPTIONS
export TRANSFORMERS_OFFLINE=1
export HF_HUB_OFFLINE=1

python server_mlm.py --port 5005 > server_mlm_mnread.log 2>&1 &
SERVER_PID=$!

sleep 30

until curl -s http://localhost:5005/ping | grep -q "pong"; do
    echo "Serveur pas encore prêt... attente..."
    sleep 1
done
echo "Server is ready!"

oracle_top_k=50
mask_percent=0.2

# Maximum parallel jobs
MAX_PARALLEL=2
job_count=0
pids=()



# Test 2: mnread ref mansfield seed 106 cp les deux (both sentence builders)
echo "Running MNREAD experiment: seed 106, ref MANSFIELD, cp (noBP), both sentence builders"
java -cp target/minicpbp-1.0.jar minicpbp.examples.NLP_MLM_noBP 1.2 5005 output 100 106 "MNREAD_MLM_Config" "randomSentenceBuilder" ${oracle_top_k} ${mask_percent} "AUTHORS" &
pids+=($!)
job_count=$((job_count + 1))

if [ $job_count -ge $MAX_PARALLEL ]; then
    wait -n
    job_count=$((job_count - 1))
fi

java -cp target/minicpbp-1.0.jar minicpbp.examples.NLP_MLM_noBP 1.2 5005 output 100 106 "MNREAD_MLM_Config" "perplexitySentenceBuilder" ${oracle_top_k} ${mask_percent} "AUTHORS" &
pids+=($!)
job_count=$((job_count + 1))

if [ $job_count -ge $MAX_PARALLEL ]; then
    wait -n
    job_count=$((job_count - 1))
fi

# Test 3: mnread ref bonlarron seed 106 cp les deux (both sentence builders)
echo "Running MNREAD experiment: seed 106, ref BONLARRON, cp (noBP), both sentence builders"
java -cp target/minicpbp-1.0.jar minicpbp.examples.NLP_MLM_noBP 1.2 5005 output 100 106 "MNREAD_MLM_Config" "randomSentenceBuilder" ${oracle_top_k} ${mask_percent} "BONLARRON" &
pids+=($!)
job_count=$((job_count + 1))

if [ $job_count -ge $MAX_PARALLEL ]; then
    wait -n
    job_count=$((job_count - 1))
fi


# Test 4: mnread ref bonlarron seed 175 cpbp+ perplexity
echo "Running MNREAD experiment: seed 175, ref BONLARRON, cpbp+ (v1_2), perplexity"
java -cp target/minicpbp-1.0.jar minicpbp.examples.NLP_MLM_v1_2 1.2 5005 output 100 175 "MNREAD_MLM_Config" "perplexitySentenceBuilder" ${oracle_top_k} ${mask_percent} "BONLARRON" &
pids+=($!)

# Wait for all remaining jobs to complete
for pid in "${pids[@]}"; do
    wait $pid
done

kill $SERVER_PID