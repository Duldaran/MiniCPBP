#!/bin/bash
#SBATCH --time=02:00:00
#SBATCH --account=def-pesantg
#SBATCH --cpus-per-task=1
#SBATCH --gpus=1
#SBATCH --mem=24G

module load java/21.0.1
python run-all.py -i 1 -t 1