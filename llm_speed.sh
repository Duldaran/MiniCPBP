#!/bin/bash
#SBATCH --time=00:10:00
#SBATCH --account=def-pesantg
#SBATCH --cpus-per-task=1
#SBATCH --gpus=1
#SBATCH --mem=12G

module load python/3.12.4
source venv/bin/activate
export TRANSFORMERS_OFFLINE=1
export HF_HUB_OFFLINE=1

python llm_speed_test.py > llm_speed_test.log 2>&1
